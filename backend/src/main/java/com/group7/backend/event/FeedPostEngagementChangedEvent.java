package com.group7.backend.event;

import java.time.OffsetDateTime;

/**
 * Published whenever an engagement mutation on a feed post changes the
 * post's visible counts (like / comment / share / repost) <em>or</em>
 * fires as a heartbeat for downstream views (comment-like).
 * {@link FeedFanoutListener} subscribes via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} and broadcasts a
 * {@link com.group7.backend.dto.response.FeedEngagementPushPayload} to
 * the author's followers plus the author themselves.
 *
 * <p>Carries the post id and author id so the listener can resolve
 * recipients without re-loading the entity, plus the post-flush
 * authoritative counts so the wire payload is a self-contained snapshot
 * (frontend assigns absolute values rather than computing deltas).
 *
 * <p>Mirrors {@link FeedPostCreatedEvent} / {@link FeedPostSharedEvent}'s
 * all-values record shape so the {@code @Async} listener never crosses
 * a thread boundary with a managed entity.
 */
public record FeedPostEngagementChangedEvent(
        Long postId,
        Long authorId,
        long likeCount,
        long commentCount,
        long shareCount,
        OffsetDateTime updatedAt
) {
}
