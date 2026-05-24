package com.att.tdp.issueflow.ticket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketDomainTests {

    @Test
    void statusCanOnlyMoveForwardOrStaySame() {
        assertThat(TicketStatus.TODO.canTransitionTo(TicketStatus.TODO)).isTrue();
        assertThat(TicketStatus.TODO.canTransitionTo(TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.IN_REVIEW)).isTrue();
        assertThat(TicketStatus.IN_REVIEW.canTransitionTo(TicketStatus.DONE)).isTrue();

        assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.TODO)).isFalse();
        assertThat(TicketStatus.DONE.canTransitionTo(TicketStatus.IN_REVIEW)).isFalse();
    }

    @Test
    void priorityEscalatesUntilCritical() {
        assertThat(TicketPriority.LOW.escalate()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(TicketPriority.MEDIUM.escalate()).isEqualTo(TicketPriority.HIGH);
        assertThat(TicketPriority.HIGH.escalate()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(TicketPriority.CRITICAL.escalate()).isEqualTo(TicketPriority.CRITICAL);
    }
}