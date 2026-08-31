package io.github.guyeven.issueflow.ticket;

import java.time.Instant;

public record UpdateTicketRequest(
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Long assigneeId,
        Instant dueDate
) {
}