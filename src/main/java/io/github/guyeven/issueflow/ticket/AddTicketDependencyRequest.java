package io.github.guyeven.issueflow.ticket;

import jakarta.validation.constraints.NotNull;

public record AddTicketDependencyRequest(
        @NotNull Long blockedBy
) {
}