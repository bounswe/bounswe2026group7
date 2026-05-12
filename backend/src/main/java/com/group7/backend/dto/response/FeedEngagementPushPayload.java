package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Slim engagement-update push payload broadcast over the
 * {@code /topic/feed.{userId}} STOMP destination so visible feed cards
 * can update like / comment / share counts without polling.
 *
 * <p>Discriminator: this payload has neither {@code authorId} nor
 * {@code sharerId} (cf. {@link FeedPostPushPayload} /
 * {@link FeedSharePushPayload}); the post is identified by id only.
 *
 * <p>Routing: the broadcast reaches every follower of the post's author
 * <em>plus</em> the author themselves, so the author also sees live
 * counts on their own post. Non-follower viewers (search, For-You
 * discovery, direct link) fall back to polling on next interaction.
 *
 * <p>Counts are <em>authoritative</em>, not deltas — the frontend sets
 * {@code likeCount = N} (rather than {@code += 1}), so a dropped STOMP
 * frame self-heals on the next received one.
 */
@Schema(description = "Engagement-counts push over /topic/feed.{userId} STOMP. " +
        "Authoritative absolute counts, not deltas — frontend sets the value to self-heal dropped frames.")
public record FeedEngagementPushPayload(
        @Schema(description = "Post id whose counts changed", example = "42")
        Long postId,

        @Schema(description = "Total likes on the post", example = "12")
        long likeCount,

        @Schema(description = "Total non-deleted comments on the post", example = "3")
        long commentCount,

        @Schema(description = "Total shares + reposts of the post", example = "5")
        long shareCount,

        @Schema(description = "Server-side timestamp of this engagement change")
        OffsetDateTime updatedAt
) {
}
