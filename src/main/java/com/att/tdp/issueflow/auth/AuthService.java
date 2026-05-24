package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.common.error.UnauthorizedException;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenDenyListService tokenDenyListService;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenDenyListService tokenDenyListService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenDenyListService = tokenDenyListService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        auditLogService.recordUserAction(
                AuditAction.LOGIN,
                user.getId(),
                AuditEntityType.AUTH,
                user.getId(),
                "User logged in"
        );

        return LoginResponse.bearer(jwtService.generateToken(user));
    }

    public void logout(String token) {
        tokenDenyListService.deny(token);
        auditLogService.recordCurrentUserAction(
                AuditAction.LOGOUT,
                AuditEntityType.AUTH,
                null,
                "User logged out"
        );
    }

    public UserResponse me(User user) {
        return UserResponse.from(user);
    }
}