package io.github.guyeven.issueflow.attachment;

public record AttachmentDownload(
        Attachment attachment,
        byte[] data
) {
}