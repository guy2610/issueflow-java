package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.error.BadRequestException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectService projectService;
    private final UserService userService;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectService projectService,
            UserService userService
    ) {
        this.ticketRepository = ticketRepository;
        this.projectService = projectService;
        this.userService = userService;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Project project = projectService.findActiveProjectEntity(request.projectId());

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = userService.findUserEntity(request.assigneeId());
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

        return TicketResponse.from(ticketRepository.save(ticket));
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
            ticket.setStatus(request.status());
        }

        if (request.priority() != null) {
            ticket.setPriority(request.priority());
            ticket.setOverdue(false);
        }

        if (request.assigneeId() != null) {
            User assignee = userService.findUserEntity(request.assigneeId());
            ticket.setAssignee(assignee);
        }

        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        return TicketResponse.from(saved);
    }

    @Transactional
    public void deleteTicket(Long id) {
        Ticket ticket = findActiveTicketEntity(id);
        ticket.softDelete();
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
}