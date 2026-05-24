package com.att.tdp.issueflow.common.security;

import com.att.tdp.issueflow.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User user) {
            return Optional.of(user);
        }

        return Optional.empty();
    }

    public Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }
}