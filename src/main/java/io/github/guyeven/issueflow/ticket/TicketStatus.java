package io.github.guyeven.issueflow.ticket;

public enum TicketStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE;

    public boolean canTransitionTo(TicketStatus next) {
        return next.ordinal() >= this.ordinal();
    }
}