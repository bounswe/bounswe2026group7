package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/feed/posts/{id}/reposts}. The body
 * field disambiguates the two sharing modes the endpoint supports:
 * <ul>
 *   <li>{@code null} or blank → bare repost (no commentary).</li>
 *   <li>non-blank up to 2000 characters → quote-share with commentary.</li>
 * </ul>
 *
 * <p>The whole request body is optional. {@code {}}, missing JSON body,
 * and {@code {"body": null}} all deserialise to a {@code body = null}
 * record and produce a bare repost. The service layer additionally
 * normalises blank input ({@code "   "}) to {@code null} so the
 * non-blank DB CHECK constraint never fires.
 */
@Schema(description = "Repost or quote-share payload. Null/blank body = bare repost; non-blank body = quote-share.")
public record CreateRepostRequest(
        @Schema(description = "Optional commentary attached to a quote-share. Capped at 2000 characters.",
                example = "Great take on this — fully agree.")
        @Size(max = 2000) String body
) {
    public static CreateRepostRequest empty() {
        return new CreateRepostRequest(null);
    }
}
