package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for mentor-to-mentor messaging (#284). Mirrors the
 * helpers and shape of {@link MessagingIntegrationTest} (registerAndLogin,
 * waitForNotification) so anyone tracing the messaging package finds one
 * consistent pattern.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorPairMessagingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ConversationParticipantRepository conversationParticipantRepository;
    @Autowired private ConversationRepository conversationRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        messageRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        notificationRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void twoMentors_exchangeMessages_onMentorPairEndpoint() throws Exception {
        Mentors m = registerTwoMentors("pair_a@test.com", "pair_b@test.com");

        sendPair(m.tokenA, m.idB, "hello peer");
        sendPair(m.tokenB, m.idA, "hi back");

        // Both mentors see the same two messages on their respective endpoints
        MvcResult listForA = mockMvc.perform(get("/api/conversations/mentor-pair/" + m.idB + "/messages")
                        .header("Authorization", "Bearer " + m.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();
        Long convoId = objectMapper.readTree(listForA.getResponse().getContentAsString())
                .get("content").get(0).get("conversationId").asLong();

        mockMvc.perform(get("/api/conversations/mentor-pair/" + m.idA + "/messages")
                        .header("Authorization", "Bearer " + m.tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].conversationId").value(convoId));

        // The conversation row carries the canonical pair shape and no mentorship
        Conversation persisted = conversationRepository.findById(convoId).orElseThrow();
        assertThat(persisted.getKind()).isEqualTo(ConversationKind.MENTOR_PAIR);
        assertThat(persisted.getMentorship()).isNull();
        long lower = Math.min(m.idA, m.idB);
        long higher = Math.max(m.idA, m.idB);
        assertThat(persisted.getPairAId()).isEqualTo(lower);
        assertThat(persisted.getPairBId()).isEqualTo(higher);

        // Exactly one conversation row exists for the pair (no duplicates from
        // both mentors initiating)
        long pairRowCount = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.MENTOR_PAIR)
                .count();
        assertThat(pairRowCount).isEqualTo(1L);
    }

    @Test
    void firstMessageFromA_producesNewMessageNotificationForB() throws Exception {
        Mentors m = registerTwoMentors("pair_notify_a@test.com", "pair_notify_b@test.com");

        sendPair(m.tokenA, m.idB, "you should see a notification");

        Notification n = waitForNotification(m.idB, NotificationType.NEW_MESSAGE);
        assertThat(n).as("recipient should receive a NEW_MESSAGE notification").isNotNull();
    }

    @Test
    void historyReadsResolveSameConversation_inBothDirections() throws Exception {
        Mentors m = registerTwoMentors("pair_dir_a@test.com", "pair_dir_b@test.com");

        sendPair(m.tokenA, m.idB, "from A");
        sendPair(m.tokenB, m.idA, "from B");

        MvcResult fromA = mockMvc.perform(get("/api/conversations/mentor-pair/" + m.idB + "/messages")
                        .header("Authorization", "Bearer " + m.tokenA))
                .andReturn();
        MvcResult fromB = mockMvc.perform(get("/api/conversations/mentor-pair/" + m.idA + "/messages")
                        .header("Authorization", "Bearer " + m.tokenB))
                .andReturn();

        Long cidFromA = objectMapper.readTree(fromA.getResponse().getContentAsString())
                .get("content").get(0).get("conversationId").asLong();
        Long cidFromB = objectMapper.readTree(fromB.getResponse().getContentAsString())
                .get("content").get(0).get("conversationId").asLong();

        assertThat(cidFromA).isEqualTo(cidFromB);
    }

    @Test
    void pairConversation_doesNotRequireActiveMentorship() throws Exception {
        // The two mentors share no mentorship (we never created one) — the
        // assertSendable branch for MENTORSHIP is bypassed because kind is
        // MENTOR_PAIR. Sending must succeed.
        Mentors m = registerTwoMentors("pair_nomship_a@test.com", "pair_nomship_b@test.com");

        sendPair(m.tokenA, m.idB, "no mentorship between us");

        assertThat(messageRepository.count()).isEqualTo(1L);
    }

    @Test
    void markAllRead_byB_setsReadAtOnAsMessage() throws Exception {
        Mentors m = registerTwoMentors("pair_read_a@test.com", "pair_read_b@test.com");
        sendPair(m.tokenA, m.idB, "please read me");

        mockMvc.perform(patch("/api/conversations/mentor-pair/" + m.idA + "/messages/read")
                        .header("Authorization", "Bearer " + m.tokenB))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/api/conversations/mentor-pair/" + m.idA + "/messages")
                        .header("Authorization", "Bearer " + m.tokenB))
                .andReturn();
        JsonNode first = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content").get(0);
        assertThat(first.get("readAt").isNull()).isFalse();
    }

    @Test
    void mentee_postingToMentorPairEndpoint_returns403() throws Exception {
        Mentors m = registerTwoMentors("pair_mentee_a@test.com", "pair_mentee_b@test.com");
        String menteeToken = registerAndLogin("pair_mentee_caller@test.com", false);

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("I shouldn't be here");
        mockMvc.perform(post("/api/conversations/mentor-pair/" + m.idA + "/messages")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mentor_targetingMentee_returns400() throws Exception {
        String mentorToken = registerAndLogin("pair_target_mentor@test.com", true);
        String menteeEmail = "pair_target_mentee@test.com";
        registerAndLogin(menteeEmail, false);
        Long menteeId = userRepository.findByEmail(menteeEmail).orElseThrow().getId();

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("hi");
        mockMvc.perform(post("/api/conversations/mentor-pair/" + menteeId + "/messages")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentor_targetingSelf_returns400() throws Exception {
        String mentorToken = registerAndLogin("pair_self@test.com", true);
        Long mentorId = userRepository.findByEmail("pair_self@test.com").orElseThrow().getId();

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("hi me");
        mockMvc.perform(post("/api/conversations/mentor-pair/" + mentorId + "/messages")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void threeMentors_aBandACpairs_areDistinctConversations() throws Exception {
        // Three mentors A, B, C — A↔B and A↔C must produce two separate
        // conversation rows.
        String tokenA = registerAndLogin("pair_3a@test.com", true);
        registerAndLogin("pair_3b@test.com", true);
        registerAndLogin("pair_3c@test.com", true);
        Long idB = userRepository.findByEmail("pair_3b@test.com").orElseThrow().getId();
        Long idC = userRepository.findByEmail("pair_3c@test.com").orElseThrow().getId();

        sendPair(tokenA, idB, "hi B");
        sendPair(tokenA, idC, "hi C");

        long pairRowCount = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.MENTOR_PAIR)
                .count();
        assertThat(pairRowCount).isEqualTo(2L);
    }

    @Test
    void mentorPair_doesNotCollideWithExistingMentorshipConversation() throws Exception {
        // The unique index on (pair_a_id, pair_b_id, kind) includes `kind` so
        // a future scenario where two users share both a mentorship and a
        // peer conversation will not collide. Here we just verify the
        // mentor-pair row is created independently of the mentorship table.
        Mentors m = registerTwoMentors("pair_coexist_a@test.com", "pair_coexist_b@test.com");

        sendPair(m.tokenA, m.idB, "peer message");

        // No mentorship was created, so no MENTORSHIP conversations exist.
        long mentorshipRows = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.MENTORSHIP)
                .count();
        assertThat(mentorshipRows).isZero();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private record Mentors(String tokenA, String tokenB, Long idA, Long idB) {}

    private Mentors registerTwoMentors(String emailA, String emailB) throws Exception {
        String tokenA = registerAndLogin(emailA, true);
        String tokenB = registerAndLogin(emailB, true);
        Long idA = userRepository.findByEmail(emailA).orElseThrow().getId();
        Long idB = userRepository.findByEmail(emailB).orElseThrow().getId();
        return new Mentors(tokenA, tokenB, idA, idB);
    }

    private void sendPair(String token, Long otherId, String content) throws Exception {
        SendMessageRequest body = new SendMessageRequest();
        body.setContent(content);
        mockMvc.perform(post("/api/conversations/mentor-pair/" + otherId + "/messages")
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

        User user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
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
}
