package io.github.guyeven.issueflow.attachment;

import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.common.error.BadRequestException;
import io.github.guyeven.issueflow.common.error.NotFoundException;
import io.github.guyeven.issueflow.ticket.Ticket;
import io.github.guyeven.issueflow.ticket.TicketService;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.common.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class AttachmentService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf",
            "text/plain"
    );

    @Value("${app.attachments.storage-dir:uploads/attachments}")
    private String storageDir;

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;


    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketService ticketService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService
    ) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Attachment exceeds maximum file size of 10 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Unsupported attachment content type: " + contentType);
        }

        Ticket ticket = ticketService.findActiveTicketEntity(ticketId);

        User uploadedBy = currentUserService.requireCurrentUser();

        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setUploadedBy(uploadedBy);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());

        String originalFileName = safeFileName(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + "-" + originalFileName;

        Path directory = Path.of(storageDir);
        Path targetPath = directory.resolve(storedFileName).normalize();

        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to store attachment file");
        }

        attachment.setOriginalFileName(originalFileName);
        attachment.setStoredFileName(storedFileName);
        attachment.setStoragePath(targetPath.toString());

        Attachment saved = attachmentRepository.save(attachment);

        auditLogService.recordCurrentUserAction(
                AuditAction.CREATE,
                AuditEntityType.ATTACHMENT,
                saved.getId(),
                "Attachment uploaded to ticket " + ticketId
        );

        return AttachmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsForTicket(Long ticketId) {
        ticketService.findActiveTicketEntity(ticketId);

        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Attachment getAttachmentEntity(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = getAttachmentEntity(attachmentId);

        try {
            Files.deleteIfExists(Path.of(attachment.getStoragePath()));
        } catch (IOException ex) {
            throw new BadRequestException("Failed to delete attachment file");
        }

        attachmentRepository.delete(attachment);

        auditLogService.recordCurrentUserAction(
                AuditAction.DELETE,
                AuditEntityType.ATTACHMENT,
                attachmentId,
                "Attachment deleted"
        );
    }

    @Transactional
    public void deleteAttachment(Long ticketId, Long attachmentId) {
        ticketService.findActiveTicketEntity(ticketId);
        Attachment attachment = getAttachmentEntity(attachmentId);
        if (!attachment.getTicket().getId().equals(ticketId)) {
            throw new NotFoundException("Attachment not found for ticket: " + attachmentId);
        }
        deleteAttachment(attachmentId);
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "attachment";
        }

        return originalFileName.replace("\\", "_").replace("/", "_");
    }
    public AttachmentDownload downloadAttachment(Long attachmentId) {
        Attachment attachment = getAttachmentEntity(attachmentId);

        try {
            byte[] data = Files.readAllBytes(Path.of(attachment.getStoragePath()));
            return new AttachmentDownload(attachment, data);
        } catch (IOException ex) {
            throw new NotFoundException("Attachment file not found on disk: " + attachmentId);
        }
    }

}
