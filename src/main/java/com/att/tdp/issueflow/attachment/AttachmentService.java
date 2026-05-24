package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;
import com.att.tdp.issueflow.common.error.BadRequestException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    private final AttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketService ticketService,
            UserService userService,
            AuditLogService auditLogService
    ) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AttachmentResponse uploadAttachment(Long ticketId, Long uploadedByUserId, MultipartFile file) {
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

        User uploadedBy = null;
        if (uploadedByUserId != null) {
            uploadedBy = userService.findUserEntity(uploadedByUserId);
        }

        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setUploadedBy(uploadedBy);
        attachment.setFileName(safeFileName(file.getOriginalFilename()));
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());

        try {
            attachment.setData(file.getBytes());
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read attachment data");
        }

        Attachment saved = attachmentRepository.save(attachment);

        auditLogService.recordUserAction(
                AuditAction.CREATE,
                uploadedBy == null ? null : uploadedBy.getId(),
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

        Long uploadedByUserId = attachment.getUploadedBy() == null
                ? null
                : attachment.getUploadedBy().getId();

        attachmentRepository.delete(attachment);

        auditLogService.recordUserAction(
                AuditAction.DELETE,
                uploadedByUserId,
                AuditEntityType.ATTACHMENT,
                attachmentId,
                "Attachment deleted"
        );
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "attachment";
        }

        return originalFileName.replace("\\", "_").replace("/", "_");
    }
}