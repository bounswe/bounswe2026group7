package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.scheduler.BanExpiryScheduler;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.awaitility.Awaitility;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the auto-ban system (#134, req 2.2.4):
 * cancel endpoint, escalation curve, request-create gate, admin endpoints,
 * and the expiry scheduler. The scheduler bean is enabled here via
 * {@code @TestPropertySource} so the test can drive it directly; the
 * default test profile keeps it off to avoid stray cron firings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.bans.expiry-notification-enabled=true",
        // "-" keeps the bean wired but suppresses cron firings; tests drive
        // sweepExpired() directly.
        "app.bans.expiry-notification.cron=-"
})
class BanIntegrationTest {

    private static final String ADMIN_EMAIL = "ban-admin@test.local";
    private static final String ADMIN_PASSWORD = "AdminBanTestPwd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private BanRepository banRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BanExpiryScheduler banExpiryScheduler;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
        banRepository.deleteAll();
        notificationRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    // ── Mentee cancel endpoint ───────────────────────────────────────────

    @Test
    void mentee_cancelOwnPendingRequest_returns204_andRecordsViolation() throws Exception {
        Mentor mentor = setupMentor("ban_mentor1@test.com", 5);
        String menteeToken = registerAndLogin("ban_mentee1@test.com", false);
        Long requestId = postRequest(menteeToken, mentor.getId());

        mockMvc.perform(delete("/api/mentorship-requests/" + requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isNoContent());

        Long menteeId = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ban_mentee1@test.com"))
                .findFirst().orElseThrow().getId();
        Mentee refreshed = menteeRepository.findById(menteeId).orElseThrow();
        assertThat(refreshed.getCancelCount()).isEqualTo(1);
    }

    @Test
    void mentee_cancelRequestNotOwned_returns409() throws Exception {
        Mentor mentor = setupMentor("ban_mentor2@test.com", 5);
        String firstMentee = registerAndLogin("ban_mentee2a@test.com", false);
        String secondMentee = registerAndLogin("ban_mentee2b@test.com", false);
        Long requestId = postRequest(firstMentee, mentor.getId());

        mockMvc.perform(delete("/api/mentorship-requests/" + requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondMentee))
                .andExpect(status().isConflict());
    }

    // ── Escalation curve & request-create gate ───────────────────────────

    @Test
    void thirdCancellation_imposesFirstBan_andBlocksNextCreate() throws Exception {
        // Three different mentors so the 3 requests don't trip the duplicate-pending check.
        Mentor m1 = setupMentor("ban_mentor3a@test.com", 5);
        Mentor m2 = setupMentor("ban_mentor3b@test.com", 5);
        Mentor m3 = setupMentor("ban_mentor3c@test.com", 5);
        String menteeToken = registerAndLogin("ban_mentee3@test.com", false);

        Long r1 = postRequest(menteeToken, m1.getId());
        cancel(menteeToken, r1);
        Long r2 = postRequest(menteeToken, m2.getId());
        cancel(menteeToken, r2);
        Long r3 = postRequest(menteeToken, m3.getId());
        cancel(menteeToken, r3);

        // 3rd cancellation crossed the threshold → ban active. Next create returns 403.
        Mentor m4 = setupMentor("ban_mentor3d@test.com", 5);
        MvcResult forbidden = mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", m4.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.banCount").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(forbidden.getResponse().getContentAsString());
        OffsetDateTime expiresAt = OffsetDateTime.parse(body.get("expiresAt").asText());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Default first-ban hours = 24. Allow ±1h slack for clock drift.
        assertThat(Duration.between(now, expiresAt).toHours()).isBetween(23L, 25L);

        // USER_BANNED notification persisted in-app.
        Long menteeId = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ban_mentee3@test.com"))
                .findFirst().orElseThrow().getId();
        awaitNotificationFor(menteeId, NotificationType.USER_BANNED);
    }

    // ── Admin endpoints ──────────────────────────────────────────────────

