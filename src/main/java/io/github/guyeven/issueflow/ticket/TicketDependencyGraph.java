package io.github.guyeven.issueflow.ticket;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TicketDependencyGraph {

    public boolean wouldCreateCycle(
            Collection<TicketDependencyEdge> existingEdges,
            Long proposedTicketId,
            Long proposedBlockerId
    ) {
        Map<Long, List<Long>> blockersByTicket = new HashMap<>();
        for (TicketDependencyEdge edge : existingEdges) {
            blockersByTicket.computeIfAbsent(edge.ticketId(), ignored -> new ArrayList<>())
                    .add(edge.blockedByTicketId());
        }

        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.push(proposedBlockerId);

        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (current.equals(proposedTicketId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            blockersByTicket.getOrDefault(current, List.of()).forEach(pending::push);
        }

        return false;
    }
}
