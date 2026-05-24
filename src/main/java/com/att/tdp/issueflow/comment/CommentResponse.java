package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.mention.MentionedUserResponse;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id,
        Long version,
        Long ticketId,
        Long authorId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        List<MentionedUserResponse> mentionedUsers
) {
    public static CommentResponse from(Comment comment) {
        return from(comment, List.of());
    }

    public static CommentResponse from(
            Comment comment,
            List<MentionedUserResponse> mentionedUsers
    ) {
        return new CommentResponse(
                comment.getId(),
                comment.getVersion(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                mentionedUsers
        );
    }
}