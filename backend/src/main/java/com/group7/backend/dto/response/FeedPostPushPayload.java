package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Slim feed-post push payload broadcast over the
 * {@code /topic/feed.{userId}} STOMP destination (#349). Clients fetch
 * the full {@link FeedPostResponse} via {@code GET /api/feed/posts/{id}}
 * when they need body / hashtags / counts — the slim shape keeps the
 * fanout payload bounded regardless of post body length.
 */
@Schema(description = "Slim feed-post push payload over /topic/feed.{userId} STOMP (#349). " +
        "Clients fetch the full FeedPostResponse via GET /api/feed/posts/{id} when they need body / hashtags.")
public record FeedPostPushPayload(

        @Schema(description = "Post id", example = "42")
        Long postId,

        @Schema(description = "Author user id", example = "17")
        Long authorId,

        @Schema(description = "Author first name (denormalised for the UI)", example = "Ada")
        String authorFirstName,

        @Schema(description = "Server-side creation timestamp")
        OffsetDateTime createdAt
) {
}
