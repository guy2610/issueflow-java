package io.github.guyeven.issueflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

    List<TicketDependency> findByTicketId(Long ticketId);

    Optional<TicketDependency> findByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    boolean existsByTicketIdAndBlockedByStatusNot(Long ticketId, TicketStatus status);

    @Query("""
            select new io.github.guyeven.issueflow.ticket.TicketDependencyEdge(
                dependency.ticket.id,
                dependency.blockedBy.id
            )
            from TicketDependency dependency
            where dependency.ticket.project.id = :projectId
            """)
    List<TicketDependencyEdge> findGraphEdgesByProjectId(@Param("projectId") Long projectId);
}
