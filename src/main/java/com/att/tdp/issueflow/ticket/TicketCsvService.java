package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.common.error.BadRequestException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TicketCsvService {

    private static final String[] HEADERS = {
            "id",
            "title",
            "description",
            "status",
            "priority",
            "type",
            "assigneeId"
    };

    private final TicketRepository ticketRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public TicketCsvService(
            TicketRepository ticketRepository,
            ProjectService projectService,
            UserService userService,
            AuditLogService auditLogService
    ) {
        this.ticketRepository = ticketRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public String exportTickets(Long projectId) {
        projectService.findActiveProjectEntity(projectId);

        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId);

        try {
            StringWriter writer = new StringWriter();

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader(HEADERS)
                    .build();

            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (Ticket ticket : tickets) {
                    printer.printRecord(
                            ticket.getId(),
                            ticket.getTitle(),
                            ticket.getDescription(),
                            ticket.getStatus(),
                            ticket.getPriority(),
                            ticket.getType(),
                            ticket.getAssignee() == null ? null : ticket.getAssignee().getId()
                    );
                }
            }

            auditLogService.recordUserAction(
                    AuditAction.EXPORT,
                    null,
                    AuditEntityType.TICKET,
                    projectId,
                    "Exported tickets for project " + projectId
            );

            return writer.toString();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to export tickets");
        }
    }

    @Transactional
    public TicketImportResult importTickets(Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }

        Project project = projectService.findActiveProjectEntity(projectId);

        int created = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
                );
                CSVParser parser = new CSVParser(reader, format)
        ) {
            int rowNumber = 1;

            for (CSVRecord record : parser) {
                rowNumber++;

                try {
                    Ticket ticket = parseTicketRecord(record, project);
                    ticketRepository.save(ticket);
                    created++;
                } catch (Exception ex) {
                    failed++;
                    errors.add("Row " + rowNumber + ": " + ex.getMessage());
                }
            }

            auditLogService.recordUserAction(
                    AuditAction.IMPORT,
                    null,
                    AuditEntityType.TICKET,
                    projectId,
                    "Imported tickets for project " + projectId + ": created=" + created + ", failed=" + failed
            );

            return new TicketImportResult(created, failed, errors);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read CSV file");
        }
    }

    private Ticket parseTicketRecord(CSVRecord record, Project project) {
        String title = getRequired(record, "title");
        String description = getOptional(record, "description");

        TicketStatus status = parseEnum(TicketStatus.class, getRequired(record, "status"), "status");
        TicketPriority priority = parseEnum(TicketPriority.class, getRequired(record, "priority"), "priority");
        TicketType type = parseEnum(TicketType.class, getRequired(record, "type"), "type");

        User assignee = null;
        String assigneeIdValue = getOptional(record, "assigneeId");
        if (assigneeIdValue != null && !assigneeIdValue.isBlank()) {
            Long assigneeId = parseLong(assigneeIdValue, "assigneeId");
            assignee = userService.findUserEntity(assigneeId);
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setType(type);
        ticket.setProject(project);
        ticket.setAssignee(assignee);

        return ticket;
    }

    private String getRequired(CSVRecord record, String field) {
        String value = getOptional(record, field);

        if (value == null || value.isBlank()) {
            throw new BadRequestException("Missing required field: " + field);
        }

        return value;
    }

    private String getOptional(CSVRecord record, String field) {
        if (!record.isMapped(field)) {
            return null;
        }

        return record.get(field);
    }

    private Long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid " + field + ": " + value);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + field + ": " + value);
        }
    }
}