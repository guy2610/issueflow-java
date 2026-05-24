package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TicketEscalationService {

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public TicketEscalationService(
            TicketRepository ticketRepository,
            AuditLogService auditLogService
    ) {
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
    }

    @Scheduled(fixedDelayString = "${app.escalation.fixed-delay-ms:60000}")
    public void runScheduledEscalation() {
        escalateOverdueTickets(Instant.now());
    }

    @Transactional
    public int escalateOverdueTickets(Instant now) {
        List<Ticket> overdueTickets =
                ticketRepository.findByDeletedAtIsNullAndStatusNotAndDueDateBefore(
                        TicketStatus.DONE,
                        now
                );

        int changed = 0;

        for (Ticket ticket : overdueTickets) {
            boolean updated = escalateTicket(ticket);

            if (updated) {
                Ticket saved = ticketRepository.saveAndFlush(ticket);

                auditLogService.recordSystemAction(
                        AuditAction.AUTO_ESCALATE,
                        AuditEntityType.TICKET,
                        saved.getId(),
                        "Ticket auto-escalated to priority " + saved.getPriority()
                                + ", isOverdue=" + saved.isOverdue()
                );

                changed++;
            }
        }

        return changed;
    }

    private boolean escalateTicket(Ticket ticket) {
        if (ticket.getDueDate() == null) {
            return false;
        }

        if (ticket.getPriority() == TicketPriority.CRITICAL) {
            if (!ticket.isOverdue()) {
                ticket.setOverdue(true);
                return true;
            }

            return false;
        }

        ticket.setPriority(ticket.getPriority().escalate());
        ticket.setOverdue(false);
        return true;
    }
}