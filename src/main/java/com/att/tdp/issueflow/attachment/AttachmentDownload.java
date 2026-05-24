package com.att.tdp.issueflow.attachment;

public record AttachmentDownload(
        Attachment attachment,
        byte[] data
) {
}