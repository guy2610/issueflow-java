package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.CommentResponse;

import java.util.List;

public record MentionPageResponse(
        List<CommentResponse> data,
        int total,
        int page
) {
    public static MentionPageResponse of(List<CommentResponse> data, int page) {
        return new MentionPageResponse(data, data.size(), page);
    }
}