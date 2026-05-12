package com.group7.backend.integration;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostEditHistoryRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.scheduler.FeedSoftDeleteCleanupScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for {@link FeedSoftDeleteCleanupScheduler} (#487)
 * against real Postgres. {@code @TestPropertySource} flips the
 * scheduler bean back on (the bulk test profile sets it off so a stray
 * cron doesn't fire during unrelated suites); the cleanup method is
 * invoked directly so the test does not have to wait for the cron tick.
 *
 * <p>Time-travel for the "deleted past the window" branch is achieved
 * by back-dating {@code deleted_at} via {@code JdbcTemplate} — same
 * pattern as {@link FeedPostRestoreIntegrationTest} for consistency.
 *
 * <p>Verifies the cascade contract: when a feed post is hard-deleted,
 * its child rows in {@code feed_post_comments},
 * {@code feed_post_hashtags}, and {@code feed_post_edit_history} are
 * reaped via the existing {@code ON DELETE CASCADE} foreign keys.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.feed.cleanup.enabled=true",
        // Pin the window short and explicit so the test asserts against
        // a known boundary rather than the production default.
        "app.feed.cleanup.restore-window-days=30"
})
class FeedSoftDeleteCleanupIntegrationTest {

    @Autowired private FeedSoftDeleteCleanupScheduler scheduler;
    @Autowired private FeedPostRepository postRepository;
    @Autowired private FeedPostCommentRepository commentRepository;
    @Autowired private FeedPostEditHistoryRepository historyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_edit_history");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_post_likes");
        jdbcTemplate.update("DELETE FROM feed_post_comments");
        jdbcTemplate.update("DELETE FROM feed_posts");
        userRepository.deleteAll();
    }

    @Test
    void purge_hardDeletesPostsBeyondWindow_andCascadesChildRows() {
        Long authorId = createMentor("cleanup_a@test.com");
        long expiredPostId = createPost(authorId, "expired");
        long freshPostId = createPost(authorId, "fresh");
        long livePostId = createPost(authorId, "still here");

        // Seed a comment + an edit-history row on each post so the
        // cascade contract has something to verify against.
        long expiredCommentId = createComment(expiredPostId, authorId, "comment on expired");
        long freshCommentId = createComment(freshPostId, authorId, "comment on fresh");
        seedHistoryRow(expiredPostId, authorId, "old body");
        seedHistoryRow(freshPostId, authorId, "old body");

        // Soft-delete two of the three posts; back-date one past the
        // 30-day window, leave the other recent.
        Timestamp longAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(31));
        Timestamp recent = Timestamp.valueOf(LocalDateTime.now().minusDays(5));
        jdbcTemplate.update("UPDATE feed_posts SET deleted_at = ? WHERE id = ?",
                longAgo, expiredPostId);
        jdbcTemplate.update("UPDATE feed_posts SET deleted_at = ? WHERE id = ?",
                recent, freshPostId);

        scheduler.purgeExpiredSoftDeletes();

        // Expired post + its children gone via FK cascade.
        assertThat(postRepository.findById(expiredPostId)).isEmpty();
        assertThat(commentRepository.findById(expiredCommentId)).isEmpty();
        Integer expiredHistoryRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_edit_history WHERE post_id = ?",
                Integer.class, expiredPostId);
        assertThat(expiredHistoryRows).isZero();
        Integer expiredHashtagRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_hashtags WHERE post_id = ?",
                Integer.class, expiredPostId);
        assertThat(expiredHashtagRows).isZero();

        // Fresh-soft-delete and live posts survive (and their children).
        assertThat(postRepository.findById(freshPostId)).isPresent();
        assertThat(commentRepository.findById(freshCommentId)).isPresent();
        assertThat(postRepository.findById(livePostId)).isPresent();
    }

    @Test
    void purge_isNoOpWhenNothingExpired() {
        Long authorId = createMentor("cleanup_noop@test.com");
        long postId = createPost(authorId, "live");

        scheduler.purgeExpiredSoftDeletes();

        assertThat(postRepository.findById(postId)).isPresent();
    }

    @Test
    void purge_alsoReapsCommentsSoftDeletedIndependentlyOfTheirPost() {
        // Today comments only soft-delete via the comment surface
        // (#347); when a future moderator-comment-delete feature
        // arrives the same scheduler is the right reaper. Verifies
        // the symmetric DELETE on feed_post_comments works.
        Long authorId = createMentor("cleanup_comment@test.com");
        long postId = createPost(authorId, "live post");

        long expiredCommentId = createComment(postId, authorId, "old reply");
        Timestamp longAgo = Timestamp.valueOf(LocalDateTime.now().minusDays(31));
        jdbcTemplate.update("UPDATE feed_post_comments SET deleted_at = ? WHERE id = ?",
                longAgo, expiredCommentId);

        scheduler.purgeExpiredSoftDeletes();

        // Parent post unaffected; the orphaned comment is reaped.
        assertThat(postRepository.findById(postId)).isPresent();
        assertThat(commentRepository.findById(expiredCommentId)).isEmpty();
    }

    @Test
    void v43PartialIndexes_existAfterMigration() {
        // Sanity check that V43 created the cleanup-side partial indexes.
        // Without them the scheduler still works but at seq-scan cost on
        // a large table. The migration runs once at context startup; if
        // a refactor accidentally drops the CREATE INDEX statements,
        // this test catches it before the prod deploy.
        Integer postsIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_feed_posts_deleted_at_pending_cleanup'",
                Integer.class);
        Integer commentsIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_feed_post_comments_deleted_at_pending_cleanup'",
                Integer.class);
        assertThat(postsIndex).isEqualTo(1);
        assertThat(commentsIndex).isEqualTo(1);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Long createMentor(String email) {
        Mentor m = new Mentor();
        m.setEmail(email);
        m.setPasswordHash(new BCryptPasswordEncoder().encode("Password1"));
        m.setFirstName("Cleanup");
        m.setLastName("Tester");
        m.setIsEmailVerified(true);
        return userRepository.save(m).getId();
    }

    private long createPost(Long authorId, String body) {
        FeedPost post = new FeedPost(authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        return postRepository.save(post).getId();
    }

    private long createComment(Long postId, Long authorId, String body) {
        FeedPostComment c = new FeedPostComment(postId, authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return commentRepository.save(c).getId();
    }

    private void seedHistoryRow(long postId, Long editorId, String previousBody) {
        // Bypass the entity to use the generated id; we don't care
        // about the JSONB content here, only that the cascade reaps
        // the row when the parent post is hard-deleted.
        jdbcTemplate.update(
                "INSERT INTO feed_post_edit_history (post_id, editor_id, previous_body, previous_hashtags, edited_at) "
                        + "VALUES (?, ?, ?, '[]'::jsonb, NOW())",
                postId, editorId, previousBody);
    }
}
