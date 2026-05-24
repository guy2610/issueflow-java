package com.att.tdp.issueflow.ticket;

public record TicketDependencyResponse(
        Long ticketId,
        Long blockedBy,
        TicketStatus blockerStatus
) {
    public static TicketDependencyResponse from(TicketDependency dependency) {
        return new TicketDependencyResponse(
                dependency.getTicket().getId(),
                dependency.getBlockedBy().getId(),
                dependency.getBlockedBy().getStatus()
        );
    }
}