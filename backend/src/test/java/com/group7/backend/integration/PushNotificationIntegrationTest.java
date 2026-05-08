package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.NotificationPreferencesUpdateRequest;
import com.group7.backend.dto.request.RegisterDeviceRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserDeviceRepository;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.NotificationEventPublisher;
import com.group7.backend.service.PushDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the push notification stack (#136). The
 * {@link PushDeliveryService} bean is replaced by a Mockito mock so the
 * test asserts dispatch behaviour without requiring real FCM credentials.
 *
 * <p>SLA: req 2.3.1 sets a 5-second ceiling on event-publication →
 * dispatch latency. The {@code REQUEST_SUBMITTED end-to-end} test
 * captures elapsed time across the AFTER_COMMIT + @Async hop and
 * asserts it lands inside the budget.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushNotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserDeviceRepository userDeviceRepository;
    @Autowired private UserNotificationPreferencesRepository preferencesRepository;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @Autowired private NotificationEventPublisher notificationEventPublisher;

    @MockitoBean private EmailService emailService;
    @MockitoBean private PushDeliveryService pushDeliveryService;

    @BeforeEach
    void cleanDb() {
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
        userDeviceRepository.deleteAll();
        preferencesRepository.deleteAll();
        notificationRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
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

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private Notification waitForNotification(Long recipientId, NotificationType type) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            List<Notification> notifications = notificationRepository.findForUser(recipientId, false);
            for (Notification notification : notifications) {
                if (notification.getType() == type) {
                    return notification;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    // ── Device round-trip ────────────────────────────────────────────────────

    @Test
    void deviceRegisterAndUnregisterRoundTrip() throws Exception {
        String menteeToken = registerAndLogin("push_mentee1@test.com", false);
        Long menteeId = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentee1@test.com"))
                .findFirst().orElseThrow().getId();

        RegisterDeviceRequest body = new RegisterDeviceRequest();
        body.setToken("device-token-A");

        mockMvc.perform(post("/api/users/me/devices")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(menteeId);
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).getToken()).isEqualTo("device-token-A");

        mockMvc.perform(delete("/api/users/me/devices/device-token-A")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isNoContent());

        assertThat(userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(menteeId)).isEmpty();
    }

    // ── Cap eviction ─────────────────────────────────────────────────────────

    @Test
    void registeringSixthDeviceEvictsOldest() throws Exception {
        String menteeToken = registerAndLogin("push_mentee2@test.com", false);
        Long menteeId = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentee2@test.com"))
                .findFirst().orElseThrow().getId();

        for (int i = 1; i <= 6; i++) {
            RegisterDeviceRequest body = new RegisterDeviceRequest();
            body.setToken("device-" + i);
            mockMvc.perform(post("/api/users/me/devices")
                            .header("Authorization", "Bearer " + menteeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());
            // Sleep a hair so last_seen_at orders deterministically.
            Thread.sleep(10);
        }

        List<UserDevice> survivors = userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(menteeId);
        assertThat(survivors).hasSize(5);
        assertThat(survivors.stream().map(UserDevice::getToken))
                .containsExactlyInAnyOrder("device-2", "device-3", "device-4", "device-5", "device-6");
    }

    // ── Preference gating ────────────────────────────────────────────────────

    @Test
    void disabledCategorySkipsPushButPersistsInApp() throws Exception {
        String menteeToken = registerAndLogin("push_mentee3@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentee3@test.com"))
                .findFirst().orElseThrow();

        // Disable the messages category
        NotificationPreferencesUpdateRequest update = new NotificationPreferencesUpdateRequest();
        update.setMessagesEnabled(false);
        mockMvc.perform(patch("/api/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        notificationEventPublisher.publishNewMessage(mentee.getId(), "Bob");

        // The in-app row is still persisted (in-app is the source of truth).
        Notification persisted = waitForNotification(mentee.getId(), NotificationType.NEW_MESSAGE);
        assertThat(persisted).isNotNull();

        // The push transport must observe the toggle. Because dispatch happens
        // on AFTER_COMMIT + @Async, give the listener a beat to settle, then
        // assert no call.
        Thread.sleep(500);
        verify(pushDeliveryService, never())
                .send(eq(mentee.getId()), eq(NotificationType.NEW_MESSAGE), anyString(), anyString());
    }

    // ── REQUEST_SUBMITTED + 5-second SLA ─────────────────────────────────────

    @Test
    void requestSubmittedFiresEndToEndWithinSlaBudget() throws Exception {
        registerAndLogin("push_mentor4@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentor4@test.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("push_mentee4@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentee4@test.com"))
                .findFirst().orElseThrow();

        long startNanos = System.nanoTime();

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()))))
                .andExpect(status().isCreated());

        // The mentor receives REQUEST_RECEIVED and the mentee receives the new
        // REQUEST_SUBMITTED confirmation.
        verify(pushDeliveryService, timeout(5_000).atLeastOnce())
                .send(eq(mentor.getId()), eq(NotificationType.REQUEST_RECEIVED), anyString(), anyString());
        verify(pushDeliveryService, timeout(5_000).atLeastOnce())
                .send(eq(mentee.getId()), eq(NotificationType.REQUEST_SUBMITTED), anyString(), anyString());

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertThat(elapsedMillis)
                .as("Push delivery must complete within the 5-second SLA (req 2.3.1)")
                .isLessThan(5_000);

        // The in-app rows are still persisted (in-app is the source of truth).
        assertThat(waitForNotification(mentor.getId(), NotificationType.REQUEST_RECEIVED)).isNotNull();
        assertThat(waitForNotification(mentee.getId(), NotificationType.REQUEST_SUBMITTED)).isNotNull();
    }

    // ── Type-agnostic transport ──────────────────────────────────────────────

    @Test
    void transportFiresForArbitraryEnumValue() throws Exception {
        registerAndLogin("push_mentee5@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("push_mentee5@test.com"))
                .findFirst().orElseThrow();

        notificationEventPublisher.publishMatchFound(mentee.getId(), "Carol");

        verify(pushDeliveryService, timeout(5_000).atLeastOnce())
                .send(eq(mentee.getId()), eq(NotificationType.MATCH_FOUND), anyString(), anyString());
    }
}
