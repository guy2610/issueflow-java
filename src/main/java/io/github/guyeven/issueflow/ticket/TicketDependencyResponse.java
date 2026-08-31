package io.github.guyeven.issueflow.ticket;

public record TicketDependencyResponse(
        Long id,
        String title,
        TicketStatus status
) {
    public static TicketDependencyResponse from(TicketDependency dependency) {
        Ticket blocker = dependency.getBlockedBy();

        return new TicketDependencyResponse(
                blocker.getId(),
                blocker.getTitle(),
                blocker.getStatus()
        );
    }
}