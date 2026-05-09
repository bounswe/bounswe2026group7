package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response shape for {@code GET /api/feed/unread-count} (#349). Slim by
 * design: a single capped count + a flag the UI uses to decide whether
 * to render the "99+" treatment.
 */
@Schema(description = "Unread feed-post count for the viewer (#349).")
public record FeedUnreadCountResponse(

        @Schema(description = "Capped count, ≤ app.feed.unread.cap (default 99).", example = "12")
        long count,

        @Schema(description = "True iff the raw count exceeds the cap; UI renders '99+'.", example = "false")
        boolean cappedAtMax
) {
}
