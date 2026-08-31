package io.github.guyeven.issueflow.mention;

import io.github.guyeven.issueflow.user.User;

public record MentionedUserResponse(
        Long id,
        String username,
        String fullName
) {
    public static MentionedUserResponse from(User user) {
        return new MentionedUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName()
        );
    }
}