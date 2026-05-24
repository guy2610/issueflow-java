package com.att.tdp.issueflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.escalation.fixed-delay-ms=600000",
        "app.attachments.storage-dir=target/test-uploads"
})
@AutoConfigureMockMvc
class IssueFlowApiIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authFlowAllowsLoginAndMe() throws Exception {
        String username = unique("admin");
        createUser(username, "ADMIN");

        String token = login(username);

        JsonNode me = getJson("/auth/me", token);

        assertThat(me.get("username").asText()).isEqualTo(username);
        assertThat(me.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void protectedEndpointRequiresJwt() throws Exception {
        int status = mvc.perform(get("/projects"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    void ticketStatusCannotMoveBackward() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(post("/tickets/update/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        mvc.perform(post("/tickets/update/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("status", "TODO"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unresolvedDependencyBlocksDoneTransition() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);

        long blockedTicketId = createTicket(token, projectId, adminId, "TODO");
        long blockerTicketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(post("/tickets/{ticketId}/dependencies", blockedTicketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("blockedBy", blockerTicketId))))
                .andExpect(status().isCreated());

        mvc.perform(post("/tickets/update/{ticketId}", blockedTicketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void softDeletedTicketIsHiddenAndCanBeRestoredByAdmin() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(delete("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        JsonNode deletedTickets = getJson("/tickets/deleted?projectId=" + projectId, token);

        assertThat(anyNode(deletedTickets, node -> node.get("id").asLong() == ticketId)).isTrue();

        mvc.perform(post("/tickets/{ticketId}/restore", ticketId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(get("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void commentMentionsAreCaseInsensitiveAndReturnedForUser() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        JsonNode dev = createUser(unique("dev"), "DEVELOPER");
        long devId = dev.get("id").asLong();
        String devUsername = dev.get("username").asText();

        JsonNode comment = postJson(
                "/tickets/" + ticketId + "/comments",
                token,
                Map.of(
                        "content", "Please check this @" + devUsername.toUpperCase(),
                        "authorId", adminId
                )
        );

        assertThat(comment.get("mentionedUsers")).hasSize(1);
        assertThat(comment.get("mentionedUsers").get(0).get("id").asLong()).isEqualTo(devId);

        JsonNode mentions = getJson("/users/" + devId + "/mentions", token);

        assertThat(mentions).hasSize(1);
        assertThat(mentions.get(0).get("id").asLong()).isEqualTo(comment.get("id").asLong());
    }

    @Test
    void ticketWithoutAssigneeIsAutoAssignedToLeastLoadedDeveloper() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);

        JsonNode targetDev = createUser(unique("target-dev"), "DEVELOPER");
        long targetDevId = targetDev.get("id").asLong();

        for (Long developerId : getDeveloperIds(token)) {
            if (!developerId.equals(targetDevId)) {
                createTicket(token, projectId, developerId, "TODO");
            }
        }

        JsonNode autoAssignedTicket = postJson(
                "/tickets",
                token,
                Map.of(
                        "title", unique("auto-ticket"),
                        "description", "Should choose the only developer with zero workload",
                        "status", "TODO",
                        "priority", "LOW",
                        "type", "BUG",
                        "projectId", projectId
                )
        );

        assertThat(autoAssignedTicket.get("assigneeId").asLong()).isEqualTo(targetDevId);
    }

    @Test
    void csvImportHandlesCommasAndQuotes() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);

        String csv = String.join("\n",
                "id,title,description,status,priority,type,assigneeId",
                ",\"CSV ticket\",\"Description with, comma and \"\"quote\"\"\",TODO,LOW,BUG," + adminId
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        String importResponseBody = mvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", String.valueOf(projectId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode result = objectMapper.readTree(importResponseBody);

        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isEqualTo(0);

        JsonNode tickets = getJson("/tickets?projectId=" + projectId, token);

        assertThat(anyNode(
                tickets,
                node -> node.get("description").asText().contains("comma and \"quote\"")
        )).isTrue();
    }

    @Test
    void attachmentCanBeUploadedDownloadedAndDeleted() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello test attachment".getBytes(StandardCharsets.UTF_8)
        );

        String uploadResponse = mvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(file)
                        .param("uploadedByUserId", String.valueOf(adminId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode attachment = objectMapper.readTree(uploadResponse);
        long attachmentId = attachment.get("id").asLong();

        mvc.perform(get("/attachments/{attachmentId}", attachmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("hello test attachment"));

        mvc.perform(delete("/attachments/{attachmentId}", attachmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/attachments/{attachmentId}", attachmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditUsesAuthenticatedUserForUserActions() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(post("/tickets/update/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("description", "audit actor test"))))
                .andExpect(status().isOk());

        JsonNode ticketLogs = getJson("/audit-logs?entityType=TICKET", token);

        assertThat(anyNode(ticketLogs, node ->
                node.get("action").asText().equals("UPDATE")
                        && node.get("entityId").asLong() == ticketId
                        && node.get("actorType").asText().equals("USER")
                        && node.get("actorUserId").asLong() == adminId
        )).isTrue();
    }

    private String createAdminAndLogin() throws Exception {
        String username = unique("admin");
        createUser(username, "ADMIN");
        return login(username);
    }

    private JsonNode createUser(String username, String role) throws Exception {
        return postJsonWithoutAuth("/users", Map.of(
                "username", username,
                "email", username + "@example.com",
                "fullName", username + " User",
                "role", role,
                "password", "secret"
        ));
    }

    private String login(String username) throws Exception {
        JsonNode response = postJsonWithoutAuth("/auth/login", Map.of(
                "username", username,
                "password", "secret"
        ));

        return response.get("accessToken").asText();
    }

    private long getMeUserId(String token) throws Exception {
        return getJson("/auth/me", token).get("id").asLong();
    }

    private long createProject(String token, long ownerId) throws Exception {
        JsonNode project = postJson("/projects", token, Map.of(
                "name", unique("project"),
                "description", "Test project",
                "ownerId", ownerId
        ));

        return project.get("id").asLong();
    }

    private long createTicket(String token, long projectId, long assigneeId, String status) throws Exception {
        JsonNode ticket = postJson("/tickets", token, Map.of(
                "title", unique("ticket"),
                "description", "Test ticket",
                "status", status,
                "priority", "MEDIUM",
                "type", "FEATURE",
                "projectId", projectId,
                "assigneeId", assigneeId
        ));

        return ticket.get("id").asLong();
    }

    private List<Long> getDeveloperIds(String token) throws Exception {
        JsonNode users = getJson("/users", token);
        List<Long> developerIds = new ArrayList<>();

        for (JsonNode user : users) {
            if ("DEVELOPER".equals(user.get("role").asText())) {
                developerIds.add(user.get("id").asLong());
            }
        }

        return developerIds;
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        String response = mvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private JsonNode postJsonWithoutAuth(String path, Object body) throws Exception {
        String response = mvc.perform(post(path)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String response = mvc.perform(get(path)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean anyNode(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(predicate);
    }
}