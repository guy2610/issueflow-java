package com.att.tdp.issueflow.ticket;

public enum TicketStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE;

    public boolean canTransitionTo(TicketStatus next) {
        return next.ordinal() >= this.ordinal();
    }
}