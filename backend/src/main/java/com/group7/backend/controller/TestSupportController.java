package com.group7.backend.controller;

import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.PasswordResetToken;
import com.group7.backend.entity.User;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Test-only support endpoints for the Playwright E2E suite (#315).
 *
 * <p>Bean is registered only when {@code app.test-endpoints.enabled=true}; in
 * production the property is unset/false so this controller never enters the
 * application context. {@link com.group7.backend.config.SecurityConfig} also
 * 404s {@code /api/test/**} when the flag is off as defence-in-depth.
 */
@RestController
@RequestMapping("/api/test")
@ConditionalOnProperty(name = "app.test-endpoints.enabled", havingValue = "true")
public class TestSupportController {

    private static final Logger log = LoggerFactory.getLogger(TestSupportController.class);

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final AuthService authService;
    private final Faker faker = new Faker(Locale.of("tr"));

    @PersistenceContext
    private EntityManager entityManager;

    public TestSupportController(UserRepository userRepository,
                                 VerificationTokenRepository verificationTokenRepository,
                                 AuthService authService) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.authService = authService;
        log.warn("TestSupportController is ENABLED — this MUST NOT happen in production");
    }

    /**
     * Wipes all user-derived test state. TRUNCATE CASCADE on {@code users}
     * propagates to mentors/mentees and every FK dependent (mentorships,
     * tokens, notifications, conversations, ...) so subsequent runs start
     * from a clean slate.
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> reset() {
        entityManager.createNativeQuery("TRUNCATE TABLE users RESTART IDENTITY CASCADE").executeUpdate();
        log.info("TestSupportController.reset truncated users + cascaded dependents");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verification-token")
    public ResponseEntity<Map<String, String>> verificationToken(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        List<VerificationToken> tokens = verificationTokenRepository.findByUserIdAndUsedFalse(user.getId());
        VerificationToken latest = tokens.stream()
                .max(Comparator.comparing(VerificationToken::getCreatedAt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no unused verification token"));
        return ResponseEntity.ok(Map.of("token", latest.getToken()));
    }

    @GetMapping("/password-reset-token")
    public ResponseEntity<Map<String, String>> passwordResetToken(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        List<PasswordResetToken> tokens = entityManager.createQuery(
                        "select t from PasswordResetToken t "
                                + "where t.user.id = :uid and t.used = false "
                                + "order by t.createdAt desc",
                        PasswordResetToken.class)
                .setParameter("uid", user.getId())
                .setMaxResults(1)
                .getResultList();
        if (tokens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no unused password reset token");
        }
        return ResponseEntity.ok(Map.of("token", tokens.get(0).getToken()));
    }

    /**
     * Seeds a Faker-generated user via the production register flow, then
     * (optionally) flips {@code is_email_verified=true} so non-AT-01 specs can
     * skip the verify step. NoOpEmailService suppresses the outbound HTTP call.
     */
    @PostMapping("/users")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedUser(@RequestBody SeedUserRequest body) {
        boolean isMentor = Boolean.TRUE.equals(body.isMentor());
        boolean preVerified = body.preVerified() == null || Boolean.TRUE.equals(body.preVerified());

        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String email = faker.internet().emailAddress();
        String password = "Pass!" + faker.number().digits(6);

        RegisterRequest req = new RegisterRequest();
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setEmail(email);
        req.setPassword(password);
        req.setIsMentor(isMentor);

        UserResponse created = authService.register(req);

        if (preVerified) {
            User saved = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("seeded user vanished"));
            saved.setIsEmailVerified(true);
            userRepository.save(saved);
        }

        return ResponseEntity.ok(Map.of(
                "id", created.getId(),
                "email", email,
                "password", password,
                "role", created.getRole(),
                "firstName", firstName,
                "lastName", lastName
        ));
    }

    public record SeedUserRequest(Boolean isMentor, Boolean preVerified) {}
}
