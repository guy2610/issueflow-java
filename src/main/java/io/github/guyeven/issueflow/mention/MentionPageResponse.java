package io.github.guyeven.issueflow.mention;

import io.github.guyeven.issueflow.comment.CommentResponse;

import java.util.List;

public record MentionPageResponse(
        List<CommentResponse> data,
        int total,
        int page
) {
    public static MentionPageResponse of(List<CommentResponse> data, int total, int page) {
        return new MentionPageResponse(data, total, page);
    }
}
