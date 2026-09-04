package io.github.guyeven.issueflow.ticket;

public record TicketDependencyEdge(
        Long ticketId,
        Long blockedByTicketId
) {
}
