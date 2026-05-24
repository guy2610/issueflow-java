package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.common.error.BadRequestException;
import com.att.tdp.issueflow.common.error.ConflictException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketDependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketService ticketService;
    private final AuditLogService auditLogService;

    public TicketDependencyService(
            TicketDependencyRepository dependencyRepository,
            TicketService ticketService,
            AuditLogService auditLogService
    ) {
        this.dependencyRepository = dependencyRepository;
        this.ticketService = ticketService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TicketDependencyResponse addDependency(Long ticketId, AddTicketDependencyRequest request) {
        Ticket ticket = ticketService.findActiveTicketEntity(ticketId);
        Ticket blocker = ticketService.findActiveTicketEntity(request.blockedBy());

        if (ticket.getId().equals(blocker.getId())) {
            throw new BadRequestException("Ticket cannot depend on itself");
        }

        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("Both tickets must belong to the same project");
        }

        if (dependencyRepository.existsByTicketIdAndBlockedById(ticketId, blocker.getId())) {
            throw new ConflictException("Dependency already exists");
        }

        TicketDependency dependency = new TicketDependency();
        dependency.setTicket(ticket);
        dependency.setBlockedBy(blocker);

        TicketDependency saved = dependencyRepository.save(dependency);

        auditLogService.recordUserAction(
                AuditAction.ADD_DEPENDENCY,
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                AuditEntityType.DEPENDENCY,
                saved.getId(),
                "Ticket " + ticketId + " blocked by ticket " + blocker.getId()
        );

        return TicketDependencyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketDependencyResponse> getDependencies(Long ticketId) {
        ticketService.findActiveTicketEntity(ticketId);

        return dependencyRepository.findByTicketId(ticketId)
                .stream()
                .map(TicketDependencyResponse::from)
                .toList();
    }

    @Transactional
    public void removeDependency(Long ticketId, Long blockerId) {
        TicketDependency dependency = dependencyRepository
                .findByTicketIdAndBlockedById(ticketId, blockerId)
                .orElseThrow(() -> new NotFoundException("Dependency not found"));

        dependencyRepository.delete(dependency);

        auditLogService.recordUserAction(
                AuditAction.REMOVE_DEPENDENCY,
                dependency.getTicket().getAssignee() == null
                        ? null
                        : dependency.getTicket().getAssignee().getId(),
                AuditEntityType.DEPENDENCY,
                dependency.getId(),
                "Removed dependency from ticket " + ticketId + " to blocker " + blockerId
        );
    }

    @Transactional(readOnly = true)
    public boolean hasUnresolvedBlockers(Long ticketId) {
        return dependencyRepository.existsByTicketIdAndBlockedByStatusNot(ticketId, TicketStatus.DONE);
    }
}