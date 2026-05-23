package com.att.tdp.issueflow.comment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long version,
        Long ticketId,
        Long authorId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getVersion(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}