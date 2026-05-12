package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the social-feed interaction surfaces (#347):
 * like / bookmark toggles, comment lifecycle, share events. Real
 * Postgres so the native ON CONFLICT upsert and the FK cascades are
 * actually exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedInteractionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private FeedPostLikeRepository likeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
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

    // ── Likes ──────────────────────────────────────────────────────────────

    @Test
    void likeToggle_idempotent_andReflectsState() throws Exception {
        String tokenA = registerAndLogin("like_a@test.com");
        String tokenB = registerAndLogin("like_b@test.com");
        long pid = createPost(tokenA, "post by A", List.of());

        // B likes
        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.viewerHasLiked").value(true));

        // B toggles off
        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.viewerHasLiked").value(false));
    }

    @Test
    void likeOnDeletedPost_returns404() throws Exception {
        String tokenA = registerAndLogin("like_del_a@test.com");
        long pid = createPost(tokenA, "to be deleted", List.of());
        mockMvc.perform(delete("/api/feed/posts/" + pid)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrent50Likes_finalCountIs50() throws Exception {
        // Distinct followers all liking the same post — every one should
        // surface as a fresh insert; ON CONFLICT DO NOTHING keeps counts
        // honest under contention.
        String authorToken = registerAndLogin("conc_author@test.com");
        long pid = createPost(authorToken, "popular", List.of());

        int N = 50;
        List<String> tokens = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            tokens.add(registerAndLogin("conc_" + i + "@test.com"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>(N);
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        return 500;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Integer> f : futures) {
                if (f.get(30, TimeUnit.SECONDS) == 200) successes++;
            }
            assertThat(successes).isEqualTo(N);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        assertThat(likeRepository.countByIdPostId(pid)).isEqualTo(N);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @Test
    void bookmarkToggle_andListBookmarks() throws Exception {
        String tokenA = registerAndLogin("bm_a@test.com");
        String tokenB = registerAndLogin("bm_b@test.com");
        long pid = createPost(tokenA, "post by A", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/bookmark")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarkCount").value(1))
                .andExpect(jsonPath("$.viewerHasBookmarked").value(true));

        mockMvc.perform(get("/api/feed/me/bookmarks")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pid));

        // Toggle off
        mockMvc.perform(post("/api/feed/posts/" + pid + "/bookmark")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.bookmarkCount").value(0));

        mockMvc.perform(get("/api/feed/me/bookmarks")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listBookmarks_surfacesLikeAndVisibleCommentCounts() throws Exception {
        String authorToken = registerAndLogin("bm_counts_author@test.com");
        String viewerToken = registerAndLogin("bm_counts_viewer@test.com");
        long pid = createPost(authorToken, "post that gets bookmarked", List.of());

        // Two likes + one visible comment + one deleted comment on the post.
        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"kept\"}"))
                .andExpect(status().isCreated());
        MvcResult dRes = mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"to delete\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long doomed = objectMapper.readTree(dRes.getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feed/comments/" + doomed)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNoContent());

        // Viewer bookmarks the post and fetches their bookmark list.
        mockMvc.perform(post("/api/feed/posts/" + pid + "/bookmark")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/me/bookmarks")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pid))
                .andExpect(jsonPath("$.content[0].likeCount").value(2))
                .andExpect(jsonPath("$.content[0].commentCount").value(1));
    }

    // ── Shares ─────────────────────────────────────────────────────────────

    @Test
    void share_appendOnly_countsAccumulate() throws Exception {
        String tokenA = registerAndLogin("share_a@test.com");
        String tokenB = registerAndLogin("share_b@test.com");
        long pid = createPost(tokenA, "shareable", List.of());

        // B shares twice — both events recorded
        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareCount").value(1));
        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareCount").value(2));
    }

    // ── Reposts (#484) ─────────────────────────────────────────────────────

    @Test
    void repost_bare_createsRow_andNotifiesAuthor() throws Exception {
        String authorToken = registerAndLogin("repost_bare_author@test.com");
        String sharerToken = registerAndLogin("repost_bare_sharer@test.com");
        long pid = createPost(authorToken, "post to bare-repost", List.of());
        long authorId = userIdByEmail("repost_bare_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareCount").value(1));

        // DB row: exactly one share, is_repost=TRUE, body=NULL.
        Integer repostRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE AND body IS NULL",
                Integer.class, pid);
        assertThat(repostRows).isEqualTo(1);

        // Author notified via the existing FEED_SHARE channel.
        awaitNotifications(authorId, NotificationType.FEED_SHARE, 1);
    }

    @Test
    void repost_quote_persistsCommentary_andNotifiesAuthor() throws Exception {
        String authorToken = registerAndLogin("repost_quote_author@test.com");
        String sharerToken = registerAndLogin("repost_quote_sharer@test.com");
        long pid = createPost(authorToken, "post to quote-share", List.of());
        long authorId = userIdByEmail("repost_quote_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"great take\"}"))
                .andExpect(status().isOk());

        String body = jdbcTemplate.queryForObject(
                "SELECT body FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE",
                String.class, pid);
        assertThat(body).isEqualTo("great take");

        awaitNotifications(authorId, NotificationType.FEED_SHARE, 1);
    }

    @Test
    void repost_softDeletedPost_returns404() throws Exception {
        String authorToken = registerAndLogin("repost_404_author@test.com");
        String sharerToken = registerAndLogin("repost_404_sharer@test.com");
        long pid = createPost(authorToken, "to be deleted", List.of());

        mockMvc.perform(delete("/api/feed/posts/" + pid)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void repost_self_doesNotNotifyAuthor() throws Exception {
        String authorToken = registerAndLogin("repost_self_author@test.com");
        long pid = createPost(authorToken, "self-repost", List.of());
        long authorId = userIdByEmail("repost_self_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());

        // Give the AFTER_COMMIT listener a beat; FEED_SHARE count must stay
        // at zero because the sharer is the author.
        Thread.sleep(300);
        assertThat(notificationsFor(authorId, NotificationType.FEED_SHARE)).isEmpty();
    }

    @Test
    void repost_idempotencyWindow_secondCallCollapses() throws Exception {
        String authorToken = registerAndLogin("repost_idem_author@test.com");
        String sharerToken = registerAndLogin("repost_idem_sharer@test.com");
        long pid = createPost(authorToken, "post for idempotency", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"identical\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"identical\"}"))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE",
                Integer.class, pid);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void repost_idempotencyKeyHeader_acceptedAndIgnored() throws Exception {
        String authorToken = registerAndLogin("repost_key_author@test.com");
        String sharerToken = registerAndLogin("repost_key_sharer@test.com");
        long pid = createPost(authorToken, "post for idempotency key", List.of());

        // With header — must accept normally (no 4xx).
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk());

        // Different sharer, same pattern — header on, but content differs so
        // the idempotency window does not collapse this independent action.
        String sharer2 = registerAndLogin("repost_key_sharer2@test.com");
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharer2)
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000002"))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE",
                Integer.class, pid);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void repost_validation_oversizeBody_returns400() throws Exception {
        String authorToken = registerAndLogin("repost_val_author@test.com");
        String sharerToken = registerAndLogin("repost_val_sharer@test.com");
        long pid = createPost(authorToken, "post for validation", List.of());

        String oversize = "x".repeat(2001);
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", oversize))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repost_blankBody_isAcceptedAsBareRepost() throws Exception {
        String authorToken = registerAndLogin("repost_blank_author@test.com");
        String sharerToken = registerAndLogin("repost_blank_sharer@test.com");
        long pid = createPost(authorToken, "blank-body repost", List.of());

        // {"body":"   "} — whitespace-only collapses to NULL via blankToNull;
        // the DB CHECK feed_post_shares_body_nonblank never fires because
        // the persisted value is NULL.
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isOk());

        String body = jdbcTemplate.queryForObject(
                "SELECT body FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE",
                String.class, pid);
        assertThat(body).isNull();
    }

    @Test
    void repost_emptyJsonBody_isAcceptedAsBareRepost() throws Exception {
        String authorToken = registerAndLogin("repost_empty_author@test.com");
        String sharerToken = registerAndLogin("repost_empty_sharer@test.com");
        long pid = createPost(authorToken, "empty-json repost", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        // No request body at all — same outcome.
        String sharer2 = registerAndLogin("repost_empty_sharer2@test.com");
        mockMvc.perform(post("/api/feed/posts/" + pid + "/reposts")
                        .header("Authorization", "Bearer " + sharer2))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE AND body IS NULL",
                Integer.class, pid);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void repost_silentShareUnchanged_doesNotWriteIsRepostFlag() throws Exception {
        // Regression guard: existing /share contract preserved verbatim.
        String authorToken = registerAndLogin("repost_silent_author@test.com");
        String sharerToken = registerAndLogin("repost_silent_sharer@test.com");
        long pid = createPost(authorToken, "silent share test", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + sharerToken))
                .andExpect(status().isOk());

        Integer silentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = FALSE AND body IS NULL",
                Integer.class, pid);
        assertThat(silentRows).isEqualTo(1);
        Integer repostRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ? AND is_repost = TRUE",
                Integer.class, pid);
        assertThat(repostRows).isZero();
    }

    // ── Comments ───────────────────────────────────────────────────────────

    @Test
    void commentLifecycle_create_list_edit_delete() throws Exception {
        String tokenA = registerAndLogin("cmt_a@test.com");
        String tokenB = registerAndLogin("cmt_b@test.com");
        long pid = createPost(tokenA, "post for comments", List.of());

        // B adds a comment
        MvcResult create = mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"first comment\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("first comment"))
                .andExpect(jsonPath("$.isAuthor").value(true))
                .andReturn();
        long commentId = objectMapper.readTree(create.getResponse().getContentAsString())
                .get("id").asLong();

        // List comments
        mockMvc.perform(get("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // A (non-author of comment) can't edit
        mockMvc.perform(patch("/api/feed/comments/" + commentId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hostile\"}"))
                .andExpect(status().isForbidden());

        // B edits
        Thread.sleep(20);
        mockMvc.perform(patch("/api/feed/comments/" + commentId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("edited"))
                .andExpect(jsonPath("$.isEdited").value(true));

        // B soft-deletes
        mockMvc.perform(delete("/api/feed/comments/" + commentId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        // List still shows it as a placeholder (body=null, isDeleted=true)
        mockMvc.perform(get("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isDeleted").value(true))
                .andExpect(jsonPath("$.content[0].body").doesNotExist());
    }

    @Test
    void commentValidation_blankBody_returns400() throws Exception {
        String token = registerAndLogin("cmt_blank@test.com");
        long pid = createPost(token, "post", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Cascade ────────────────────────────────────────────────────────────

    @Test
    void postDelete_cascadesAllInteractionTables() throws Exception {
        String tokenA = registerAndLogin("csc_a@test.com");
        String tokenB = registerAndLogin("csc_b@test.com");
        long pid = createPost(tokenA, "to cascade", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/bookmark")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hi\"}"))
                .andExpect(status().isCreated());

        // Hard-delete the post via JDBC to fire the FK cascade.
        // (API-level DELETE is soft; we want to verify the FK cascade
        // wires correctly for any future hard-delete code path.)
        jdbcTemplate.update("DELETE FROM feed_post_hashtags WHERE post_id = ?", pid);
        jdbcTemplate.update("DELETE FROM feed_posts WHERE id = ?", pid);

        Integer likes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_likes WHERE post_id = ?", Integer.class, pid);
        Integer bookmarks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_bookmarks WHERE post_id = ?", Integer.class, pid);
        Integer shares = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_shares WHERE post_id = ?", Integer.class, pid);
        Integer comments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_comments WHERE post_id = ?", Integer.class, pid);
        assertThat(likes).isZero();
        assertThat(bookmarks).isZero();
        assertThat(shares).isZero();
        assertThat(comments).isZero();
    }

    // ── GET /interactions — read-without-toggle ────────────────────────────

    @Test
    void getInteractions_returnsAllCounts_andViewerFlags() throws Exception {
        // Build state: A creates a post; B likes + bookmarks + shares + comments
        // on it. C reads /interactions and sees the counts but no viewer
        // flags. B reads and sees the same counts plus their flags set.
        String tokenA = registerAndLogin("ix_a@test.com");
        String tokenB = registerAndLogin("ix_b@test.com");
        String tokenC = registerAndLogin("ix_c@test.com");
        long pid = createPost(tokenA, "post for interactions", List.of());

        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/bookmark")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"nice\"}"))
                .andExpect(status().isCreated());

        // C reads — sees all counts, no viewer flags.
        mockMvc.perform(get("/api/feed/posts/" + pid + "/interactions")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.bookmarkCount").value(1))
                .andExpect(jsonPath("$.shareCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.viewerHasLiked").value(false))
                .andExpect(jsonPath("$.viewerHasBookmarked").value(false));

        // B reads — same counts, viewer flags set.
        mockMvc.perform(get("/api/feed/posts/" + pid + "/interactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerHasLiked").value(true))
                .andExpect(jsonPath("$.viewerHasBookmarked").value(true));
    }

    @Test
    void getInteractions_softDeletedPost_returns404() throws Exception {
        String tokenA = registerAndLogin("ix_del@test.com");
        long pid = createPost(tokenA, "delete me", List.of());
        mockMvc.perform(delete("/api/feed/posts/" + pid)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/feed/posts/" + pid + "/interactions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // ── Auth gate ──────────────────────────────────────────────────────────

    @Test
    void allEndpoints_unauthenticated_return403() throws Exception {
        mockMvc.perform(post("/api/feed/posts/1/like")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/feed/posts/1/bookmark")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/feed/posts/1/share")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/feed/posts/1/comments")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/me/bookmarks")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/posts/1/interactions")).andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Inter");
        req.setLastName("Action");
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

    private long userIdByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private List<Notification> notificationsFor(Long userId, NotificationType type) {
        return notificationRepository.findForUser(userId, false).stream()
                .filter(n -> n.getType() == type)
                .toList();
    }

    private List<Notification> awaitNotifications(Long userId, NotificationType type, int expectedCount) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(notificationsFor(userId, type)).hasSize(expectedCount));
        return notificationsFor(userId, type);
    }

    // ── Engagement notifications ───────────────────────────────────────────

    @Test
    void toggleLike_publishesFeedLikeNotification_toPostAuthorOnly() throws Exception {
        String authorToken = registerAndLogin("notif_like_author@test.com");
        String likerToken = registerAndLogin("notif_like_liker@test.com");
        long pid = createPost(authorToken, "post to like", List.of());
        long authorId = userIdByEmail("notif_like_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk());

        List<Notification> rows = awaitNotifications(authorId, NotificationType.FEED_LIKE, 1);
        assertThat(rows.get(0).getBody()).contains("liked your post.");

        // Author self-likes → no extra notification (self-actor skip).
        mockMvc.perform(post("/api/feed/posts/" + pid + "/like")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
        // Give the async listener a beat; it should still settle at 1.
        Thread.sleep(200);
        assertThat(notificationsFor(authorId, NotificationType.FEED_LIKE)).hasSize(1);
    }

    @Test
    void toggleLike_secondLikeAcrossPosts_collapsesUnder24hDedup() throws Exception {
        String authorToken = registerAndLogin("notif_dedup_author@test.com");
        String likerToken = registerAndLogin("notif_dedup_liker@test.com");
        long pidA = createPost(authorToken, "post A", List.of());
        long pidB = createPost(authorToken, "post B", List.of());
        long authorId = userIdByEmail("notif_dedup_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pidA + "/like")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk());
        awaitNotifications(authorId, NotificationType.FEED_LIKE, 1);
        mockMvc.perform(post("/api/feed/posts/" + pidB + "/like")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk());
        // Same body "<firstName> liked your post." → second insert is deduped.
        // Give the listener time to run and confirm it stays at 1.
        Thread.sleep(300);
        assertThat(notificationsFor(authorId, NotificationType.FEED_LIKE)).hasSize(1);
    }

    @Test
    void addComment_publishesFeedCommentNotification_toPostAuthor() throws Exception {
        String authorToken = registerAndLogin("notif_cmt_author@test.com");
        String commenterToken = registerAndLogin("notif_cmt_commenter@test.com");
        long pid = createPost(authorToken, "post to comment", List.of());
        long authorId = userIdByEmail("notif_cmt_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + commenterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isCreated());

        List<Notification> rows = awaitNotifications(authorId, NotificationType.FEED_COMMENT, 1);
        assertThat(rows.get(0).getBody()).contains("commented on your post.");

        // Author self-comments → no extra notification (self-actor skip).
        mockMvc.perform(post("/api/feed/posts/" + pid + "/comments")
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"my own\"}"))
                .andExpect(status().isCreated());
        Thread.sleep(200);
        assertThat(notificationsFor(authorId, NotificationType.FEED_COMMENT)).hasSize(1);
    }

    @Test
    void recordShare_publishesFeedShareNotification_toPostAuthor() throws Exception {
        String authorToken = registerAndLogin("notif_share_author@test.com");
        String sharerToken = registerAndLogin("notif_share_sharer@test.com");
        long pid = createPost(authorToken, "post to share", List.of());
        long authorId = userIdByEmail("notif_share_author@test.com");

        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + sharerToken))
                .andExpect(status().isOk());

        List<Notification> rows = awaitNotifications(authorId, NotificationType.FEED_SHARE, 1);
        assertThat(rows.get(0).getBody()).contains("shared your post.");

        // Author self-shares → no extra notification.
        mockMvc.perform(post("/api/feed/posts/" + pid + "/share")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk());
        Thread.sleep(200);
        assertThat(notificationsFor(authorId, NotificationType.FEED_SHARE)).hasSize(1);
    }
}
