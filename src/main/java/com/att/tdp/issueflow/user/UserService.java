package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.error.ConflictException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEntityType;
import com.att.tdp.issueflow.audit.AuditLogService;

import java.util.List;

@Service
public class UserService {

    private static final String DEFAULT_PASSWORD = "secret";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(request.role());

        String rawPassword = request.password() == null || request.password().isBlank()
                ? DEFAULT_PASSWORD
                : request.password();

        // Temporary plain placeholder until Spring Security password encoding is added.
        // We will replace this with BCrypt when implementing auth.
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        User saved = userRepository.save(user);
        auditLogService.recordCurrentUserAction(
                AuditAction.CREATE,
                AuditEntityType.USER,
                saved.getId(),
                "User registered: " + saved.getUsername()
        );
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(findUserEntity(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUserEntity(id);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }

        user.setRole(request.role());

        auditLogService.recordCurrentUserAction(
                AuditAction.UPDATE,
                AuditEntityType.USER,
                user.getId(),
                "User updated"
        );
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findUserEntity(id);
        userRepository.delete(user);
        auditLogService.recordCurrentUserAction(
                AuditAction.DELETE,
                AuditEntityType.USER,
                user.getId(),
                "User deleted"
        );
    }

    public User findUserEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}