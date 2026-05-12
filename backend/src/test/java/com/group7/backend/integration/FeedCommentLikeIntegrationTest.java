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
 * End-to-end coverage for the comment-like surface (#483) against real
 * Postgres. Walks the toggle round-trip, two-viewer aggregate, the
 * page-level state batching contract, the soft-delete 404 branch, the
 * anonymous-403 gate, and the FK ON DELETE CASCADE on hard-delete of
 * the parent comment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedCommentLikeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_comment_likes");
        jdbcTemplate.update("DELETE FROM feed_post_comments");
        jdbcTemplate.update("DELETE FROM feed_post_shares");
        jdbcTemplate.update("DELETE FROM feed_post_bookmarks");
        jdbcTemplate.update("DELETE FROM feed_post_likes");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── Toggle round-trip ──────────────────────────────────────────────────

    @Test
    void firstLike_returns200_withViewerHasLikedTrue_andCount1() throws Exception {
        String token = registerAndLogin("cl_a@test.com");
        long postId = createPost(token, "post");
        long commentId = createComment(token, postId, "first");

        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerHasLiked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    void secondLike_togglesOff_withViewerHasLikedFalse_andCount0() throws Exception {
        String token = registerAndLogin("cl_b@test.com");
        long postId = createPost(token, "post");
        long commentId = createComment(token, postId, "first");
        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerHasLiked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    // ── 404 branches ───────────────────────────────────────────────────────

    @Test
    void likeOnSoftDeletedComment_returns404() throws Exception {
        String token = registerAndLogin("cl_del@test.com");
        long postId = createPost(token, "post");
        long commentId = createComment(token, postId, "first");
        mockMvc.perform(delete("/api/feed/comments/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void likeOnMissingComment_returns404() throws Exception {
        String token = registerAndLogin("cl_miss@test.com");

        mockMvc.perform(post("/api/feed/comments/999999/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── ACL ────────────────────────────────────────────────────────────────

    @Test
    void anonymousLike_returns403() throws Exception {
        String token = registerAndLogin("cl_owner@test.com");
        long postId = createPost(token, "post");
        long commentId = createComment(token, postId, "first");

        // Project convention is 403 (Spring Security default for missing
        // Authorization header), not 401.
        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like"))
                .andExpect(status().isForbidden());
    }

    // ── Multi-viewer aggregate + per-viewer flag ───────────────────────────

    @Test
    void twoViewersLikeSameComment_likeCountIs2_andEachViewerSeesOwnFlag() throws Exception {
        String tokenA = registerAndLogin("cl_two_a@test.com");
        String tokenB = registerAndLogin("cl_two_b@test.com");
        long postId = createPost(tokenA, "post");
        long commentId = createComment(tokenA, postId, "first");

        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.viewerHasLiked").value(true));

        // Viewer A's perspective via list endpoint.
        mockMvc.perform(get("/api/feed/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].likeCount").value(2))
                .andExpect(jsonPath("$.content[0].viewerHasLiked").value(true));

        // Viewer B's perspective.
        mockMvc.perform(get("/api/feed/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].likeCount").value(2))
                .andExpect(jsonPath("$.content[0].viewerHasLiked").value(true));
    }

    @Test
    void listComments_perCommentLikedFlagReflectsViewer() throws Exception {
        String authorToken = registerAndLogin("cl_list_author@test.com");
        String viewerToken = registerAndLogin("cl_list_viewer@test.com");
        long postId = createPost(authorToken, "post");
        long c1 = createComment(authorToken, postId, "one");
        long c2 = createComment(authorToken, postId, "two");
        long c3 = createComment(authorToken, postId, "three");

        // Viewer likes c1 and c3, not c2.
        mockMvc.perform(post("/api/feed/comments/" + c1 + "/like")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/comments/" + c3 + "/like")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(c1))
                .andExpect(jsonPath("$.content[0].viewerHasLiked").value(true))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[1].id").value(c2))
                .andExpect(jsonPath("$.content[1].viewerHasLiked").value(false))
                .andExpect(jsonPath("$.content[1].likeCount").value(0))
                .andExpect(jsonPath("$.content[2].id").value(c3))
                .andExpect(jsonPath("$.content[2].viewerHasLiked").value(true))
                .andExpect(jsonPath("$.content[2].likeCount").value(1));
    }

    // ── FK ON DELETE CASCADE on hard-delete of comment ────────────────────

    @Test
    void hardDeletingComment_cascadesToFeedPostCommentLikes() throws Exception {
        String token = registerAndLogin("cl_cascade@test.com");
        long postId = createPost(token, "post");
        long commentId = createComment(token, postId, "first");
        mockMvc.perform(post("/api/feed/comments/" + commentId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Integer before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_post_comment_likes WHERE comment_id = ?",
                Integer.class, commentId);
        assertThat(before).isEqualTo(1);

        // Hard-delete the comment row directly (mirrors what the #487
        // cleanup scheduler will do once it lands).
        jdbcTemplate.update("DELETE FROM feed_post_comments WHERE id = ?", commentId);

        Integer after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_post_comment_likes WHERE comment_id = ?",
                Integer.class, commentId);
        assertThat(after).isZero();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("CL");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(true);
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

    private long createPost(String token, String body) throws Exception {
        Map<String, Object> req = Map.of("body", body, "hashtags", List.of("test"));
        MvcResult res = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createComment(String token, long postId, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/feed/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
}
