package com.att.tdp.issueflow.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateTicketRequest(
        @NotBlank String title,
        String description,
        @NotNull TicketStatus status,
        @NotNull TicketPriority priority,
        @NotNull TicketType type,
        @NotNull Long projectId,
        Long assigneeId,
        Instant dueDate
) {
}