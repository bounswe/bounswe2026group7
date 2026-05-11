package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lighter feed-post payload for the list endpoints in #350 (For-You,
 * Following, search). Drops the {@code isAuthor} / {@code isEdited} /
 * {@code updatedAt} fields from {@link FeedPostResponse} — they're not
 * needed at the list level and skipping them keeps the JOIN cost lower.
 *
 * <p>{@code likeCount} and {@code commentCount} are placeholders set to
 * 0 until {@code #347} (interactions) lands. Adding them to the contract
 * now means #347 only has to populate them, not extend the DTO.
 *
 * <p>For full state (edit indicators, viewer-relative flags), the
 * {@code GET /api/feed/posts/{id}} detail endpoint stays the source of
 * truth.
 */
@Schema(description = "Slim feed-post payload for list endpoints (For-You, Following, search).")
public record FeedPostListItem(
        @Schema(description = "Post id", example = "42")
        Long id,

        @Schema(description = "Author user id", example = "17")
        Long authorId,

        @Schema(description = "Author first name (denormalised for the UI)", example = "Ada")
        String authorFirstName,

        @Schema(description = "Post body", example = "Excited to share thoughts on data science.")
        String body,

        @Schema(description = "Hashtags attached to the post (alphabetical)", example = "[\"datascience\", \"nlp\"]")
        List<String> hashtags,

        @Schema(description = "Server-side creation timestamp")
        OffsetDateTime createdAt,

        @Schema(description = "Number of likes (placeholder 0 until #347)", example = "0")
        long likeCount,

        @Schema(description = "Number of comments (placeholder 0 until #347)", example = "0")
        long commentCount,

        @Schema(description = "Short codes explaining why the For-You ranker placed this post. "
                + "Empty for non-ranked feeds (Following, search, author profile). "
                + "Codes from the advanced ranker are namespaced with a `feed:` prefix; the "
                + "frontend strips the prefix and maps each code to a localized chip.",
                example = "[\"feed:semantic-match:0.82\", \"feed:fresh\", \"feed:follow-boost\"]")
        List<String> factors
) {
}
