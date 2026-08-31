package io.github.guyeven.issueflow.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        String fullName,
        @NotNull UserRole role
) {
}