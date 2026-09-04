package io.github.guyeven.issueflow.ticket;

import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.common.error.BadRequestException;
import io.github.guyeven.issueflow.project.Project;
import io.github.guyeven.issueflow.project.ProjectService;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.user.UserService;
import io.github.guyeven.issueflow.user.UserRole;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.UncheckedIOException;

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

    @Transactional
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

            auditLogService.recordCurrentUserAction(
                    AuditAction.EXPORT,
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

            auditLogService.recordCurrentUserAction(
                    AuditAction.IMPORT,
                    AuditEntityType.TICKET,
                    projectId,
                    "Imported tickets for project " + projectId + ": created=" + created + ", failed=" + failed
            );

            return new TicketImportResult(created, failed, errors);
        } catch (IOException | UncheckedIOException ex) {
            throw new BadRequestException("Failed to read CSV file");
        }
    }

    private Ticket parseTicketRecord(CSVRecord record, Project project) {
        String title = getRequired(record, "title");
        String description = getOptional(record, "description");

        TicketStatus status = parseEnum(TicketStatus.class, getRequired(record, "status"), "status");
        if (status != TicketStatus.TODO) {
            throw new BadRequestException("Imported tickets must start in TODO");
        }
        TicketPriority priority = parseEnum(TicketPriority.class, getRequired(record, "priority"), "priority");
        TicketType type = parseEnum(TicketType.class, getRequired(record, "type"), "type");

        User assignee = null;
        String assigneeIdValue = getOptional(record, "assigneeId");
        if (assigneeIdValue != null && !assigneeIdValue.isBlank()) {
            Long assigneeId = parseLong(assigneeIdValue, "assigneeId");
            assignee = userService.findUserEntity(assigneeId);
            if (assignee.getRole() != UserRole.DEVELOPER) {
                throw new BadRequestException("Tickets can only be assigned to developers");
            }
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
