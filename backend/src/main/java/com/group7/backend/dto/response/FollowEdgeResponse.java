package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body of {@code POST /api/users/{id}/follow}. Identity-only —
 * carries the directional edge {@code (followerId, followeeId)} without a
 * {@code createdAt} timestamp. The HTTP status code carries the
 * created-vs-already-existed signal: {@code 201} for a fresh insert,
 * {@code 200} for an idempotent re-follow.
 *
 * <p>The timestamp is deliberately not surfaced here. Including it would
 * force a same-transaction read after the native upsert, which interacts
 * unpleasantly with Hibernate's L1 cache. The timestamp is exposed in the
 * follower / following list payloads where it actually matters for sort
 * order (covered by the {@code UserSummary} list endpoints, which sort by
 * recency at the SQL layer).
 */
@Schema(description = "Result of a follow / unfollow action — directional edge identity only.")
public record FollowEdgeResponse(
        @Schema(description = "User id of the follower (caller)", example = "42")
        Long followerId,
        @Schema(description = "User id of the followee (target)", example = "17")
        Long followeeId
) {
}
