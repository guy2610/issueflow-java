package io.github.guyeven.issueflow.ticket;

import io.github.guyeven.issueflow.project.Project;
import io.github.guyeven.issueflow.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByProjectIdAndDeletedAtIsNull(Long projectId);
    List<Ticket> findByProjectIdAndDeletedAtIsNotNull(Long projectId);
    long countByProjectAndAssigneeAndStatusNotAndDeletedAtIsNull(Project project, User assignee, TicketStatus status);
    List<Ticket> findByDeletedAtIsNullAndStatusNotAndDueDateBefore(
            TicketStatus status,
            Instant now
    );
}