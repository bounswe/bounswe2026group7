package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Single feed-post payload returned by {@code GET / POST / PATCH
 * /api/feed/posts/{id}} (#348).
 *
 * <p>{@code authorFirstName} is denormalised so the UI can render the
 * post header without a follow-up fetch. {@code isAuthor} and
 * {@code isEdited} are computed per request: {@code isAuthor} compares
 * the authenticated viewer to {@code authorId}; {@code isEdited} is
 * {@code true} when {@code updatedAt} is more than one second after
 * {@code createdAt} (1-second tolerance handles same-transaction clock
 * skew on create).
 *
 * <p>Hashtags are returned in alphabetical order (server-side
 * {@code @OrderBy}) so the UI can rely on a deterministic ordering
 * regardless of insertion order or storage backend.
 */
@Schema(description = "Authored social-feed post with its hashtags and viewer-relative flags.")
public record FeedPostResponse(
        @Schema(description = "Post id", example = "42")
        Long id,

        @Schema(description = "Author user id", example = "17")
        Long authorId,

        @Schema(description = "Author first name (denormalised for the UI)", example = "Ada")
        String authorFirstName,

        @Schema(description = "Post body", example = "Excited to share thoughts on data science.")
        String body,

        @Schema(description = "Hashtags attached to the post (alphabetical order)",
                example = "[\"datascience\", \"nlp\"]")
        List<String> hashtags,

        @Schema(description = "Server-side creation timestamp")
        OffsetDateTime createdAt,

        @Schema(description = "Server-side last-update timestamp")
        OffsetDateTime updatedAt,

        @Schema(description = "True if updatedAt is meaningfully after createdAt")
        boolean isEdited,

        @Schema(description = "True if the viewer is the post author")
        boolean isAuthor,

        @Schema(description = "Image attachments on the post, in author-specified order. Empty when "
                + "the post has no media (#485). Each downloadUrl resolves to the feed-scoped "
                + "/api/uploads/feed-media/{id} endpoint, which requires authentication but no "
                + "per-user ACL.")
        List<AttachmentSummary> attachments,

        @Schema(description = "True if the authenticated viewer has liked this post. Always "
                + "false for anonymous reads. Lets the UI render the heart-icon toggle state "
                + "without a follow-up GET /interactions call per item.")
        boolean viewerHasLiked,

        @Schema(description = "True if the authenticated viewer has bookmarked this post. "
                + "Always false for anonymous reads. Lets the UI render the bookmark-icon "
                + "toggle state without a follow-up GET /interactions call per item.")
        boolean viewerHasBookmarked
) {
}
