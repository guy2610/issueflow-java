package io.github.guyeven.issueflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.FileSystemUtils;
import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogRepository;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.ticket.TicketRepository;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.user.UserRepository;
import io.github.guyeven.issueflow.user.UserRole;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
class IssueFlowApiIntegrationTests extends PostgresIntegrationTest {

    private static final String TEST_PASSWORD = "portfolio-pass";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPersistentState() throws Exception {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    mentions,
                    attachments,
                    comments,
                    ticket_dependencies,
                    tickets,
                    projects,
                    audit_logs,
                    users
                RESTART IDENTITY CASCADE
                """);
        FileSystemUtils.deleteRecursively(Path.of("target/test-uploads"));
    }

    @Test
    void authFlowAllowsLoginAndMe() throws Exception {
        String username = unique("admin");
        createAdmin(username);

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

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
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
                .andExpect(status().isOk());

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
                .andExpect(status().isOk());

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
        JsonNode data = mentions.get("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).get("id").asLong()).isEqualTo(comment.get("id").asLong());
        assertThat(mentions.get("total").asInt()).isEqualTo(1);
        assertThat(mentions.get("page").asInt()).isEqualTo(1);
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
        long developerId = createUser(unique("csv-dev"), "DEVELOPER").get("id").asLong();

        String csv = String.join("\n",
                "id,title,description,status,priority,type,assigneeId",
                ",\"CSV ticket\",\"Description with, comma and \"\"quote\"\"\",TODO,LOW,BUG," + developerId
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
                .andExpect(status().isOk());

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

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("description", "audit actor test"))))
                .andExpect(status().isOk());

        JsonNode ticketLogs = getJson("/audit-logs?entityType=TICKET", token);

        assertThat(anyNode(ticketLogs, node ->
                node.get("action").asText().equals("UPDATE")
                        && node.get("entityId").asLong() == ticketId
                        && node.get("actor").asText().equals("USER")
                        && node.get("performedBy").asLong() == adminId
        )).isTrue();
    }

    @Test
    void doneTicketCannotBeUpdated() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("description", "should fail"))))
                .andExpect(status().isBadRequest());
    }
    @Test
    void deletedTicketsEndpointRequiresAdminRole() throws Exception {
        JsonNode dev = createUser(unique("dev"), "DEVELOPER");
        String token = login(dev.get("username").asText());

        mvc.perform(get("/tickets/deleted?projectId=1")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }@Test
    void attachmentRejectsUnsupportedContentType() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.html",
                "text/html",
                "<html>bad</html>".getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(file)
                        .param("uploadedByUserId", String.valueOf(adminId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }@Test
    void csvExportReturnsTicketsAsCsv() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);

        long developerId = createUser(unique("export-dev"), "DEVELOPER").get("id").asLong();
        postJson("/tickets", token, Map.of(
                "title", "Export ticket",
                "description", "Description with, comma",
                "status", "TODO",
                "priority", "LOW",
                "type", "BUG",
                "projectId", projectId,
                "assigneeId", developerId
        ));

        String csv = mvc.perform(get("/tickets/export?projectId=" + projectId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("id,title,description,status,priority,type,assigneeId");
        assertThat(csv).contains("Export ticket");
        assertThat(csv).contains("\"Description with, comma\"");
    }

    @Test
    void adminCreationRequiresAnAuthenticatedAdmin() throws Exception {
        String username = unique("public-admin");
        Map<String, Object> anonymousRequest = Map.of(
                "username", username,
                "email", username + "@example.com",
                "fullName", "Public Admin",
                "role", "ADMIN",
                "password", TEST_PASSWORD);
        mvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content(json(anonymousRequest)))
                .andExpect(status().isForbidden());

        JsonNode developer = createUser(unique("admin-creator-dev"), "DEVELOPER");
        String developerToken = login(developer.get("username").asText());
        String developerAttemptUsername = unique("developer-created-admin");
        mvc.perform(post("/users")
                        .header("Authorization", bearer(developerToken))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", developerAttemptUsername,
                                "email", developerAttemptUsername + "@example.com",
                                "fullName", "Developer Created Admin",
                                "role", "ADMIN",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isForbidden());

        String adminToken = createAdminAndLogin();
        String createdAdminUsername = unique("admin-created-admin");
        mvc.perform(post("/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", createdAdminUsername,
                                "email", createdAdminUsername + "@example.com",
                                "fullName", "Admin Created Admin",
                                "role", "ADMIN",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("role").asText()).isEqualTo("ADMIN"));
    }

    @Test
    void csvImportAcceptsTodoTicketAssignedToDeveloper() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long developerId = createUser(unique("csv-valid-dev"), "DEVELOPER").get("id").asLong();

        JsonNode result = importCsv(token, projectId,
                "id,title,description,status,priority,type,assigneeId\n" +
                        ",Valid import,Valid row,TODO,LOW,BUG," + developerId);

        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(result.get("failed").asInt()).isZero();
    }

    @Test
    void csvImportRejectsNonTodoInitialStatusPerRow() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long developerId = createUser(unique("csv-status-dev"), "DEVELOPER").get("id").asLong();

        JsonNode result = importCsv(token, projectId,
                "id,title,description,status,priority,type,assigneeId\n" +
                        ",Invalid status,Invalid row,DONE,LOW,BUG," + developerId);

        assertThat(result.get("created").asInt()).isZero();
        assertThat(result.get("failed").asInt()).isEqualTo(1);
        assertThat(result.get("errors").get(0).asText()).contains("must start in TODO");
    }

    @Test
    void csvImportRejectsAdminAssigneePerRow() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);

        JsonNode result = importCsv(token, projectId,
                "id,title,description,status,priority,type,assigneeId\n" +
                        ",Invalid assignee,Invalid row,TODO,LOW,BUG," + adminId);

        assertThat(result.get("created").asInt()).isZero();
        assertThat(result.get("failed").asInt()).isEqualTo(1);
        assertThat(result.get("errors").get(0).asText()).contains("assigned to developers");
    }

    @Test
    void registrationRequiresExplicitPassword() throws Exception {
        String username = unique("missing-password");
        mvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "fullName", "Missing Password",
                                "role", "DEVELOPER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedCommentAuthorCannotBeSpoofed() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");
        long otherUserId = createUser(unique("other-author"), "DEVELOPER").get("id").asLong();

        JsonNode comment = postJson("/tickets/" + ticketId + "/comments", token,
                Map.of("content", "Authenticated author", "authorId", otherUserId));

        assertThat(comment.get("authorId").asLong()).isEqualTo(adminId);
    }

    @Test
    void attachmentUploaderCannotBeSpoofed() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");
        long otherUserId = createUser(unique("other-uploader"), "DEVELOPER").get("id").asLong();
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain",
                "identity test".getBytes(StandardCharsets.UTF_8));

        String body = mvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(file)
                        .param("uploadedByUserId", String.valueOf(otherUserId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("uploadedByUserId").asLong()).isEqualTo(adminId);
    }

    @Test
    void developerCannotAccessUserAdministrationOrAuditLogs() throws Exception {
        JsonNode developer = createUser(unique("restricted-dev"), "DEVELOPER");
        String token = login(developer.get("username").asText());

        mvc.perform(get("/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/audit-logs").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nestedCommentPathMustMatchCommentsTicket() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long firstTicketId = createTicket(token, projectId, adminId, "TODO");
        long secondTicketId = createTicket(token, projectId, adminId, "TODO");
        JsonNode comment = postJson("/tickets/" + firstTicketId + "/comments", token,
                Map.of("content", "belongs to first ticket"));

        mvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", secondTicketId, comment.get("id").asLong())
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("content", "wrong parent"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", secondTicketId, comment.get("id").asLong())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void nestedAttachmentPathMustMatchAttachmentsTicket() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long firstTicketId = createTicket(token, projectId, adminId, "TODO");
        long secondTicketId = createTicket(token, projectId, adminId, "TODO");
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain",
                "parent test".getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/tickets/{ticketId}/attachments", firstTicketId)
                        .file(file).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long attachmentId = objectMapper.readTree(body).get("id").asLong();

        mvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", secondTicketId, attachmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketPriorityUpdateIsPersisted() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");

        mvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("priority", "CRITICAL"))))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("priority").asText()).isEqualTo("CRITICAL"));
    }

    @Test
    void ticketCannotBeCreatedInTerminalState() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long developerId = createUser(unique("initial-state-dev"), "DEVELOPER").get("id").asLong();

        mvc.perform(post("/tickets")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("title", "Invalid initial state", "status", "DONE",
                                "priority", "MEDIUM", "type", "FEATURE", "projectId", projectId,
                                "assigneeId", developerId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolledBackDomainMutationDoesNotLeaveAuditEntry() throws Exception {
        String token = createAdminAndLogin();
        long adminId = getMeUserId(token);
        long projectId = createProject(token, adminId);
        long ticketId = createTicket(token, projectId, adminId, "TODO");
        long auditCountBefore = auditLogRepository.count();
        String originalDescription = ticketRepository.findById(ticketId).orElseThrow().getDescription();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var ticket = ticketRepository.findById(ticketId).orElseThrow();
            ticket.setDescription("must roll back");
            auditLogService.recordSystemAction(AuditAction.UPDATE, AuditEntityType.TICKET,
                    ticketId, "Mutation that must roll back");
            status.setRollbackOnly();
        });

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
        assertThat(ticketRepository.findById(ticketId).orElseThrow().getDescription())
                .isEqualTo(originalDescription);
    }

    private String createAdminAndLogin() throws Exception {
        String username = unique("admin");
        createAdmin(username);
        return login(username);
    }

    private void createAdmin(String username) {
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(username + "@example.com");
        admin.setFullName(username + " User");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        userRepository.saveAndFlush(admin);
    }

    private JsonNode createUser(String username, String role) throws Exception {
        return postJsonWithoutAuth("/users", Map.of(
                "username", username,
                "email", username + "@example.com",
                "fullName", username + " User",
                "role", role,
                "password", TEST_PASSWORD
        ));
    }

    private String login(String username) throws Exception {
        JsonNode response = postJsonWithoutAuth("/auth/login", Map.of(
                "username", username,
                "password", TEST_PASSWORD
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
        User requestedAssignee = userRepository.findById(assigneeId).orElseThrow();
        if (requestedAssignee.getRole() != UserRole.DEVELOPER) {
            assigneeId = createUser(unique("ticket-dev"), "DEVELOPER").get("id").asLong();
        }
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

    private JsonNode importCsv(String token, long projectId, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "tickets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String response = mvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", String.valueOf(projectId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
