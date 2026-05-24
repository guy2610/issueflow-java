package com.att.tdp.issueflow.attachment;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        Long uploadedByUserId,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getUploadedBy() == null ? null : attachment.getUploadedBy().getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }
}