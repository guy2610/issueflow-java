package com.att.tdp.issueflow.attachment;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam(required = false) Long uploadedByUserId,
            @RequestPart("file") MultipartFile file
    ) {
        return attachmentService.uploadAttachment(ticketId, uploadedByUserId, file);
    }

    @GetMapping("/tickets/{ticketId}/attachments")
    public List<AttachmentResponse> getAttachmentsForTicket(@PathVariable Long ticketId) {
        return attachmentService.getAttachmentsForTicket(ticketId);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<ByteArrayResource> downloadAttachment(@PathVariable Long attachmentId) {
        Attachment attachment = attachmentService.getAttachmentEntity(attachmentId);

        ByteArrayResource resource = new ByteArrayResource(attachment.getData());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getFileName())
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }
}