package io.github.guyeven.issueflow.user;

import io.github.guyeven.issueflow.auth.PasswordConfig;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PasswordConfig.class)
class BootstrapAdminInitializerTests {

    private static final String BOOTSTRAP_PASSWORD = "bootstrap-password";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void noConfigurationDoesNotCreateAdmin() {
        initializer().initializeAdmin("", "", "");

        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();
    }

    @Test
    void completeConfigurationCreatesAdminWhenNoneExists() {
        initializer().initializeAdmin("bootstrap-admin", "bootstrap@example.com", BOOTSTRAP_PASSWORD);

        User admin = userRepository.findByUsername("bootstrap-admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("bootstrap@example.com");
    }

    @Test
    void existingAdminPreventsAnotherBootstrapAdmin() {
        saveAdmin("existing-admin", "existing@example.com");

        initializer().initializeAdmin("second-admin", "second@example.com", BOOTSTRAP_PASSWORD);

        assertThat(userRepository.findByUsername("second-admin")).isEmpty();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void bootstrapPasswordIsEncoded() {
        initializer().initializeAdmin("encoded-admin", "encoded@example.com", BOOTSTRAP_PASSWORD);

        String storedHash = userRepository.findByUsername("encoded-admin").orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(BOOTSTRAP_PASSWORD);
        assertThat(passwordEncoder.matches(BOOTSTRAP_PASSWORD, storedHash)).isTrue();
    }

    private BootstrapAdminInitializer initializer() {
        return new BootstrapAdminInitializer(
                userRepository, passwordEncoder, validator, "", "", "");
    }

    private void saveAdmin(String username, String email) {
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFullName("Existing Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(BOOTSTRAP_PASSWORD));
        userRepository.saveAndFlush(admin);
    }
}
