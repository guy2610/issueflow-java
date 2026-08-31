package io.github.guyeven.issueflow.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Validator validator,
            @Value("${issueflow.bootstrap.admin.username:}") String username,
            @Value("${issueflow.bootstrap.admin.email:}") String email,
            @Value("${issueflow.bootstrap.admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeAdmin(username, email, password);
    }

    void initializeAdmin(String configuredUsername, String configuredEmail, String configuredPassword) {
        if (isBlank(configuredUsername) || isBlank(configuredEmail) || isBlank(configuredPassword)) {
            return;
        }

        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        CreateUserRequest request = new CreateUserRequest(
                configuredUsername,
                configuredEmail,
                configuredUsername,
                UserRole.ADMIN,
                configuredPassword
        );
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid bootstrap administrator configuration: " + message);
        }

        User admin = new User();
        admin.setUsername(request.username());
        admin.setEmail(request.email());
        admin.setFullName(request.fullName());
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(admin);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
