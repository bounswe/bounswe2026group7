package com.group7.backend.dto.response;

import com.group7.backend.entity.UserKeywordMute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "A single muted keyword for the authenticated user.")
public record KeywordMuteResponse(
        @Schema(description = "Surrogate id used in the DELETE endpoint path.", example = "17")
        Long id,

        @Schema(description = "The normalised (lowercase) keyword.", example = "crypto")
        String keyword,

        @Schema(description = "Server-side creation timestamp.")
        OffsetDateTime createdAt
) {
    public static KeywordMuteResponse from(UserKeywordMute mute) {
        return new KeywordMuteResponse(mute.getId(), mute.getKeyword(), mute.getCreatedAt());
    }
}
