package com.att.tdp.issueflow.auth;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenDenyListService {

    private final Set<String> deniedTokens = ConcurrentHashMap.newKeySet();

    public void deny(String token) {
        deniedTokens.add(token);
    }

    public boolean isDenied(String token) {
        return deniedTokens.contains(token);
    }
}