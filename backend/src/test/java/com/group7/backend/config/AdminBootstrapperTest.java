package com.group7.backend.config;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.admin-bootstrap.enabled=true",
        "app.admin-bootstrap.email=bootstrap-admin@test.local",
        "app.admin-bootstrap.password=Sup3rSecurePwd!",
        "app.admin-bootstrap.first-name=Test",
        "app.admin-bootstrap.last-name=Admin"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminBootstrapperTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AdminBootstrapper adminBootstrapper;
    @Autowired private AdminBootstrapProperties props;

    @AfterEach
    void cleanup() {
        userRepository.findByEmail(props.getEmail()).ifPresent(userRepository::delete);
    }

    @Test
    void contextLoadCreatesAdminAndRerunIsIdempotent() {
        // Explicitly run the bootstrapper in case ApplicationRunner was skipped
        // due to Spring TestContext caching or other test suite quirks.
        adminBootstrapper.run(null);

        Optional<User> created = userRepository.findByEmail(props.getEmail());
        assertThat(created)
                .as("admin should be created at context startup")
                .isPresent();
        User user = created.orElseThrow();
        assertThat(user).isInstanceOf(Admin.class);
        assertThat(user.getFirstName()).isEqualTo("Test");
        assertThat(user.getLastName()).isEqualTo("Admin");
        assertThat(user.getIsEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches("Sup3rSecurePwd!", user.getPasswordHash()))
                .as("password hash should match the bootstrap password")
                .isTrue();

        // Idempotency: running the bootstrapper a second time must not duplicate.
        long countBefore = userRepository.count();
        adminBootstrapper.run(null);
        long countAfter = userRepository.count();
        assertThat(countAfter)
                .as("idempotent rerun must not create a duplicate admin")
                .isEqualTo(countBefore);
    }
}
