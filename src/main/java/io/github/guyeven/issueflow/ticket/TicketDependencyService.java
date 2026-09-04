package io.github.guyeven.issueflow.ticket;

import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.common.error.BadRequestException;
import io.github.guyeven.issueflow.common.error.ConflictException;
import io.github.guyeven.issueflow.common.error.NotFoundException;
import io.github.guyeven.issueflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketDependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketService ticketService;
    private final ProjectService projectService;
    private final TicketDependencyGraph dependencyGraph;
    private final AuditLogService auditLogService;

    public TicketDependencyService(
            TicketDependencyRepository dependencyRepository,
            TicketService ticketService,
            ProjectService projectService,
            TicketDependencyGraph dependencyGraph,
            AuditLogService auditLogService
    ) {
        this.dependencyRepository = dependencyRepository;
        this.ticketService = ticketService;
        this.projectService = projectService;
        this.dependencyGraph = dependencyGraph;
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

        Long projectId = ticket.getProject().getId();
        projectService.lockActiveProjectForDependencyMutation(projectId);

        if (dependencyRepository.existsByTicketIdAndBlockedById(ticketId, blocker.getId())) {
            throw new ConflictException("Dependency already exists");
        }

        List<TicketDependencyEdge> existingEdges = dependencyRepository.findGraphEdgesByProjectId(projectId);
        if (dependencyGraph.wouldCreateCycle(existingEdges, ticketId, blocker.getId())) {
            throw new ConflictException("Dependency would create a cycle");
        }

        TicketDependency dependency = new TicketDependency();
        dependency.setTicket(ticket);
        dependency.setBlockedBy(blocker);

        TicketDependency saved = dependencyRepository.save(dependency);

        auditLogService.recordCurrentUserAction(
                AuditAction.ADD_DEPENDENCY,
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
        Ticket ticket = ticketService.findActiveTicketEntity(ticketId);
        projectService.lockActiveProjectForDependencyMutation(ticket.getProject().getId());

        TicketDependency dependency = dependencyRepository
                .findByTicketIdAndBlockedById(ticketId, blockerId)
                .orElseThrow(() -> new NotFoundException("Dependency not found"));

        dependencyRepository.delete(dependency);

        auditLogService.recordCurrentUserAction(
                AuditAction.REMOVE_DEPENDENCY,
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
