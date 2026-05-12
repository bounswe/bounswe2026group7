package com.group7.backend.scheduler;

import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Daily sweep that hard-deletes feed posts and comments soft-deleted
 * longer ago than the configured restore window (#487). Runs at 03:45 UTC
 * by default — off-peak across both Turkey and Europe timezones, and a
 * slot unused by the existing schedulers (03:15 attachment-orphan, 03:30
 * bot-signal, 09:00 match-notification).
 *
 * <p>Bound to the same {@code app.feed.cleanup.restore-window-days} value
 * that {@code FeedPostService.restorePost} consults, so the moment a post
 * becomes ineligible for restore (410 Gone) is exactly the moment it
 * becomes eligible for hard-delete here.
 *
 * <h2>Concurrency</h2>
 * <p>ShedLock is not wired in this project (see
 * {@code MatchNotificationScheduler}'s javadoc). The deploy is single-node;
 * if multi-node ever ships, the bulk DELETE
 * ({@code WHERE deleted_at IS NOT NULL AND deleted_at < cutoff}) is
 * monotonic — a duplicate run from a second node reaps zero rows because
 * the first run already moved the cutoff. No double-delete, no harm.
 *
 * <h2>Cascade</h2>
 * <p>Hard-deleting a {@code feed_posts} row reaps every related row via
 * the existing FK {@code ON DELETE CASCADE} constraints:
 * {@code feed_post_likes}, {@code feed_post_bookmarks},
 * {@code feed_post_shares}, {@code feed_post_comments},
 * {@code feed_post_hashtags}, and {@code feed_post_edit_history}.
 * Comments soft-deleted independently of their parent post are reaped
 * by the symmetric comment-side delete.
 *
 * <h2>Test gating</h2>
 * <p>{@code @ConditionalOnProperty(matchIfMissing = true)} means the
 * scheduler is on by default in production. The test profile sets
 * {@code app.feed.cleanup.enabled=false} so the scheduler bean is not
 * created during the bulk test suite; the dedicated integration test
 * for this scheduler flips the flag back on via {@code @TestPropertySource}.
 */
@Component
@ConditionalOnProperty(name = "app.feed.cleanup.enabled",
        havingValue = "true", matchIfMissing = true)
public class FeedSoftDeleteCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedSoftDeleteCleanupScheduler.class);

    private final FeedPostRepository postRepository;
    private final FeedPostCommentRepository commentRepository;
    private final int restoreWindowDays;

    public FeedSoftDeleteCleanupScheduler(
            FeedPostRepository postRepository,
            FeedPostCommentRepository commentRepository,
            @Value("${app.feed.cleanup.restore-window-days:30}") int restoreWindowDays) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        // Defensive clamp: a misconfigured 0 would reap everything on
        // every run. Mirrors FeedPostService's restoreWindowDays clamp.
        this.restoreWindowDays = Math.max(1, restoreWindowDays);
    }

    @Scheduled(cron = "${app.feed.cleanup.cron:0 45 3 * * *}",
            zone = "${app.feed.cleanup.zone:UTC}")
    @Transactional
    public void purgeExpiredSoftDeletes() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(restoreWindowDays);
        int posts = postRepository.hardDeletePostsSoftDeletedBefore(cutoff);
        int comments = commentRepository.hardDeleteCommentsSoftDeletedBefore(cutoff);
        if (posts > 0 || comments > 0) {
            log.info("feed-cleanup purged: posts={}, comments={}, cutoff={}, windowDays={}",
                    posts, comments, cutoff, restoreWindowDays);
        }
    }
}
