package com.group7.backend.controller;

import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.PasswordResetToken;
import com.group7.backend.entity.User;
import com.group7.backend.entity.VerificationToken;
import com.group7.backend.config.ratelimit.BucketCache;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.scheduler.MeetingSchedulerProcessor;
import com.group7.backend.scheduler.ReminderNotificationScheduler;
import com.group7.backend.service.AuthService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorshipAutoCompletionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final Optional<BucketCache> bucketCache;
    private final Optional<MeetingSchedulerProcessor> meetingSchedulerProcessor;
    private final Optional<MeetingRepository> meetingRepository;
    private final Optional<MentorshipAutoCompletionService> mentorshipAutoCompletionService;
    private final Optional<ReminderNotificationScheduler> reminderNotificationScheduler;
    private final Clock clock;
    private final Faker faker = new Faker(Locale.of("tr"));

    @PersistenceContext
    private EntityManager entityManager;

    public TestSupportController(UserRepository userRepository,
                                 VerificationTokenRepository verificationTokenRepository,
                                 AuthService authService,
                                 JwtService jwtService,
                                 PasswordEncoder passwordEncoder,
                                 Optional<BucketCache> bucketCache,
                                 Optional<MeetingSchedulerProcessor> meetingSchedulerProcessor,
                                 Optional<MeetingRepository> meetingRepository,
                                 Optional<MentorshipAutoCompletionService> mentorshipAutoCompletionService,
                                 Optional<ReminderNotificationScheduler> reminderNotificationScheduler,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.authService = authService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.bucketCache = bucketCache;
        this.meetingSchedulerProcessor = meetingSchedulerProcessor;
        this.meetingRepository = meetingRepository;
        this.mentorshipAutoCompletionService = mentorshipAutoCompletionService;
        this.reminderNotificationScheduler = reminderNotificationScheduler;
        this.clock = clock;
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

        // Pre-mint a session token so specs can skip the UI login when they
        // just need an authenticated request context (used by AT-02 for the
        // schedule/task/blog API legs).
        String sessionToken = jwtService.generateToken(created.getId(), email, created.getRole());

        return ResponseEntity.ok(Map.of(
                "id", created.getId(),
                "email", email,
                "password", password,
                "role", created.getRole(),
                "firstName", firstName,
                "lastName", lastName,
                "sessionToken", sessionToken
        ));
    }

    /**
     * Seeds an Admin user (the production AuthService.register flow only
     * builds Mentor/Mentee), bypasses verification, and returns a JWT so
     * Playwright specs can persist it as a {@code storageState} fixture for
     * AT-05 admin-review flows. Bean is still gated by
     * {@code app.test-endpoints.enabled=true}.
     */
    @PostMapping("/admin")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedAdmin() {
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String email = "e2e-admin-" + faker.regexify("[a-z0-9]{8}") + "@example.com";
        String password = "Admin!" + faker.number().digits(6);

        Admin admin = new Admin();
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setIsEmailVerified(true);
        Admin saved = userRepository.save(admin);

        String sessionToken = jwtService.generateToken(saved.getId(), email, "ADMIN");

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "email", email,
                "password", password,
                "role", "ADMIN",
                "firstName", firstName,
                "lastName", lastName,
                "sessionToken", sessionToken
        ));
    }

    /**
     * Wipes the in-memory rate-limit bucket cache so a single CI run can
     * exhaust a bucket (AT-07's 11-login probe) without leaking state into
     * subsequent tests. No-op when the rate-limit autoconfig isn't on the
     * classpath; returns 204 either way so callers don't need to branch.
     */
    @PostMapping("/reset-ratelimits")
    public ResponseEntity<Void> resetRateLimits() {
        bucketCache.ifPresent(BucketCache::clear);
        log.info("TestSupportController.resetRateLimits cleared bucket cache "
                + "(present={})", bucketCache.isPresent());
        return ResponseEntity.noContent().build();
    }

    /**
     * Manually fires {@link MeetingSchedulerProcessor#sendReminders} so AT-06
     * can verify the meeting-reminder leg without waiting up to 5 minutes for
     * the production cron tick. The "now" parameter the production scheduler
     * uses is mirrored here so a spec can position a meeting and then trigger
     * the same window evaluation.
     */
    @PostMapping("/trigger-meeting-reminders")
    public ResponseEntity<Map<String, Object>> triggerMeetingReminders() {
        if (meetingSchedulerProcessor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MeetingSchedulerProcessor not available — scheduler likely disabled");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        meetingSchedulerProcessor.get().sendReminders(now);
        return ResponseEntity.ok(Map.of("triggeredAt", now.toString()));
    }

    /**
     * Sweeps {@code PENDING_CONFIRMATION} meetings whose confirmation deadline
     * has passed and dispatches each to
     * {@link MeetingSchedulerProcessor#processPending} so the row flips to
     * {@code EXPIRED} and the auto-decline notification fires. Used by AT-13
     * Step 8 to fast-forward the meeting auto-decline scheduler without
     * waiting for the production cron tick.
     */
    @PostMapping("/trigger-meeting-auto-decline")
    public ResponseEntity<Map<String, Object>> triggerMeetingAutoDecline() {
        if (meetingSchedulerProcessor.isEmpty() || meetingRepository.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MeetingScheduler beans missing — scheduler likely disabled");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Meeting> pending = meetingRepository.get()
                .findByStatusAndConfirmationDeadlineBefore(MeetingStatus.PENDING_CONFIRMATION, now);
        int count = 0;
        for (Meeting m : pending) {
            if (meetingSchedulerProcessor.get().processPending(m)) {
                count++;
            }
        }
        return ResponseEntity.ok(Map.of(
                "processedCount", count,
                "triggeredAt", now.toString()
        ));
    }

    /**
     * Wraps {@link MentorshipAutoCompletionService#autoCompleteExpired} so a
     * spec can finalise an ACTIVE mentorship whose {@code endDate} has passed
     * without waiting on the hourly cron. Used by AT-14 Step 6 to fast-forward
     * the mentorship auto-completion sweep.
     */
    @PostMapping("/trigger-mentorship-auto-completion")
    public ResponseEntity<Map<String, Object>> triggerMentorshipAutoCompletion() {
        if (mentorshipAutoCompletionService.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MentorshipAutoCompletionService not available");
        }
        int completed = mentorshipAutoCompletionService.get().autoCompleteExpired();
        return ResponseEntity.ok(Map.of("completedCount", completed));
    }

    /**
     * Wraps {@link ReminderNotificationScheduler#processReminders} so a spec
     * can drive the task/milestone deadline-reminder pass without waiting on
     * the half-hourly cron. Used by AT-15 Step 5 to fast-forward the reminder
     * notification scheduler.
     */
    @PostMapping("/trigger-reminder-scheduler")
    public ResponseEntity<Map<String, Object>> triggerReminderScheduler() {
        if (reminderNotificationScheduler.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ReminderNotificationScheduler not available — reminders disabled");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        reminderNotificationScheduler.get().processReminders();
        return ResponseEntity.ok(Map.of("triggeredAt", now.toString()));
    }

    public record SeedUserRequest(Boolean isMentor, Boolean preVerified) {}
}
