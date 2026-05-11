package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/users/me/keyword-mutes}. The service
 * normalises (trim, lowercase) and rejects keywords with characters
 * outside {@code [a-z0-9 -]} after normalisation.
 */
@Schema(description = "A single keyword to mute. Server lowercases and rejects bad characters.")
public record KeywordMuteRequest(
        @Schema(description = "Keyword to mute (1–120 chars).", example = "crypto")
        @NotBlank
        @Size(max = 120)
        String keyword
) {
}
