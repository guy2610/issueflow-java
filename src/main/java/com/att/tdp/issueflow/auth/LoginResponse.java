package com.att.tdp.issueflow.auth;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
    public static LoginResponse bearer(String token) {
        return new LoginResponse(token, "Bearer");
    }
}