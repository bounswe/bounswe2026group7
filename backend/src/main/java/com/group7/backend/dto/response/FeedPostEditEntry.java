package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One entry in the edit history of a feed post (#487). Returned by
 * {@code GET /api/feed/posts/{id}/history} in reverse-chronological
 * order. Each entry captures the post's body and hashtag set as they
 * were before a particular edit was applied, so a UI can reconstruct
 * the timeline by walking the entries oldest-first.
 *
 * <p>{@link #editorId} is the snapshot editor's user id only — display
 * names are not denormalised here. The client batch-resolves names via
 * the existing user-summary endpoint when it needs to render them. This
 * keeps history reads cheap (no per-entry user lookup) and avoids
 * stale-name bugs when a user updates their profile after editing a post.
 */
@Schema(description = "One edit-history entry for a feed post.")
public record FeedPostEditEntry(
        @Schema(description = "History entry id", example = "37")
        Long id,

        @Schema(description = "User id of the editor at edit time, or null if the editor's account was removed",
                example = "17")
        Long editorId,

        @Schema(description = "Post body as it was before this edit", example = "Original body text")
        String previousBody,

        @Schema(description = "Hashtags attached to the post before this edit", example = "[\"datascience\", \"nlp\"]")
        List<String> previousHashtags,

        @Schema(description = "Server-side timestamp of the edit")
        OffsetDateTime editedAt
) {
}
