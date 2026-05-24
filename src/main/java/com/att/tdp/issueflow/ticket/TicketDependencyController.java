package com.att.tdp.issueflow.ticket;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
public class TicketDependencyController {

    private final TicketDependencyService dependencyService;

    public TicketDependencyController(TicketDependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public TicketDependencyResponse addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddTicketDependencyRequest request
    ) {
        return dependencyService.addDependency(ticketId, request);
    }

    @GetMapping
    public List<TicketDependencyResponse> getDependencies(@PathVariable Long ticketId) {
        return dependencyService.getDependencies(ticketId);
    }

    @DeleteMapping("/{blockerId}")
    @ResponseStatus(HttpStatus.OK)
    public void removeDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId
    ) {
        dependencyService.removeDependency(ticketId, blockerId);
    }
}