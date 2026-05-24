package com.att.tdp.issueflow.ticket;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketCsvService ticketCsvService;

    public TicketController(TicketService ticketService, TicketCsvService ticketCsvService) {
        this.ticketService = ticketService;
        this.ticketCsvService = ticketCsvService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }
    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TicketResponse> getDeletedTickets(@RequestParam Long projectId) {
        return ticketService.getDeletedTicketsByProject(projectId);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
        String csv = ticketCsvService.exportTickets(projectId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("tickets-project-" + projectId + ".csv")
                                .build()
                                .toString()
                )
                .body(csv);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketImportResult importTickets(
            @RequestParam Long projectId,
            @RequestPart("file") MultipartFile file
    ) {
        return ticketCsvService.importTickets(projectId, file);
    }

    @PostMapping("/{ticketId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse restoreTicket(@PathVariable Long ticketId) {
        return ticketService.restoreTicket(ticketId);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getTicket(@PathVariable Long ticketId) {
        return ticketService.getTicket(ticketId);
    }

    @GetMapping
    public List<TicketResponse> getTicketsByProject(@RequestParam Long projectId) {
        return ticketService.getTicketsByProject(projectId);
    }

    @PostMapping("/update/{ticketId}")
    public TicketResponse updateTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        return ticketService.updateTicket(ticketId, request);
    }

    @DeleteMapping("/{ticketId}")
    @ResponseStatus(HttpStatus.OK)
    public void deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
    }
    @PatchMapping("/{ticketId}")
    public TicketResponse patchTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request
    ) {
        return ticketService.updateTicket(ticketId, request);
    }
}