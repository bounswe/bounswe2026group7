package com.group7.backend.dto.request;

import com.group7.backend.dto.feed.FeedPostLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code PATCH /api/feed/posts/{id}} (#348). Both
 * fields are optional ({@code null} = "leave alone" — the project's
 * partial-update convention as used by {@code MeetingService} and
 * {@code UserService}). When supplied, fields go through the same caps
 * and normalisation as the create path.
 *
 * <p>{@code body} cannot use {@code @NotBlank} here because {@code null}
 * means "don't touch"; if a non-null body is supplied, the service
 * rejects blank input with {@link IllegalArgumentException} → 400.
 */
@Schema(description = "Partial update for a feed post. Null fields are not modified; non-null fields are validated and applied.")
public record UpdateFeedPostRequest(
        @Schema(description = "New body, or null to leave unchanged. Up to 2000 characters when present.")
        @Size(max = FeedPostLimits.MAX_BODY_LENGTH)
        String body,

        @Schema(description = "New hashtags, or null to leave unchanged. Server applies the same normalisation as create.")
        @Size(max = FeedPostLimits.MAX_HASHTAGS)
        List<@Size(max = FeedPostLimits.MAX_HASHTAG_LENGTH) String> hashtags
) {
}
