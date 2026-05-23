package com.att.tdp.issueflow.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        String fullName,
        @NotNull UserRole role
) {
}