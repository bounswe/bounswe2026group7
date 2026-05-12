package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #280 — Admin Role Infrastructure: end-to-end coverage for the admin
 * ban/unban surface and admin-initiated messaging (direct DMs + broadcasts).
 *
 * <p>The auto-ban path is already covered by {@link BanIntegrationTest}; this
 * suite focuses exclusively on the admin-initiated additions and the login
 * gate that rejects banned users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBanAndMessagingIntegrationTest {

    private static final String ADMIN_EMAIL = "admin280@test.local";
    private static final String ADMIN_PASSWORD = "AdminInfra280Pwd";
    private static final String ADMIN2_EMAIL = "admin280-second@test.local";
    private static final String ADMIN2_PASSWORD = "AdminInfra280Pwd";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private BanRepository banRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationParticipantRepository participantRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        messageRepository.deleteAll();
        // Conversation_participants cascades on conversation delete; deleting
        // conversations first keeps the cleanup idempotent across reruns.
        conversationRepository.deleteAll();
        banRepository.deleteAll();
        notificationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    // ── Ban / unban flow ──────────────────────────────────────────────────

    @Test
    void admin_canBanUser_andBannedUserGetsBannedUntilOnLogin() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("ban_target@test.com", false);
        Long targetId = userRepository.findByEmail("ban_target@test.com").orElseThrow().getId();

        // Pre-ban login works.
        login("ban_target@test.com", "Password1", "MENTEE");

        // Admin imposes a 24h ban.
        mockMvc.perform(post("/api/admin/users/" + targetId + "/ban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", "Spam reports", "durationHours", 24))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetId))
                .andExpect(jsonPath("$.reason").value("Spam reports"));

        // Now login is blocked with the BANNED_UNTIL code + ban metadata.
        LoginRequest login = new LoginRequest();
        login.setEmail("ban_target@test.com");
        login.setPassword("Password1");
        MvcResult forbidden = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BANNED_UNTIL"))
                .andExpect(jsonPath("$.reason").value("Spam reports"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.banCount").value(1))
                .andReturn();

        // Body shape sanity: the banCount is the next ordinal for the user.
        JsonNode body = objectMapper.readTree(forbidden.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
    }

    @Test
    void admin_unbanUser_restoresLogin() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("unban_target@test.com", true);
        Long targetId = userRepository.findByEmail("unban_target@test.com").orElseThrow().getId();

        // Ban then unban.
        mockMvc.perform(post("/api/admin/users/" + targetId + "/ban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", "Test", "durationHours", 48))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/users/" + targetId + "/unban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liftedAt").exists());

        // Login works again.
        login("unban_target@test.com", "Password1", "MENTOR");
    }

    @Test
    void unban_returns404_whenNoActiveBan() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("not_banned@test.com", false);
        Long targetId = userRepository.findByEmail("not_banned@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/" + targetId + "/unban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdmin_cannotCallBanOrUnbanEndpoints() throws Exception {
        registerAndVerify("nonadmin@test.com", false);
        String menteeToken = login("nonadmin@test.com", "Password1", "MENTEE");

        mockMvc.perform(post("/api/admin/users/1/ban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", "no", "durationHours", 1))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/1/unban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void banRequest_rejectsNonPositiveDuration() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("validation_target@test.com", false);
        Long targetId = userRepository.findByEmail("validation_target@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/" + targetId + "/ban")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reason", "x", "durationHours", 0))))
                .andExpect(status().isBadRequest());
    }

    // ── Admin direct messaging ───────────────────────────────────────────

    @Test
    void admin_canDmAnyUser_withoutMentorship() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("dm_target@test.com", false);
        Long targetId = userRepository.findByEmail("dm_target@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/messages/direct/" + targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Hello from admin"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello from admin"));

        // Conversation row was created with kind=ADMIN_DIRECT.
        List<Conversation> directs = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.ADMIN_DIRECT)
                .toList();
        assertThat(directs).hasSize(1);
        Conversation direct = directs.get(0);
        assertThat(direct.getMentorship()).isNull();
        assertThat(direct.getPairAId()).isNotNull();
        assertThat(direct.getPairBId()).isNotNull();
        assertThat(direct.getPairAId()).isLessThan(direct.getPairBId());

        // Both admin and target are participants — bypassing the mentorship gate.
        Long adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        assertThat(participantRepository.existsByConversationIdAndUserId(direct.getId(), adminId)).isTrue();
        assertThat(participantRepository.existsByConversationIdAndUserId(direct.getId(), targetId)).isTrue();
    }

    @Test
    void adminDirect_isIdempotentAcrossSends() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("dm_repeat@test.com", true);
        Long targetId = userRepository.findByEmail("dm_repeat@test.com").orElseThrow().getId();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/messages/direct/" + targetId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("content", "msg " + i))))
                    .andExpect(status().isCreated());
        }

        long directConvCount = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.ADMIN_DIRECT).count();
        assertThat(directConvCount).isEqualTo(1L);
    }

    @Test
    void adminDirect_recipientSeesInbox_canReadThread_andReply() throws Exception {
        seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        registerAndVerify("dm_inbox_target@test.com", false);
        String userToken = login("dm_inbox_target@test.com", "Password1", "MENTEE");
        Long targetId = userRepository.findByEmail("dm_inbox_target@test.com").orElseThrow().getId();
        Long adminId = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(post("/api/admin/messages/direct/" + targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Hello from admin"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/conversations/admin-direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].peerId").value(adminId))
                .andExpect(jsonPath("$.content[0].peerFirstName").value("Bootstrap"))
                .andExpect(jsonPath("$.content[0].lastMessageContent").value("Hello from admin"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));

        mockMvc.perform(get("/api/conversations/admin-direct/" + adminId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Hello from admin"));

        mockMvc.perform(patch("/api/conversations/admin-direct/" + adminId + "/messages/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/conversations/admin-direct/" + adminId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Received, thanks"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Received, thanks"));
    }

    @Test
    void adminDirect_selfDmReturns400() throws Exception {
        Admin admin = seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(post("/api/admin/messages/direct/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "self"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdmin_cannotCallDirectMessageEndpoint() throws Exception {
        registerAndVerify("dm_user@test.com", false);
        registerAndVerify("dm_other@test.com", true);
        String userToken = login("dm_user@test.com", "Password1", "MENTEE");
        Long otherId = userRepository.findByEmail("dm_other@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/messages/direct/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "no"))))
                .andExpect(status().isForbidden());
    }

    // ── Admin broadcast ──────────────────────────────────────────────────

    @Test
    void admin_broadcast_createsSingleton_andIncludesAllAdmins() throws Exception {
        Admin a1 = seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        Admin a2 = seedAdmin(ADMIN2_EMAIL, ADMIN2_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "All-hands"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("All-hands"));

        List<Conversation> broadcasts = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.ADMIN_BROADCAST)
                .toList();
        assertThat(broadcasts).hasSize(1);
        Conversation broadcast = broadcasts.get(0);
        assertThat(broadcast.getMentorship()).isNull();
        assertThat(broadcast.getPairAId()).isNull();
        assertThat(broadcast.getPairBId()).isNull();

        // Every admin is a participant — both the sender and the second admin
        // who has not yet posted, so #2 sees future broadcasts in their inbox.
        assertThat(participantRepository.existsByConversationIdAndUserId(broadcast.getId(), a1.getId())).isTrue();
        assertThat(participantRepository.existsByConversationIdAndUserId(broadcast.getId(), a2.getId())).isTrue();

        // Second broadcast still resolves to the same singleton row.
        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Round 2"))))
                .andExpect(status().isCreated());
        long broadcastCount = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.ADMIN_BROADCAST).count();
        assertThat(broadcastCount).isEqualTo(1L);
    }

    @Test
    void broadcast_promotedAdminPicksUpFutureBroadcasts() throws Exception {
        // First admin sends one broadcast; second admin is added later and a
        // third broadcast then re-syncs the participant list to include them.
        Admin a1 = seedAdmin(ADMIN_EMAIL, ADMIN_PASSWORD);
        String firstAdminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "first"))))
                .andExpect(status().isCreated());
        Conversation broadcast = conversationRepository.findAll().stream()
                .filter(c -> c.getKind() == ConversationKind.ADMIN_BROADCAST)
                .findFirst().orElseThrow();
        assertThat(participantRepository.existsByConversationIdAndUserId(broadcast.getId(), a1.getId())).isTrue();

        // Second admin joins after the first broadcast.
        Admin a2 = seedAdmin(ADMIN2_EMAIL, ADMIN2_PASSWORD);
        assertThat(participantRepository.existsByConversationIdAndUserId(broadcast.getId(), a2.getId()))
                .as("newly-promoted admin is not retroactively a participant until next broadcast")
                .isFalse();

        // Next broadcast re-syncs the admin set.
        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "second"))))
                .andExpect(status().isCreated());
        assertThat(participantRepository.existsByConversationIdAndUserId(broadcast.getId(), a2.getId())).isTrue();
    }

    @Test
    void nonAdmin_cannotBroadcast() throws Exception {
        registerAndVerify("broadcaster@test.com", true);
        String mentorToken = login("broadcaster@test.com", "Password1", "MENTOR");

        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "no"))))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Admin seedAdmin(String email, String password) {
        Admin admin = new Admin();
        admin.setFirstName("Bootstrap");
        admin.setLastName("Admin");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setIsEmailVerified(true);
        return userRepository.save(admin);
    }

    private void registerAndVerify(String email, boolean isMentor) throws Exception {
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
}
