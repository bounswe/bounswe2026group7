package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregate counts + viewer-relative state for a single post (#347).
 * Returned as the response of toggle endpoints (like / bookmark) and
 * embedded in the post-detail surfaces so the UI renders the toggle
 * state without a follow-up call.
 */
@Schema(description = "Interaction counts and viewer-relative toggle state for a feed post.")
public record FeedPostInteractionState(
        @Schema(description = "Total number of likes on the post", example = "42")
        long likeCount,

        @Schema(description = "Total number of non-deleted comments", example = "7")
        long commentCount,

        @Schema(description = "Total number of shares (events)", example = "3")
        long shareCount,

        @Schema(description = "Total number of bookmarks", example = "12")
        long bookmarkCount,

        @Schema(description = "True if the viewer has liked this post")
        boolean viewerHasLiked,

        @Schema(description = "True if the viewer has bookmarked this post")
        boolean viewerHasBookmarked
) {
}
