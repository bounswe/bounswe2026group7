package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.BotSignal;
import com.group7.backend.entity.User;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.BotSignalRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spam-bot defence end-to-end coverage (#345). Boots a full Spring context
 * with the spam layer flipped ON (the default test profile keeps it OFF
 * for everything else) and exercises each signal type plus the admin
 * clear-bot-flag escape hatch.
 *
 * <p>The auto-ban-on-threshold path is exercised by accumulating rejected
 * attempts from the same IP before a successful registration; the
 * threshold is lowered to 2 via {@code @SpringBootTest(properties=...)} so
 * the scenario runs without seeding 3+ pre-attempts.
 */
@SpringBootTest(properties = {
        "app.spam.enabled=true",
        "app.spam.min-submit-seconds=1.5",
        "app.spam.auto-ban.signal-threshold=2",
        "app.spam.auto-ban.window=PT1H",
        "app.spam.auto-ban.duration-hours=24",
        "app.spam.form-token-secret=integration-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpamBotDetectionIntegrationTest {

    private static final String ADMIN_EMAIL = "admin345@test.local";
    private static final String ADMIN_PASSWORD = "AdminInfra345Pwd";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private BanRepository banRepository;
    @Autowired private BotSignalRepository botSignalRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        botSignalRepository.deleteAll();
        banRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    void honeypotPopulated_returnsGeneric400_andRecordsSignal_andNoUserCreated() throws Exception {
        String token = issueFormToken();
        // Sleep past min-submit-seconds is not needed here — honeypot fires first.

        RegisterRequest req = baseRequest("bot1@test.com");
        req.setFormToken(token);
        req.setWebsite("http://spam.example");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request rejected"));

        assertThat(userRepository.findByEmail("bot1@test.com")).isEmpty();
        assertThat(botSignalRepository.findAll())
                .singleElement()
                .extracting(BotSignal::getSignalType)
                .isEqualTo(BotSignal.SignalType.HONEYPOT);
    }

    @Test
    void timingTooFast_returnsGeneric400_andRecordsSignal() throws Exception {
        String token = issueFormToken();
        // Submit immediately — well below 1.5s.

        RegisterRequest req = baseRequest("fastbot@test.com");
        req.setFormToken(token);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail("fastbot@test.com")).isEmpty();
        assertThat(botSignalRepository.findAll())
                .singleElement()
                .extracting(BotSignal::getSignalType)
                .isEqualTo(BotSignal.SignalType.TIMING_TOO_FAST);
    }

    @Test
    void formTokenMissing_returnsGeneric400() throws Exception {
        RegisterRequest req = baseRequest("notoken@test.com");
        // No formToken set — invalid path.

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        assertThat(botSignalRepository.findAll())
                .singleElement()
                .extracting(BotSignal::getSignalType)
                .isEqualTo(BotSignal.SignalType.FORM_TOKEN_INVALID);
    }

    @Test
    void humanFlow_succeeds_andNoFlag() throws Exception {
        String token = issueFormToken();
        Thread.sleep(1700L); // clear the 1.5s gate

        RegisterRequest req = baseRequest("human@test.com");
        req.setFormToken(token);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("human@test.com").orElseThrow();
        assertThat(saved.getIsSuspectedBot()).isFalse();
        assertThat(banRepository.findByUser_IdOrderByCreatedAtDesc(saved.getId())).isEmpty();
    }

    @Test
    void accumulatedSignalsBeforeRegister_triggerAutoBanAfterRegister() throws Exception {
        // Two rejected attempts (signal-threshold=2 in test props).
        for (int i = 0; i < 2; i++) {
            String t = issueFormToken();
            RegisterRequest reject = baseRequest("attacker" + i + "@test.com");
            reject.setFormToken(t);
            reject.setWebsite("trap"); // honeypot, instant reject
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reject)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(botSignalRepository.count()).isEqualTo(2);

        // Third attempt — clean payload, valid timing — succeeds but is flagged.
        String okToken = issueFormToken();
        Thread.sleep(1700L);
        RegisterRequest ok = baseRequest("survivor@test.com");
        ok.setFormToken(okToken);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ok)))
                .andExpect(status().isCreated());

        User flagged = userRepository.findByEmail("survivor@test.com").orElseThrow();
        assertThat(flagged.getIsSuspectedBot()).isTrue();
        assertThat(flagged.getSuspectedAt()).isNotNull();
        assertThat(banRepository.findByUser_IdOrderByCreatedAtDesc(flagged.getId()))
                .isNotEmpty()
                .first()
                .extracting(b -> b.getReason())
                .isEqualTo("automated abuse signal");

        // Verify the post-commit IP_BURST signal was also recorded.
        assertThat(botSignalRepository.findAll())
                .extracting(BotSignal::getSignalType)
                .contains(BotSignal.SignalType.IP_BURST);
    }

    @Test
    void admin_clearBotFlag_clearsFlagAndLiftsAutoBan() throws Exception {
        // Trigger auto-ban via the two-attempt path.
        for (int i = 0; i < 2; i++) {
            String t = issueFormToken();
            RegisterRequest reject = baseRequest("z" + i + "@test.com");
            reject.setFormToken(t);
            reject.setWebsite("trap");
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reject)))
                    .andExpect(status().isBadRequest());
        }
        String okToken = issueFormToken();
        Thread.sleep(1700L);
        RegisterRequest ok = baseRequest("flagged@test.com");
        ok.setFormToken(okToken);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ok)))
                .andExpect(status().isCreated());
        User flagged = userRepository.findByEmail("flagged@test.com").orElseThrow();
        assertThat(flagged.getIsSuspectedBot()).isTrue();

        // Admin clears the flag.
        seedAdmin();
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/users/" + flagged.getId() + "/clear-bot-flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        User cleared = userRepository.findByEmail("flagged@test.com").orElseThrow();
        assertThat(cleared.getIsSuspectedBot()).isFalse();
        assertThat(cleared.getSuspectedAt()).isNull();
        // Active ban lifted (liftedAt set).
        assertThat(banRepository.findByUser_IdOrderByCreatedAtDesc(flagged.getId()))
                .first()
                .extracting(b -> b.getLiftedAt())
                .isNotNull();
    }

    @Test
    void clearBotFlag_isIdempotent_whenNoActiveBan() throws Exception {
        // Seed a user with the flag manually (no ban row).
        seedAdmin();
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String okToken = issueFormToken();
        Thread.sleep(1700L);
        RegisterRequest req = baseRequest("manual@test.com");
        req.setFormToken(okToken);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail("manual@test.com").orElseThrow();
        user.setIsSuspectedBot(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/clear-bot-flag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getIsSuspectedBot())
                .isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String issueFormToken() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/auth/form-token"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("token").asText();
    }

    private RegisterRequest baseRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Doe");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(false);
        return req;
    }

    private void seedAdmin() {
        Admin admin = new Admin();
        admin.setFirstName("Bootstrap");
        admin.setLastName("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setIsEmailVerified(true);
        userRepository.save(admin);
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }
}
