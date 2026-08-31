package io.github.guyeven.issueflow.project;

import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.ticket.TicketRepository;
import io.github.guyeven.issueflow.ticket.TicketStatus;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.user.UserRepository;
import io.github.guyeven.issueflow.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class WorkloadService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final ProjectService projectService;
    private final AuditLogService auditLogService;

    public WorkloadService(
            UserRepository userRepository,
            TicketRepository ticketRepository,
            ProjectService projectService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.projectService = projectService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public User selectLeastLoadedDeveloper(Project project) {
        return userRepository.findByRoleOrderByCreatedAtAsc(UserRole.DEVELOPER)
                .stream()
                .min(Comparator
                        .comparingLong((User user) -> countOpenTickets(project, user))
                        .thenComparing(User::getCreatedAt)
                )
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkloadResponse> getProjectWorkload(Long projectId) {
        Project project = projectService.findActiveProjectEntity(projectId);

        return userRepository.findByRoleOrderByCreatedAtAsc(UserRole.DEVELOPER)
                .stream()
                .map(user -> new WorkloadResponse(
                        user.getId(),
                        user.getUsername(),
                        countOpenTickets(project, user)
                ))
                .sorted(Comparator
                        .comparingLong(WorkloadResponse::openTicketCount)
                        .thenComparing(WorkloadResponse::userId)
                )
                .toList();
    }

    public void recordAutoAssignment(Long ticketId, User assignee) {
        auditLogService.recordSystemAction(
                AuditAction.AUTO_ASSIGN,
                AuditEntityType.TICKET,
                ticketId,
                "Ticket auto-assigned to user " + assignee.getId()
        );
    }

    private long countOpenTickets(Project project, User user) {
        return ticketRepository.countByProjectAndAssigneeAndStatusNotAndDeletedAtIsNull(
                project,
                user,
                TicketStatus.DONE
        );
    }
}