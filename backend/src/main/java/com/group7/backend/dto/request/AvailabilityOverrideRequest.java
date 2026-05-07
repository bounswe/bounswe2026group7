package com.group7.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.group7.backend.entity.AvailabilityOverrideKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Request shape for adding a one-off availability override to the
 * authenticated mentor's schedule. {@code mentorId} is intentionally absent
 * — it comes from the JWT credentials, not the request body, so a mentor
 * can only mutate their own data.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One-off availability override (AVAILABLE add or UNAVAILABLE block)")
public class AvailabilityOverrideRequest {

    @NotNull(message = "kind is required")
    @Schema(description = "Whether this period adds availability or blocks it",
            example = "UNAVAILABLE")
    private AvailabilityOverrideKind kind;

    @NotNull(message = "startAt is required")
    @Schema(description = "Inclusive start of the override range (timestamptz)",
            example = "2026-06-10T09:00:00Z")
    private OffsetDateTime startAt;

    @NotNull(message = "endAt is required")
    @Schema(description = "Exclusive end of the override range (timestamptz)",
            example = "2026-06-20T17:00:00Z")
    private OffsetDateTime endAt;

    /**
     * Cross-field check: range must be non-empty (end strictly after start).
     * {@code @JsonIgnore} keeps this off the wire format. Returns {@code true}
     * when either bound is null so the per-field {@link NotNull} messages
     * surface first instead of being masked by a confusing range error.
     */
    @AssertTrue(message = "endAt must be after startAt")
    @JsonIgnore
    public boolean isRangeValid() {
        if (startAt == null || endAt == null) {
            return true;
        }
        return endAt.isAfter(startAt);
    }
}
