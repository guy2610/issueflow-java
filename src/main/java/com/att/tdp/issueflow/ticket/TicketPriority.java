package com.att.tdp.issueflow.ticket;

public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public TicketPriority escalate() {
        if (this == CRITICAL) {
            return CRITICAL;
        }
        return values()[this.ordinal() + 1];
    }
}