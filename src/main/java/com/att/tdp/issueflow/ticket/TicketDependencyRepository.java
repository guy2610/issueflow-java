package com.att.tdp.issueflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

    List<TicketDependency> findByTicketId(Long ticketId);

    Optional<TicketDependency> findByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    boolean existsByTicketIdAndBlockedByStatusNot(Long ticketId, TicketStatus status);
}