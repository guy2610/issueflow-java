package io.github.guyeven.issueflow.project;

public record WorkloadResponse(
        Long userId,
        String username,
        long openTicketCount
) {
}