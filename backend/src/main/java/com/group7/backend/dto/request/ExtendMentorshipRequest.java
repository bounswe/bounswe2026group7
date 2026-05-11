package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for extending an active mentorship's duration (#237). "
                    + "Mentor-only. Adds the requested months to end_date.")
public class ExtendMentorshipRequest {

    private static final Set<Integer> ALLOWED = Set.of(1, 3, 6);

    @NotNull(message = "additionalMonths is required")
    @Schema(description = "Months to add to the current end_date. Must be 1, 3, or 6 — "
                        + "matching the durations allowed at acceptance time.",
            example = "3")
    private Integer additionalMonths;

    @jakarta.validation.constraints.AssertTrue(message = "additionalMonths must be 1, 3, or 6")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    public boolean isAdditionalMonthsAllowed() {
        return additionalMonths != null && ALLOWED.contains(additionalMonths);
    }
}
