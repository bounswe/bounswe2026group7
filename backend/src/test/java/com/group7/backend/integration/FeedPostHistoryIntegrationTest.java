package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * End-to-end coverage for the feed-post edit-history surface (#487)
 * against real Postgres. Walks the snapshot-on-update path, the
 * GET /history endpoint authorisation matrix, and verifies the
 * JSONB round-trip for previous_hashtags (first JSONB column added
 * to this codebase via Hibernate 6 native binding).
 *
 * <p>The test profile disables rate-limiting, the cleanup scheduler,
 * and the trending refresh, so this suite is not coupled to any
 * cron firing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedPostHistoryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        // Children first by FK, then parents.
        jdbcTemplate.update("DELETE FROM feed_post_edit_history");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── Snapshot capture on PATCH ──────────────────────────────────────────

    @Test
    void twoEdits_yieldTwoHistoryRowsNewestFirst_capturingPriorState() throws Exception {
        String token = registerAndLogin("hist_a@test.com", true);
        long postId = createPost(token, "v1", List.of("alpha", "beta"));

        // First edit: change body only.
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"v2\"}"))
                .andExpect(status().isOk());

        // Second edit: change hashtags only.
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hashtags\":[\"gamma\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first: the second edit's snapshot captured
                // body="v2" and the hashtag set ["alpha","beta"] that
                // existed AFTER the first edit but BEFORE the second.
                .andExpect(jsonPath("$[0].previousBody").value("v2"))
                .andExpect(jsonPath("$[0].previousHashtags.length()").value(2))
                .andExpect(jsonPath("$[0].previousHashtags[0]").value("alpha"))
                .andExpect(jsonPath("$[0].previousHashtags[1]").value("beta"))
                // Oldest entry: the first edit's snapshot — body was
                // the original "v1" with the same starting hashtag set.
                .andExpect(jsonPath("$[1].previousBody").value("v1"))
                .andExpect(jsonPath("$[1].previousHashtags.length()").value(2))
                .andExpect(jsonPath("$[1].previousHashtags[0]").value("alpha"))
                .andExpect(jsonPath("$[1].previousHashtags[1]").value("beta"));
    }

    @Test
    void noopPatch_doesNotProduceHistoryRow() throws Exception {
        String token = registerAndLogin("hist_noop@test.com", true);
        long postId = createPost(token, "same body", List.of("tag"));

        // PATCH with the existing values — no real change.
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"same body\",\"hashtags\":[\"tag\"]}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_edit_history WHERE post_id = ?",
                Integer.class, postId);
        assertThat(count).isZero();
    }

    @Test
    void bothFieldsNullPatch_doesNotProduceHistoryRow() throws Exception {
        String token = registerAndLogin("hist_null@test.com", true);
        long postId = createPost(token, "body", List.of("tag"));

        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_edit_history WHERE post_id = ?",
                Integer.class, postId);
        assertThat(count).isZero();
    }

    // ── GET /history authorisation matrix ──────────────────────────────────

    @Test
    void author_canReadHistory() throws Exception {
        String token = registerAndLogin("hist_owner@test.com", true);
        long postId = createPost(token, "owner body", List.of());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canReadHistoryOfAnyPost() throws Exception {
        String authorToken = registerAndLogin("hist_author2@test.com", true);
        long postId = createPost(authorToken, "any body", List.of());
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"updated\"}"))
                .andExpect(status().isOk());

        String adminToken = registerAdminAndLogin("hist_admin@test.com");
        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].previousBody").value("any body"));
    }

    @Test
    void nonAuthorNonAdmin_getsForbidden() throws Exception {
        String authorToken = registerAndLogin("hist_owner3@test.com", true);
        String otherToken = registerAndLogin("hist_other@test.com", true);
        long postId = createPost(authorToken, "private body", List.of());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingPost_returns404() throws Exception {
        String token = registerAndLogin("hist_missing@test.com", true);

        mockMvc.perform(get("/api/feed/posts/999999/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyVisible_evenAfterSoftDelete() throws Exception {
        String token = registerAndLogin("hist_softdel@test.com", true);
        long postId = createPost(token, "v1", List.of());
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"v2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Author can still see the history entry even though the post
        // is soft-deleted — useful for moderation/audit before the
        // cleanup scheduler hard-deletes it.
        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].previousBody").value("v1"));
    }

    // ── @Min/@Max clamp on limit param (defence-in-depth) ───────────────────

    @Test
    void limitOver50_returns400() throws Exception {
        String token = registerAndLogin("hist_lim@test.com", true);
        long postId = createPost(token, "body", List.of());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/history?limit=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitZero_returns400() throws Exception {
        String token = registerAndLogin("hist_lim0@test.com", true);
        long postId = createPost(token, "body", List.of());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/history?limit=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ── JSONB round-trip on previous_hashtags ───────────────────────────────

    @Test
    void jsonbColumn_roundTripsListOfStrings_preservingOrder() throws Exception {
        String token = registerAndLogin("hist_jsonb@test.com", true);
        long postId = createPost(token, "v1",
                List.of("zeta", "alpha", "mü", "data_science"));
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"v2\"}"))
                .andExpect(status().isOk());

        // The snapshot captures the hashtag set sorted alphabetically
        // (TreeSet inside the service); the JSONB column round-trips
        // the resulting List<String> exactly via @JdbcTypeCode(SqlTypes.JSON).
        // The tags stored are the post's current tags, which are normalised
        // to lowercase already on creation (see HashtagNormalizer); we
        // assert the alphabetical order survived the JSONB write/read.
        mockMvc.perform(get("/api/feed/posts/" + postId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previousHashtags.length()").value(4))
                .andExpect(jsonPath("$[0].previousHashtags[0]").value("alpha"))
                .andExpect(jsonPath("$[0].previousHashtags[1]").value("data_science"))
                .andExpect(jsonPath("$[0].previousHashtags[2]").value("mü"))
                .andExpect(jsonPath("$[0].previousHashtags[3]").value("zeta"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("History");
        req.setLastName("Tester");
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

    private String registerAdminAndLogin(String email) throws Exception {
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("Password1"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setIsEmailVerified(true);
        userRepository.save(admin);

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

    private long createPost(String token, String body, List<String> hashtags) throws Exception {
        Map<String, Object> req = Map.of("body", body, "hashtags", hashtags);
        MvcResult res = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
}
