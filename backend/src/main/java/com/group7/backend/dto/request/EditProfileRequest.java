package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Common profile fields shared by both roles")
public class EditProfileRequest {

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Schema(description = "First name", example = "Bora")
    private String firstName;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Schema(description = "Last name", example = "Sarioglu")
    private String lastName;

    @Size(max = 120, message = "City must be at most 120 characters")
    @Schema(description = "Free-form city name (e.g. 'Istanbul'). Used for the "
            + "case-insensitive 'same city' fallback when coordinates are absent.",
            example = "Istanbul")
    private String city;

    @DecimalMin(value = "-90.0",  message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0",   message = "Latitude must be between -90 and 90")
    @Schema(description = "Latitude in decimal degrees. Must be set together with "
            + "longitude or both omitted (DB CHECK constraint enforces pair-completeness). "
            + "Clients are encouraged to round to 2 decimal places (~1 km precision) "
            + "for privacy.",
            example = "41.01")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0",  message = "Longitude must be between -180 and 180")
    @Schema(description = "Longitude in decimal degrees. See latitude for pair-completeness rule.",
            example = "28.98")
    private Double longitude;

    // profilePhoto is set exclusively via POST /api/users/me/photo
}
