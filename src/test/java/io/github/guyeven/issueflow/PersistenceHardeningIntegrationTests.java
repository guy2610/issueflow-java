package io.github.guyeven.issueflow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.escalation.fixed-delay-ms=600000")
class PersistenceHardeningIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    mentions, attachments, comments, ticket_dependencies,
                    tickets, projects, audit_logs, users
                RESTART IDENTITY CASCADE
                """);
    }

    @AfterEach
    void leaveDatabaseClean() {
        resetDatabase();
    }

    @Test
    void flywayCreatesSchemaBeforeHibernateValidation() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void databaseEnforcesUniqueUsernameAndEmail() {
        insertUser("first", "first@example.com", "DEVELOPER");

        assertThatThrownBy(() -> insertUser("first", "other@example.com", "DEVELOPER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("other", "first@example.com", "DEVELOPER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesDependencyUniquenessAndRejectsSelfDependency() {
        long userId = insertUser("developer", "developer@example.com", "DEVELOPER");
        long projectId = insertProject(userId);
        long blockedId = insertTicket(projectId, userId, "TODO");
        long blockerId = insertTicket(projectId, userId, "TODO");

        insertDependency(blockedId, blockerId);

        assertThatThrownBy(() -> insertDependency(blockedId, blockerId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDependency(blockedId, blockedId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidEnumValues() {
        assertThatThrownBy(() -> insertUser("invalid", "invalid@example.com", "MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeysRestrictDeletionOfReferencedRows() {
        long userId = insertUser("owner", "owner@example.com", "ADMIN");
        insertProject(userId);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ticketVersionDetectsConcurrentUpdates() {
        long userId = insertUser("locker", "locker@example.com", "DEVELOPER");
        long projectId = insertProject(userId);
        long ticketId = insertTicket(projectId, userId, "TODO");

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var firstTicket = first.find(io.github.guyeven.issueflow.ticket.Ticket.class, ticketId);
            var secondTicket = second.find(io.github.guyeven.issueflow.ticket.Ticket.class, ticketId);

            firstTicket.setTitle("first update");
            first.getTransaction().commit();

            secondTicket.setTitle("stale update");
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(jakarta.persistence.RollbackException.class)
                    .hasRootCauseInstanceOf(org.hibernate.StaleObjectStateException.class);
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }

    private long insertUser(String username, String email, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users
                    (username, email, full_name, role, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, username, email, username, role, "hash", now(), now());
    }

    private long insertProject(long ownerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO projects (name, owner_id, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, "project", ownerId, now(), now());
    }

    private long insertTicket(long projectId, long assigneeId, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO tickets
                    (version, title, status, priority, type, project_id, assignee_id,
                     is_overdue, created_at, updated_at)
                VALUES (0, ?, ?, 'MEDIUM', 'FEATURE', ?, ?, false, ?, ?)
                RETURNING id
                """, Long.class, "ticket", status, projectId, assigneeId, now(), now());
    }

    private void insertDependency(long ticketId, long blockerId) {
        jdbcTemplate.update("""
                INSERT INTO ticket_dependencies (ticket_id, blocked_by_ticket_id, created_at)
                VALUES (?, ?, ?)
                """, ticketId, blockerId, now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
