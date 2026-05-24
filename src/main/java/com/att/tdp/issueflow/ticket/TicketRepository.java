package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;
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