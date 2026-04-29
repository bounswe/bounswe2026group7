package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.*;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.NotificationEventPublisher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @MockitoBean private EmailService emailService;
        @Autowired private NotificationEventPublisher notificationEventPublisher;

    @BeforeEach
    void cleanDb() {
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
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

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionToken").asText();
    }

    private Long createMentorshipRequest(String menteeToken, Long mentorId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("mentorId", mentorId));
        MvcResult result = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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

    @Test
    void acceptRequestCreatesNotificationAndReadEndpointsWork() throws Exception {
        String mentorToken = registerAndLogin("notif_mentor1@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("notif_mentor1@test.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("notif_mentee1@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("notif_mentee1@test.com"))
                .findFirst().orElseThrow();

        Long requestId = createMentorshipRequest(menteeToken, mentor.getId());

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk());

        Notification created = waitForNotification(mentee.getId(), NotificationType.REQUEST_ACCEPTED);
        assertThat(created).isNotNull();

        MvcResult listResult = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + menteeToken)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REQUEST_ACCEPTED"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andReturn();

        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        Long notificationId = listJson.get(0).get("id").asLong();

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + menteeToken)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void matchingEndpointDoesNotCreateMatchFoundNotification() throws Exception {
        registerAndLogin("notif_mentor2@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("notif_mentor2@test.com"))
                .findFirst().orElseThrow();
        mentor.setFirstName("Ayse");
        mentor.setMaxMenteeCapacity(3);
        mentor.setCurrentMenteeCount(0);
        mentor.setField("Computer Science");
        mentor.setExpertise("java backend systems");
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("notif_mentee2@test.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("notif_mentee2@test.com"))
                .findFirst().orElseThrow();
        mentee.setMajor("Computer Science");
        mentee.setGoals("learn backend development");
        menteeRepository.save(mentee);

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("Ayse"));

        // Wait a short amount of time to ensure no async notification is published
        Thread.sleep(500);

        List<Notification> notifications = notificationRepository.findForUser(mentee.getId(), false);
        boolean hasMatchFound = notifications.stream().anyMatch(n -> n.getType() == NotificationType.MATCH_FOUND);
        assertThat(hasMatchFound).isFalse();
    }

        @Test
        void publisherCreatesNewMessageAndMeetingReminderNotifications() throws Exception {
                String userToken = registerAndLogin("notif_user3@test.com", false);
                Long userId = userRepository.findByEmail("notif_user3@test.com").orElseThrow().getId();

                notificationEventPublisher.publishNewMessage(userId, "Ayse");
                notificationEventPublisher.publishMeetingReminder(userId, "Your meeting starts in 30 minutes.");

                Notification messageNotification = waitForNotification(userId, NotificationType.NEW_MESSAGE);
                Notification meetingNotification = waitForNotification(userId, NotificationType.MEETING_REMINDER);

                assertThat(messageNotification).isNotNull();
                assertThat(meetingNotification).isNotNull();

                mockMvc.perform(get("/api/notifications")
                                                .header("Authorization", "Bearer " + userToken)
                                                .param("unreadOnly", "false"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.type=='NEW_MESSAGE')]").isNotEmpty())
                                .andExpect(jsonPath("$[?(@.type=='MEETING_REMINDER')]").isNotEmpty());
        }
}