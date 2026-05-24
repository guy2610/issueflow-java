package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.error.BadRequestException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.ticket.TicketDependencyRepository;
import com.att.tdp.issueflow.project.WorkloadService;

import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final WorkloadService workloadService;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectService projectService,
            UserService userService,
            AuditLogService auditLogService,
            TicketDependencyRepository ticketDependencyRepository,
            WorkloadService workloadService

    ) {
        this.ticketRepository = ticketRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.ticketDependencyRepository = ticketDependencyRepository;
        this.workloadService = workloadService;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Project project = projectService.findActiveProjectEntity(request.projectId());

        User assignee = null;
        boolean autoAssigned = false;

        if (request.assigneeId() != null) {
            assignee = userService.findUserEntity(request.assigneeId());
        } else {
            assignee = workloadService.selectLeastLoadedDeveloper(project);
            autoAssigned = assignee != null;
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(request.status());
        ticket.setPriority(request.priority());
        ticket.setType(request.type());
        ticket.setProject(project);
        ticket.setAssignee(assignee);
        ticket.setDueDate(request.dueDate());
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        auditLogService.recordUserAction(
                AuditAction.CREATE,
                saved.getAssignee() == null ? null : saved.getAssignee().getId(),
                AuditEntityType.TICKET,
                saved.getId(),
                "Ticket created"
        );
        if (autoAssigned) {
            workloadService.recordAutoAssignment(saved.getId(), assignee);
        }
        return TicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long id) {
        return TicketResponse.from(findActiveTicketEntity(id));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByProject(Long projectId) {
        projectService.findActiveProjectEntity(projectId);

        return ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId)
                .stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = findActiveTicketEntity(id);

        if (ticket.isDone()) {
            throw new BadRequestException("Ticket cannot be updated once it is DONE");
        }

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Ticket title cannot be blank");
            }
            ticket.setTitle(request.title());
        }

        if (request.description() != null) {
            ticket.setDescription(request.description());
        }

        if (request.status() != null) {
            validateStatusTransition(ticket.getStatus(), request.status());

            if (request.status() == TicketStatus.DONE &&
                    ticketDependencyRepository.existsByTicketIdAndBlockedByStatusNot(ticket.getId(), TicketStatus.DONE)) {
                throw new BadRequestException("Ticket cannot transition to DONE while it has unresolved blockers");
            }

            ticket.setStatus(request.status());
        }

        if (request.priority() != null) {
            if (request.priority() != ticket.getPriority()) {
                ticket.setOverdue(false);
            }
        }

        if (request.assigneeId() != null) {
            User assignee = userService.findUserEntity(request.assigneeId());
            ticket.setAssignee(assignee);
        }

        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        auditLogService.recordUserAction(
                AuditAction.UPDATE,
                saved.getAssignee() == null ? null : saved.getAssignee().getId(),
                AuditEntityType.TICKET,
                saved.getId(),
                "Ticket updated"
        );
        return TicketResponse.from(saved);
    }

    @Transactional
    public void deleteTicket(Long id) {
        Ticket ticket = findActiveTicketEntity(id);
        ticket.softDelete();
        auditLogService.recordUserAction(
                AuditAction.DELETE,
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                AuditEntityType.TICKET,
                ticket.getId(),
                "Ticket soft-deleted"
        );
    }

    public Ticket findActiveTicketEntity(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));

        if (ticket.isDeleted()) {
            throw new NotFoundException("Ticket not found: " + id);
        }

        return ticket;
    }

    private void validateStatusTransition(TicketStatus current, TicketStatus next) {
        if (!current.canTransitionTo(next)) {
            throw new BadRequestException(
                    "Invalid status transition from " + current + " to " + next
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedTicketsByProject(Long projectId) {
        projectService.findActiveProjectEntity(projectId);

        return ticketRepository.findByProjectIdAndDeletedAtIsNotNull(projectId)
                .stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional
    public TicketResponse restoreTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));

        if (!ticket.isDeleted()) {
            return TicketResponse.from(ticket);
        }

        ticket.restore();
        Ticket saved = ticketRepository.saveAndFlush(ticket);

        auditLogService.recordUserAction(
                AuditAction.RESTORE,
                saved.getAssignee() == null ? null : saved.getAssignee().getId(),
                AuditEntityType.TICKET,
                saved.getId(),
                "Ticket restored"
        );

        return TicketResponse.from(saved);
    }
}