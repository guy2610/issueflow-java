package com.att.tdp.issueflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
        @NotBlank String name,
        String description,
        @NotNull Long ownerId
) {
}