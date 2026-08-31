package io.github.guyeven.issueflow.auth;

import io.github.guyeven.issueflow.common.error.UnauthorizedException;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.user.UserRepository;
import io.github.guyeven.issueflow.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;

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

        String token = jwtService.generateToken(user);

        auditLogService.recordSecurityEvent(
                AuditAction.LOGIN,
                user.getId(),
                user.getId(),
                "User logged in"
        );

        return LoginResponse.bearer(token, jwtService.getExpirationSeconds());
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
