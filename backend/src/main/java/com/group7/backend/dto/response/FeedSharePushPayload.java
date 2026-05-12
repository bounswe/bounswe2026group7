package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Slim STOMP push payload broadcast over {@code /topic/feed.{userId}}
 * when a user the recipient follows reposts or quote-shares a post.
 * Mirrors {@link FeedPostPushPayload} for original-post pushes but
 * adds {@code sharerId} / {@code sharerFirstName} / {@code commentary}
 * so the UI can render the "X reposted this" attribution without an
 * extra fetch.
 *
 * <p>Clients still call {@code GET /api/feed/posts/{postId}} for the
 * full post body and hashtags; this payload only carries enough to
 * surface the timeline entry.
 */
@Schema(description = "Slim repost push payload over /topic/feed.{userId} STOMP. " +
        "Commentary is null for bare reposts; non-null for quote-shares.")
public record FeedSharePushPayload(
        @Schema(description = "Share row id (feed_post_shares.id)", example = "501")
        Long shareId,

        @Schema(description = "Reposted post id", example = "42")
        Long postId,

        @Schema(description = "User id of the user who reposted", example = "17")
        Long sharerId,

        @Schema(description = "First name of the sharer (denormalised for the UI)", example = "Ada")
        String sharerFirstName,

        @Schema(description = "Quote-share commentary; null on bare reposts",
                nullable = true,
                example = "Great take on this — fully agree.")
        String commentary,

        @Schema(description = "Server-side share timestamp (feed_post_shares.created_at)")
        OffsetDateTime sharedAt
) {
}
