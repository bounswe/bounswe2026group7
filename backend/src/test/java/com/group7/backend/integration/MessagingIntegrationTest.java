package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.*;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        messageRepository.deleteAll();
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
        notificationRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void mentorAndMenteeExchangeMessagesAndReadReceipts() throws Exception {
        Fixture fix = setupActiveMentorship("msg_mentor1@test.com", "msg_mentee1@test.com");

        // Mentor sends two messages
        sendMessage(fix.mentorToken, fix.mentorshipId, "Hello mentee");
        sendMessage(fix.mentorToken, fix.mentorshipId, "Are you free Friday?");
        // Mentee sends one
        sendMessage(fix.menteeToken, fix.mentorshipId, "Yes I am");

        // History as mentee — newest first, three messages
        mockMvc.perform(get("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + fix.menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].content").value("Yes I am"))
                .andExpect(jsonPath("$.content[1].content").value("Are you free Friday?"))
                .andExpect(jsonPath("$.content[2].content").value("Hello mentee"));

        // Mentee marks all read
        mockMvc.perform(patch("/api/mentorships/" + fix.mentorshipId + "/messages/read")
                        .header("Authorization", "Bearer " + fix.menteeToken))
                .andExpect(status().isNoContent());

        // The two mentor-sent messages should now have non-null readAt; the mentee's own
        // message stays unread (we never mark our own outgoing messages read here).
        MvcResult listResult = mockMvc.perform(
                        get("/api/mentorships/" + fix.mentorshipId + "/messages")
                                .header("Authorization", "Bearer " + fix.menteeToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("content");
        for (JsonNode msg : content) {
            boolean fromMentor = msg.get("senderId").asLong() == fix.mentorId;
            if (fromMentor) {
                assertThat(msg.get("readAt").isNull()).isFalse();
            } else {
                assertThat(msg.get("readAt").isNull()).isTrue();
            }
        }
    }

    @Test
    void nonParticipantCannotSendOrRead() throws Exception {
        Fixture fix = setupActiveMentorship("msg_m_a@test.com", "msg_e_a@test.com");
        String outsiderToken = registerAndLogin("msg_outsider@test.com", true);

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("intruder");

        mockMvc.perform(post("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendingTriggersNewMessageNotificationForRecipient() throws Exception {
        Fixture fix = setupActiveMentorship("msg_m_b@test.com", "msg_e_b@test.com");

        sendMessage(fix.mentorToken, fix.mentorshipId, "Notify me");

        Notification newMessage = waitForNotification(fix.menteeId, NotificationType.NEW_MESSAGE);
        assertThat(newMessage).isNotNull();
        assertThat(newMessage.getBody()).contains(fix.mentorFirstName);
    }

    @Test
    void blankContentFailsValidation() throws Exception {
        Fixture fix = setupActiveMentorship("msg_m_c@test.com", "msg_e_c@test.com");

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("   ");
        mockMvc.perform(post("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + fix.mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAttachmentReturnsUrlReachableFromSendMessage() throws Exception {
        Fixture fix = setupActiveMentorship("msg_m_d@test.com", "msg_e_d@test.com");

        byte[] pdf = pdfBody("payload");
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", pdf);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + fix.mentorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andReturn();
        String attachmentUrl = objectMapper
                .readTree(uploadResult.getResponse().getContentAsString())
                .get("url").asText();
        assertThat(attachmentUrl).contains("/api/uploads/attachments/");

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("here is the doc");
        body.setAttachmentUrl(attachmentUrl);
        mockMvc.perform(post("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + fix.mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentUrl").value(attachmentUrl));
    }

    @Test
    void rejectsAttachmentWithBadMagicBytes() throws Exception {
        registerAndLogin("msg_uploader@test.com", true);
        String token = loginToken("msg_uploader@test.com");

        MockMultipartFile bogus = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not a real pdf".getBytes());

        mockMvc.perform(multipart("/api/messages/attachments")
                        .file(bogus)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Fixture(
            String mentorToken,
            String menteeToken,
            Long mentorId,
            Long menteeId,
            Long mentorshipId,
            String mentorFirstName) {
    }

    private Fixture setupActiveMentorship(String mentorEmail, String menteeEmail) throws Exception {
        String mentorToken = registerAndLogin(mentorEmail, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(mentorEmail))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentor.setFirstName("Mira");
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin(menteeEmail, false);
        Long menteeId = userRepository.findByEmail(menteeEmail).orElseThrow().getId();

        // Mentee creates a request, mentor accepts → ACTIVE mentorship
        String createBody = objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()));
        MvcResult requestResult = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk())
                .andReturn();
        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();

        return new Fixture(mentorToken, menteeToken, mentor.getId(), menteeId, mentorshipId,
                mentor.getFirstName());
    }

    private void sendMessage(String token, Long mentorshipId, String content) throws Exception {
        SendMessageRequest body = new SendMessageRequest();
        body.setContent(content);
        mockMvc.perform(post("/api/mentorships/" + mentorshipId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content));
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(isMentor ? "Mira" : "Eli");
        req.setLastName(isMentor ? "Mentor" : "Mentee");
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

        return loginToken(email);
    }

    private String loginToken(String email) throws Exception {
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

    private static byte[] pdfBody(String trailing) {
        byte[] header = new byte[]{0x25, 0x50, 0x44, 0x46};  // %PDF
        byte[] tail = trailing.getBytes();
        byte[] result = new byte[header.length + tail.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(tail, 0, result, header.length, tail.length);
        return result;
    }
}