    @Test
    void admin_canListAndLiftBan_andLiftSendsNotification() throws Exception {
        Admin admin = seedAdmin();
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        // Pre-seed an active ban via the repository directly.
        String menteeToken = registerAndLogin("ban_mentee4@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ban_mentee4@test.com"))
                .findFirst().orElseThrow();
        Ban active = new Ban();
        active.setUser(mentee);
        active.setReason("Frequent cancellations");
        active.setBanCount(1);
        active.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
        Ban saved = banRepository.save(active);

        // Admin lists.
        mockMvc.perform(get("/api/admin/bans/users/" + mentee.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(saved.getId()));

        // Non-admin (mentee themselves) is forbidden.
        mockMvc.perform(get("/api/admin/bans/users/" + mentee.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());

        // Admin lifts.
        mockMvc.perform(delete("/api/admin/bans/" + saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liftedAt").exists())
                .andExpect(jsonPath("$.liftedByAdminId").value(admin.getId()));

        Ban refreshed = banRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getLiftedAt()).isNotNull();
        assertThat(refreshed.getLiftedByAdminId()).isEqualTo(admin.getId());

        // BAN_LIFTED notification fired.
        awaitNotificationFor(mentee.getId(), NotificationType.BAN_LIFTED);
    }

    @Test
    void mentee_cannotAccessAdminBanEndpoints() throws Exception {
        Mentor mentor = setupMentor("ban_mentor5@test.com", 5);
        String menteeToken = registerAndLogin("ban_mentee5@test.com", false);

        mockMvc.perform(delete("/api/admin/bans/" + 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());

        // Use mentor.getId() so the path is well-formed; auth gate fires first.
        mockMvc.perform(get("/api/admin/bans/users/" + mentor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    // ── BanExpiryScheduler ────────────────────────────────────────────────

    @Test
    void banExpiryScheduler_firesNotificationOnceForExpiredBan() throws Exception {
        String menteeToken = registerAndLogin("ban_mentee6@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ban_mentee6@test.com"))
                .findFirst().orElseThrow();

        Ban expired = new Ban();
        expired.setUser(mentee);
        expired.setReason("test");
        expired.setBanCount(1);
        expired.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        expired.setExpiryNotified(false);
        Ban saved = banRepository.save(expired);

        banExpiryScheduler.sweepExpired();

        Ban refreshed = banRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.isExpiryNotified()).isTrue();
        awaitNotificationFor(mentee.getId(), NotificationType.BAN_EXPIRED);

        // Idempotent: second sweep does not double-fire.
        long beforeCount = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.BAN_EXPIRED)
                .count();
        banExpiryScheduler.sweepExpired();
        long afterCount = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.BAN_EXPIRED)
                .count();
        assertThat(afterCount).isEqualTo(beforeCount);

        // Mentee can use the system again — scheduler does not gate on its own,
        // the createRequest ban check sees no active ban (already expired).
        Mentor mentor = setupMentor("ban_mentor6@test.com", 5);
        mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()))))
                .andExpect(status().isCreated());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Mentor setupMentor(String email, int capacity) throws Exception {
        registerAndLogin(email, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(capacity);
        return mentorRepository.save(mentor);
    }

    private Long postRequest(String menteeToken, Long mentorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", mentorId))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void cancel(String menteeToken, Long requestId) throws Exception {
        mockMvc.perform(delete("/api/mentorship-requests/" + requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isNoContent());
    }

    private void awaitNotificationFor(Long recipientId, NotificationType type) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    List<Notification> notifications =
                            notificationRepository.findForUser(recipientId, false);
                    assertThat(notifications)
                            .extracting(Notification::getType)
                            .contains(type);
                });
    }

    private Admin seedAdmin() {
        Admin admin = new Admin();
        admin.setFirstName("Bootstrap");
        admin.setLastName("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setIsEmailVerified(true);
        return userRepository.save(admin);
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(isMentor ? "Mentor" : "Mentee");
        req.setLastName("User");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        return login(email, "Password1", isMentor ? "MENTOR" : "MENTEE");
    }
}
