package com.group7.backend.dto.request;

import com.group7.backend.dto.feed.FeedPostLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

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
        List<@Size(max = FeedPostLimits.MAX_HASHTAG_LENGTH) String> hashtags,

        @Schema(description = "Optional image-attachment ids returned by POST /api/messages/attachments. "
                + "Max 4 per post. Each id must belong to the post's author and have an image "
                + "content type (image/jpeg, image/png, image/gif, image/webp). Null or empty "
                + "creates a text-only post.",
                example = "[\"5b9c1f0a-2c2c-4cf2-8f1d-9d4f1a0e6b5e\"]")
        @Size(max = 4, message = "A post can carry at most 4 attachments")
        List<@NotNull UUID> attachmentIds
) {
}
