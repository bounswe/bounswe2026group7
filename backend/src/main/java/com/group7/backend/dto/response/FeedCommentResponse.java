package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Single comment payload (#347). Soft-deleted comments are returned
 * with {@code body == null} and {@code isDeleted == true} so the UI
 * can render a "[comment removed]" placeholder while preserving thread
 * structure (parent-comment-id is reserved for future threading; the
 * controller renders this placeholder regardless).
 */
@Schema(description = "Comment on a feed post; deleted comments surface as placeholders.")
public record FeedCommentResponse(
        @Schema(description = "Comment id", example = "101")
        Long id,

        @Schema(description = "Post id", example = "42")
        Long postId,

        @Schema(description = "Author user id; null when the author was deleted", nullable = true)
        Long authorId,

        @Schema(description = "Author first name; null when deleted or unavailable", nullable = true)
        String authorFirstName,

        @Schema(description = "Comment body; null when soft-deleted", nullable = true)
        String body,

        @Schema(description = "Server-side creation timestamp")
        OffsetDateTime createdAt,

        @Schema(description = "Server-side last-update timestamp")
        OffsetDateTime updatedAt,

        @Schema(description = "True if updatedAt is meaningfully after createdAt")
        boolean isEdited,

        @Schema(description = "True if the viewer authored this comment")
        boolean isAuthor,

        @Schema(description = "True if the comment was soft-deleted")
        boolean isDeleted
) {
}
