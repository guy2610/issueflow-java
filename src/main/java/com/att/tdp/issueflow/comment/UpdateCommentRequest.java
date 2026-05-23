package com.att.tdp.issueflow.comment;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
        @NotBlank String content
) {
}