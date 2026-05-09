package com.group7.backend.dto.request;

import com.group7.backend.dto.feed.FeedPostLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/feed/posts} (#348).
 *
 * <p>{@code body} is required and capped at
 * {@link FeedPostLimits#MAX_BODY_LENGTH} characters. {@code hashtags}
 * is optional (null or empty allowed); each element is capped at
 * {@link FeedPostLimits#MAX_HASHTAG_LENGTH}, and the list size is capped
 * at {@link FeedPostLimits#MAX_HASHTAGS}. Server-side normalisation
 * (lowercase, leading-{@code #} strip, regex validate, dedupe) happens
 * after Bean Validation.
 */
@Schema(description = "Create a feed post (text + optional hashtags).")
public record CreateFeedPostRequest(
        @Schema(description = "Post body — required, up to 2000 characters", example = "Excited to share thoughts on data science.")
        @NotBlank
        @Size(max = FeedPostLimits.MAX_BODY_LENGTH)
        String body,

        @Schema(description = "Optional hashtags. Server normalises (lowercase, strip leading '#', dedupe). Max 10.",
                example = "[\"DataScience\", \"#NLP\"]")
        @Size(max = FeedPostLimits.MAX_HASHTAGS)
        List<@Size(max = FeedPostLimits.MAX_HASHTAG_LENGTH) String> hashtags
) {
}
