package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/feed/posts/{id}/comments} and
 * {@code PATCH /api/feed/comments/{id}} (#347). Body is required on
 * create; PATCH treats null body as "leave alone" but the service
 * rejects null on the create path.
 */
@Schema(description = "Comment body — required on create.")
public record FeedCommentRequest(
        @Schema(description = "Comment body, up to 1000 chars", example = "Great post!")
        @NotBlank
        @Size(max = 1000)
        String body
) {
}
