package com.group7.backend.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.ratelimit.MutableClock;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.BotSignalRepository;
import com.group7.backend.repository.MeetingActionItemRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MeetingRescheduleRequestRepository;
import com.group7.backend.repository.MentorRatingRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.TaskSubmissionRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Base for end-to-end (cross-feature) integration tests added by issue #254.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li><b>Boot one Spring context</b> for the whole subclass via
 *       {@code @SpringBootTest} + {@code @AutoConfigureMockMvc}, with the
 *       application clock pinned by {@link E2EClockConfig}.</li>
 *   <li><b>Wipe state</b> in {@link #cleanDb()} between scenarios so each
 *       {@code @Test} starts from an empty database. Order matches the
 *       FK dependency chain; {@code ON DELETE CASCADE} would suffice for
 *       most child rows, but the explicit list documents the chain and
 *       insulates the suite from Hibernate flush-order surprises.</li>
 *   <li><b>Expose builders</b> via the {@link #api} field, so subclasses
 *       compose flows like {@code api.users().asMentor().email(...).
 *       registerVerifyAndLogin()} without touching MockMvc directly.</li>
 * </ol>
 *
 * <p>Do not retrofit existing feature-scoped integration tests onto this
 * base — the goal here is the cross-feature workflow class. Splitting that
 * cleanup into a dedicated follow-up keeps the issue's diff focused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(E2EClockConfig.class)
public abstract class AbstractE2ETest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected MutableClock mutableClock;

    // Repositories — wiped in cleanDb() and used directly by helpers / assertions.
    @Autowired protected UserRepository userRepository;
    @Autowired protected MentorRepository mentorRepository;
    @Autowired protected VerificationTokenRepository verificationTokenRepository;
    @Autowired protected PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired protected MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired protected MentorshipRepository mentorshipRepository;
    @Autowired protected MentorRatingRepository mentorRatingRepository;
    @Autowired protected TaskRepository taskRepository;
    @Autowired protected TaskSubmissionRepository taskSubmissionRepository;
    @Autowired protected MeetingRepository meetingRepository;
    @Autowired protected MeetingActionItemRepository meetingActionItemRepository;
    @Autowired protected MeetingRescheduleRequestRepository meetingRescheduleRequestRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected BanRepository banRepository;
    @Autowired protected BotSignalRepository botSignalRepository;
    @Autowired protected AvailabilitySlotRepository availabilitySlotRepository;

    @MockitoBean protected EmailService emailService;

    protected E2EClient api;

    @BeforeEach
    void cleanDb() {
        // Children → parents (FK direction). Some of these are also covered
        // by ON DELETE CASCADE, but listing them explicitly avoids depending
        // on Hibernate flush order and keeps `clean` ↔ `state` symmetric.
        mentorRatingRepository.deleteAll();
        taskSubmissionRepository.deleteAll();
        taskRepository.deleteAll();
        meetingActionItemRepository.deleteAll();
        meetingRescheduleRequestRepository.deleteAll();
        meetingRepository.deleteAll();
        notificationRepository.deleteAll();
        botSignalRepository.deleteAll();
        banRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        availabilitySlotRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        mutableClock.setNow(E2EClockConfig.FIXED_NOW);

        // Email side effect is the verification token write to the repo —
        // make the send a no-op so the mock never throws.
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());

        api = new E2EClient(mockMvc, objectMapper, userRepository, verificationTokenRepository);
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    /**
     * Mentors register with capacity 0; bumping it via the matching admin
     * flow would add unrelated complexity to every scenario. Direct repo
     * write is the project-standard E2E shortcut (see
     * {@code MentorshipIntegrationTest.acceptRequestCreatesActiveMentorship}).
     */
    protected void setMentorCapacity(UserHandle mentor, int capacity) {
        Mentor m = mentorRepository.findById(mentor.id())
                .orElseThrow(() -> new IllegalStateException("Mentor not found: " + mentor.id()));
        m.setMaxMenteeCapacity(capacity);
        mentorRepository.save(m);
    }

    protected void advanceClock(Duration d) {
        mutableClock.advance(d);
    }

    protected Instant now() {
        return mutableClock.instant();
    }
}
