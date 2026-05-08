package com.group7.backend.event;

import com.group7.backend.dto.feed.FeedTopics;
import com.group7.backend.dto.response.FeedPostPushPayload;
import com.group7.backend.repository.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * Fans out a real-time STOMP push to every follower's per-user topic
 * AFTER a {@link com.group7.backend.entity.FeedPost} create transaction
 * commits (#349). Satisfies NFR 2.3.4 (feed updates reflected within
 * 5 seconds of post creation).
 *
 * <p><b>Why AFTER_COMMIT.</b> A synchronous fanout from the controller
 * would block the HTTP response on N broadcast sends and couple the
 * post-creation transaction to broker success. {@link
 * TransactionPhase#AFTER_COMMIT} keeps the create endpoint fast,
 * ensures fanout fires only for committed posts (rolled-back creates
 * produce zero broadcasts), and matches the existing notification
 * path's failure isolation.
 *
 * <p><b>Why {@code @Async}.</b> Fanout to a power-author's followers can
 * span hundreds of broadcasts; running on a worker thread keeps the
 * commit thread free for the next request. {@code fallbackExecution}
 * stays at the default {@code false} — if the publisher is ever invoked
 * outside a transaction the listener stays silent (fail-closed) rather
 * than broadcasting a phantom event.
 *
 * <p><b>Race: F follows the author while the fanout is in progress.</b>
 * The follower set is snapshotted at the start of the listener, so F is
 * not in the snapshot and misses this single push. Acceptable: F's next
 * pull-refresh / unread-count call recovers the post immediately.
 *
 * <p><b>Top-level try/catch.</b> The codebase has no
 * {@code AsyncUncaughtExceptionHandler}, so a throw from anywhere
 * outside {@code broadcastTo}'s per-recipient try/catch would be silently
 * swallowed by Spring's default {@code SimpleAsyncTaskExecutor}. Wrap
 * the entire body so the failure surfaces in logs.
 */
@Component
public class FeedFanoutListener {

    private static final Logger log = LoggerFactory.getLogger(FeedFanoutListener.class);

    private final FollowRepository followRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public FeedFanoutListener(FollowRepository followRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.followRepository = followRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedPostCreated(FeedPostCreatedEvent event) {
        try {
            Set<Long> followers = followRepository.findFollowerIdsByFolloweeId(event.authorId());
            if (followers.isEmpty()) {
                return;
            }
            int delivered = broadcastTo(followers, toPayload(event), event.postId());
            log.info("Feed fanout: postId={}, authorId={}, recipients={}, delivered={}",
                    event.postId(), event.authorId(), followers.size(), delivered);
        } catch (RuntimeException ex) {
            log.error("Feed fanout aborted: postId={}, authorId={}: {}",
                    event.postId(), event.authorId(), ex.getMessage(), ex);
        }
    }

    private static FeedPostPushPayload toPayload(FeedPostCreatedEvent event) {
        return new FeedPostPushPayload(
                event.postId(), event.authorId(), event.authorFirstName(), event.createdAt());
    }

    private int broadcastTo(Set<Long> recipients, FeedPostPushPayload payload, Long postId) {
        int delivered = 0;
        for (Long recipientId : recipients) {
            try {
                messagingTemplate.convertAndSend(FeedTopics.FEED_PREFIX + recipientId, payload);
                delivered++;
            } catch (RuntimeException ex) {
                log.warn("Feed fanout failed: postId={}, recipient={}: {}",
                        postId, recipientId, ex.getMessage());
            }
        }
        return delivered;
    }
}
