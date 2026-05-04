package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.SendMessageRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the mentor-pair inbox listing (#323) against real
 * Postgres. Mirrors the helpers and shape of
 * {@link MentorPairMessagingIntegrationTest} (registerAndLogin,
 * registerTwoMentors, sendPair) so the messaging package keeps a consistent
 * test pattern.
 *
 * <p>Each scenario is self-contained: {@code @BeforeEach cleanDb()} wipes
 * the relevant repositories, then the scenario seeds users and conversations
 * fresh. The codebase uses explicit {@code deleteAll()} cleanup rather than
 * {@code @Transactional} rollback because the messaging stack publishes
 * events via {@code @TransactionalEventListener(AFTER_COMMIT)}, which never
 * fires inside a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorPairInboxIntegrationTest {

    private static final String INBOX_URL = "/api/conversations/mentor-pair";

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

    // ── 15. Empty inbox: no pair conversations → empty page ────────────────

    @Test
    void mentorWithNoPairConversations_seesEmptyInbox() throws Exception {
        String token = registerAndLogin("inbox_empty@test.com", true);

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── 16. A sends to B, A views own inbox: unread=0 (self-sent filter) ───

    @Test
    void senderViewsOwnInbox_unreadIsZero() throws Exception {
        Mentors m = registerTwoMentors("inbox_send_a@test.com", "inbox_send_b@test.com");
        sendPair(m.tokenA, m.idB, "see you Tuesday");

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + m.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].peerId").value(m.idB))
                .andExpect(jsonPath("$.content[0].peerFirstName").value("Mira"))
                .andExpect(jsonPath("$.content[0].lastMessageContent").value("see you Tuesday"))
                .andExpect(jsonPath("$.content[0].lastMessageSentAt").exists())
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));
    }

    // ── 17. Receiver sees non-zero unread (the always-zero-bug guard) ──────

    @Test
    void receiverViewsInboxBeforeReading_unreadMatchesMessageCount() throws Exception {
        Mentors m = registerTwoMentors("inbox_unread_a@test.com", "inbox_unread_b@test.com");
        sendPair(m.tokenA, m.idB, "one");
        sendPair(m.tokenA, m.idB, "two");
        sendPair(m.tokenA, m.idB, "three");

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + m.tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].peerId").value(m.idA))
                .andExpect(jsonPath("$.content[0].unreadCount").value(3));
    }

    // ── 18. Mark-read drops unread to 0 ────────────────────────────────────

    @Test
    void markReadByReceiver_unreadDropsToZero() throws Exception {
        Mentors m = registerTwoMentors("inbox_read_a@test.com", "inbox_read_b@test.com");
        sendPair(m.tokenA, m.idB, "one");
        sendPair(m.tokenA, m.idB, "two");

        // Existing #284 endpoint: PATCH /api/conversations/mentor-pair/{otherMentorId}/messages/read.
        mockMvc.perform(patch("/api/conversations/mentor-pair/" + m.idA + "/messages/read")
                        .header("Authorization", "Bearer " + m.tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + m.tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));
    }

    // ── 19. Recency ordering across multiple peers ─────────────────────────

    @Test
    void multiplePeers_orderedByMostRecentActivity() throws Exception {
        // Three mentors A, B, C. A↔B exchange first, then A↔C exchange — A's
        // inbox should list [C-pair, B-pair]. The c.id DESC tiebreaker in the
        // ORDER BY makes this deterministic even if both messages land in the
        // same millisecond, so no Thread.sleep needed.
        String tokenA = registerAndLogin("inbox_order_a@test.com", true);
        registerAndLogin("inbox_order_b@test.com", true);
        registerAndLogin("inbox_order_c@test.com", true);
        Long idB = userRepository.findByEmail("inbox_order_b@test.com").orElseThrow().getId();
        Long idC = userRepository.findByEmail("inbox_order_c@test.com").orElseThrow().getId();

        sendPair(tokenA, idB, "earlier message to B");
        sendPair(tokenA, idC, "later message to C");

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].peerId").value(idC))
                .andExpect(jsonPath("$.content[0].lastMessageContent").value("later message to C"))
                .andExpect(jsonPath("$.content[1].peerId").value(idB))
                .andExpect(jsonPath("$.content[1].lastMessageContent").value("earlier message to B"));
    }

    // ── 20. Pagination across 5 pair conversations ─────────────────────────

    @Test
    void fivePairConversations_paginateCleanly() throws Exception {
        String tokenA = registerAndLogin("inbox_page_a@test.com", true);
        for (int i = 1; i <= 5; i++) {
            String peerEmail = "inbox_page_p" + i + "@test.com";
            registerAndLogin(peerEmail, true);
            Long peerId = userRepository.findByEmail(peerEmail).orElseThrow().getId();
            sendPair(tokenA, peerId, "msg-" + i);
        }

        long totalSeen = 0;
        totalSeen += assertPage(tokenA, 0, 2, 2);
        totalSeen += assertPage(tokenA, 1, 2, 2);
        totalSeen += assertPage(tokenA, 2, 2, 1);
        assertThat(totalSeen).isEqualTo(5L);

        // Past-end page: client asks for page 5 (well beyond totalPages=3).
        // Empty content but the response must still report the actual total
        // so the client can detect they overshot, rather than mistaking it
        // for an empty inbox. Caught by manual testing — the early
        // implementation collapsed to totalElements=0 here.
        mockMvc.perform(get(INBOX_URL).param("page", "5").param("size", "2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    // ── 21. Mentee role denied ─────────────────────────────────────────────

    @Test
    void menteeCallingInbox_returns403() throws Exception {
        String menteeToken = registerAndLogin("inbox_mentee@test.com", false);

        mockMvc.perform(get(INBOX_URL)
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers (copied verbatim from MentorPairMessagingIntegrationTest) ──

    private record Mentors(String tokenA, String tokenB, Long idA, Long idB) {
    }

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

    private long assertPage(String token, int page, int size, int expectedContentSize) throws Exception {
        MvcResult result = mockMvc.perform(get(INBOX_URL)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(expectedContentSize))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("content").size();
    }
}
