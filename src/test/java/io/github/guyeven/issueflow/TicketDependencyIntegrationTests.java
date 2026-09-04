package io.github.guyeven.issueflow;

import io.github.guyeven.issueflow.common.error.BadRequestException;
import io.github.guyeven.issueflow.common.error.ConflictException;
import io.github.guyeven.issueflow.common.error.NotFoundException;
import io.github.guyeven.issueflow.project.ProjectService;
import io.github.guyeven.issueflow.ticket.AddTicketDependencyRequest;
import io.github.guyeven.issueflow.ticket.TicketDependencyRepository;
import io.github.guyeven.issueflow.ticket.TicketDependencyService;
import io.github.guyeven.issueflow.ticket.TicketService;
import io.github.guyeven.issueflow.ticket.TicketStatus;
import io.github.guyeven.issueflow.ticket.UpdateTicketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.escalation.fixed-delay-ms=600000")
class TicketDependencyIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketDependencyService dependencyService;

    @Autowired
    private TicketDependencyRepository dependencyRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ProjectService projectService;

    private long userId;
    private long projectId;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    mentions, attachments, comments, ticket_dependencies,
                    tickets, projects, audit_logs, users
                RESTART IDENTITY CASCADE
                """);
        userId = insertUser("dependency-developer", "dependency@example.test");
        projectId = insertProject(userId, "Dependency project");
    }

    @Test
    void createsSimpleChainAndDiamondWithoutRejectingConvergingPaths() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        long c = insertTicket(projectId, "C");
        long d = insertTicket(projectId, "D");

        add(a, b);
        add(a, c);
        add(b, d);
        add(c, d);

        assertThat(dependencyRepository.findGraphEdgesByProjectId(projectId)).hasSize(4);
        assertThat(dependencyService.getDependencies(a)).extracting("id").containsExactlyInAnyOrder(b, c);
    }

    @Test
    void rejectsDuplicateDependency() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        add(a, b);

        assertThatThrownBy(() -> add(a, b))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dependency already exists");
    }

    @Test
    void rejectsDirectTwoNodeCycle() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        add(a, b);

        assertCycle(() -> add(b, a));
    }

    @Test
    void rejectsThreeNodeTransitiveCycle() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        long c = insertTicket(projectId, "C");
        add(a, b);
        add(b, c);

        assertCycle(() -> add(c, a));
    }

    @Test
    void rejectsLongerTransitiveCycle() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        long c = insertTicket(projectId, "C");
        long d = insertTicket(projectId, "D");
        add(a, b);
        add(b, c);
        add(c, d);

        assertCycle(() -> add(d, a));
    }

    @Test
    void rejectsCrossProjectDependency() {
        long otherProjectId = insertProject(userId, "Other project");
        long a = insertTicket(projectId, "A");
        long b = insertTicket(otherProjectId, "B");

        assertThatThrownBy(() -> add(a, b))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Both tickets must belong to the same project");
    }

    @Test
    void unresolvedBlockerPreventsDoneAndCompletedBlockerPermitsDone() {
        long blocked = insertTicket(projectId, "Blocked");
        long blocker = insertTicket(projectId, "Blocker");
        add(blocked, blocker);

        assertThatThrownBy(() -> markDone(blocked))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ticket cannot transition to DONE while it has unresolved blockers");

        markDone(blocker);
        markDone(blocked);
        assertThat(ticketService.getTicket(blocked).status()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void deletedBlockerRemainsUntilDependencyIsExplicitlyRemoved() {
        long blocked = insertTicket(projectId, "Blocked");
        long blocker = insertTicket(projectId, "Blocker");
        add(blocked, blocker);

        ticketService.deleteTicket(blocker);

        assertThat(dependencyService.getDependencies(blocked)).hasSize(1);
        assertThat(dependencyRepository.findGraphEdgesByProjectId(projectId)).hasSize(1);
        assertThatThrownBy(() -> markDone(blocked)).isInstanceOf(BadRequestException.class);

        dependencyService.removeDependency(blocked, blocker);
        markDone(blocked);

        assertThat(dependencyRepository.findGraphEdgesByProjectId(projectId)).isEmpty();
        assertThat(ticketService.getTicket(blocked).status()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void deletedIntermediateTicketRemainsPartOfCycleAnalysis() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        long c = insertTicket(projectId, "C");
        add(a, b);
        add(b, c);
        ticketService.deleteTicket(b);

        assertCycle(() -> add(c, a));
    }

    @Test
    void restoringBlockedTicketRetainsItsDependencies() {
        long blocked = insertTicket(projectId, "Blocked");
        long blocker = insertTicket(projectId, "Blocker");
        add(blocked, blocker);

        ticketService.deleteTicket(blocked);
        assertThat(dependencyRepository.findGraphEdgesByProjectId(projectId)).hasSize(1);
        assertThatThrownBy(() -> dependencyService.removeDependency(blocked, blocker))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ticket not found: " + blocked);

        ticketService.restoreTicket(blocked);
        assertThat(dependencyService.getDependencies(blocked)).hasSize(1);
        assertThatThrownBy(() -> markDone(blocked)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void softDeletedProjectRejectsDependencyMutation() {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        add(a, b);
        projectService.deleteProject(projectId);

        assertThatThrownBy(() -> dependencyService.removeDependency(a, b))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found: " + projectId);
        assertThatThrownBy(() -> add(b, a))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found: " + projectId);
        assertThat(dependencyRepository.findGraphEdgesByProjectId(projectId)).hasSize(1);
    }

    @Test
    void concurrentOppositeEdgesCommitOnlyOneAcyclicMutation() throws Exception {
        long a = insertTicket(projectId, "A");
        long b = insertTicket(projectId, "B");
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> concurrentAdd(start, a, b));
            Future<String> second = executor.submit(() -> concurrentAdd(start, b, a));

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "Dependency would create a cycle");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        var edges = dependencyRepository.findGraphEdgesByProjectId(projectId);
        assertThat(edges).hasSize(1);
        assertThat(edges.getFirst().ticketId()).isNotEqualTo(edges.getFirst().blockedByTicketId());
    }

    private String concurrentAdd(CyclicBarrier start, long ticketId, long blockerId) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            add(ticketId, blockerId);
            return "SUCCESS";
        } catch (ConflictException exception) {
            return exception.getMessage();
        }
    }

    private void assertCycle(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dependency would create a cycle");
    }

    private void add(long ticketId, long blockerId) {
        dependencyService.addDependency(ticketId, new AddTicketDependencyRequest(blockerId));
    }

    private void markDone(long ticketId) {
        ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, TicketStatus.DONE, null, null, null));
    }

    private long insertUser(String username, String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users
                    (username, email, full_name, role, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, 'DEVELOPER', 'hash', ?, ?)
                RETURNING id
                """, Long.class, username, email, username, now(), now());
    }

    private long insertProject(long ownerId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projects (name, owner_id, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, name, ownerId, now(), now());
    }

    private long insertTicket(long targetProjectId, String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tickets
                    (version, title, status, priority, type, project_id, assignee_id,
                     is_overdue, created_at, updated_at)
                VALUES (0, ?, 'TODO', 'MEDIUM', 'FEATURE', ?, ?, false, ?, ?)
                RETURNING id
                """, Long.class, title, targetProjectId, userId, now(), now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
