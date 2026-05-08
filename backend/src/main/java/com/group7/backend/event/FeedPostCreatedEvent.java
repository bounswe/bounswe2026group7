package com.group7.backend.event;

import java.time.OffsetDateTime;

/**
 * Published when a {@link com.group7.backend.entity.FeedPost} commits
 * (#349). {@link FeedFanoutListener} subscribes via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} and fans out a slim
 * STOMP push to every follower's per-user topic.
 *
 * <p><b>All-values record, no entity references.</b> The listener runs
 * via {@code @Async} on a different thread than the publisher; carrying
 * a {@code FeedPost} entity across the thread hop would surface
 * {@code LazyInitializationException} on the lazy hashtag collection.
 * The four fields here are everything the slim
 * {@link com.group7.backend.dto.response.FeedPostPushPayload} needs;
 * clients fetch the full {@code FeedPostResponse} via
 * {@code GET /api/feed/posts/{id}} when they want body / hashtags.
 */
public record FeedPostCreatedEvent(
        Long postId,
        Long authorId,
        String authorFirstName,
        OffsetDateTime createdAt
) {
}
