package com.att.tdp.issueflow.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotBlank String content,
        @NotNull Long authorId
) {
}