package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the soft-delete restore endpoint (#487)
 * against real Postgres. Exercises the full status-code surface of
 * {@code POST /api/feed/posts/{id}/restore}: 200 (happy path), 401
 * (unauthenticated), 403 (non-author), 404 (missing), 409 (live
 * post), and 410 (expired window). Time-travel for the expired-window
 * branch is achieved by back-dating {@code deleted_at} via
 * {@code JdbcTemplate} — keeping the system clock untouched is
 * simpler than introducing a fixed-{@code Clock} test bean and avoids
 * coupling unrelated time-sensitive code paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedPostRestoreIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_edit_history");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void deleteThenRestore_makesPostVisibleAgain() throws Exception {
        String token = registerAndLogin("restore_a@test.com", true);
        long postId = createPost(token, "to be restored", List.of("alpha"));

        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Soft-deleted: GET → 404.
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // Restore → 200 with the post body restored.
        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.body").value("to be restored"))
                .andExpect(jsonPath("$.hashtags[0]").value("alpha"));

        // Now visible again.
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void restoreOnLivePost_returns409() throws Exception {
        String token = registerAndLogin("restore_live@test.com", true);
        long postId = createPost(token, "still live", List.of());

        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void restoreByNonAuthor_returns403() throws Exception {
        String authorToken = registerAndLogin("restore_owner@test.com", true);
        String otherToken = registerAndLogin("restore_other@test.com", true);
        long postId = createPost(authorToken, "private body", List.of());

        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreOnMissingPost_returns404() throws Exception {
        String token = registerAndLogin("restore_missing@test.com", true);

        mockMvc.perform(post("/api/feed/posts/999999/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreAfterWindow_returns410() throws Exception {
        String token = registerAndLogin("restore_expired@test.com", true);
        long postId = createPost(token, "old post", List.of());

        // Soft-delete via the API to validate the public path …
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        // … then back-date deleted_at past the configured window.
        Timestamp longAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(31));
        jdbcTemplate.update(
                "UPDATE feed_posts SET deleted_at = ? WHERE id = ?",
                longAgo, postId);

        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());
    }

    @Test
    void restoreWithoutAuth_returns403() throws Exception {
        // Project convention: missing/invalid Authorization header
        // surfaces as 403 (Spring Security's default unauthenticated
        // response), not 401. The same shape is observable on every
        // protected endpoint in the codebase.
        String token = registerAndLogin("restore_anon@test.com", true);
        long postId = createPost(token, "guarded", List.of());
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doubleRestore_secondCallReturns409() throws Exception {
        // Documented contract: restore is not idempotent. The first call
        // succeeds (200); the second call sees a live post and returns 409.
        String token = registerAndLogin("restore_double@test.com", true);
        long postId = createPost(token, "twice restored?", List.of());

        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + postId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Restore");
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
