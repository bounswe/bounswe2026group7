package com.group7.backend.dto.response;

import com.group7.backend.entity.AvailabilityOverride;
import com.group7.backend.entity.AvailabilityOverrideKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Wire shape for a single availability override. The mentor reference is
 * not exposed because the request and listing endpoints are already scoped
 * by mentor (own credentials for writes, path variable for reads).
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One-off availability override (AVAILABLE add or UNAVAILABLE block)")
public class AvailabilityOverrideResponse {

    @Schema(description = "Override ID", example = "42")
    private Long id;

    @Schema(description = "Whether this period adds availability or blocks it",
            example = "UNAVAILABLE")
    private AvailabilityOverrideKind kind;

    @Schema(description = "Inclusive start of the override range",
            example = "2026-06-10T09:00:00Z")
    private OffsetDateTime startAt;

    @Schema(description = "Exclusive end of the override range",
            example = "2026-06-20T17:00:00Z")
    private OffsetDateTime endAt;

    public static AvailabilityOverrideResponse from(AvailabilityOverride entity) {
        AvailabilityOverrideResponse r = new AvailabilityOverrideResponse();
        r.setId(entity.getId());
        r.setKind(entity.getKind());
        r.setStartAt(entity.getStartAt());
        r.setEndAt(entity.getEndAt());
        return r;
    }
}
