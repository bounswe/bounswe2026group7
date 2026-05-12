package com.group7.backend.event;

import java.time.OffsetDateTime;

/**
 * Published when a {@link com.group7.backend.entity.FeedPostShare} commits
 * with {@code is_repost = TRUE} (i.e., a repost or quote-share via
 * {@code POST /api/feed/posts/{id}/reposts}). {@link FeedFanoutListener}
 * subscribes via {@code @TransactionalEventListener(AFTER_COMMIT)} and
 * fans out a slim STOMP push to every follower of the sharer.
 *
 * <p>Silent shares (the existing {@code /share} endpoint, which writes
 * {@code is_repost = FALSE}) do <b>not</b> publish this event — they
 * only emit the {@code FEED_SHARE} notification side-effect and never
 * fan out to followers.
 *
 * <p>Mirrors {@link FeedPostCreatedEvent}'s all-values record shape so
 * the {@code @Async} listener never crosses a thread boundary with a
 * managed entity (no {@code LazyInitializationException} risk).
 */
public record FeedPostSharedEvent(
        Long shareId,
        Long postId,
        Long sharerId,
        String sharerFirstName,
        String commentary,
        OffsetDateTime sharedAt
) {
}
