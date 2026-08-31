package io.github.guyeven.issueflow.ticket;

import java.util.List;

public record TicketImportResult(
        int created,
        int failed,
        List<String> errors
) {
}