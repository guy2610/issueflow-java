package io.github.guyeven.issueflow.user;

import io.github.guyeven.issueflow.common.error.ConflictException;
import io.github.guyeven.issueflow.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.common.error.ForbiddenException;
import io.github.guyeven.issueflow.common.security.CurrentUserService;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == UserRole.ADMIN && currentUserService.getCurrentUser()
                .map(user -> user.getRole() != UserRole.ADMIN)
                .orElse(true)) {
            throw new ForbiddenException("Only an administrator can create an administrator account");
        }

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

        user.setPasswordHash(passwordEncoder.encode(request.password()));

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
