package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Basic user response")
public class UserResponse {
    @Schema(description = "User id", example = "1")
    private Long id;
    @Schema(description = "First name")
    private String firstName;
    @Schema(description = "Last name")
    private String lastName;
    @Schema(description = "Email address")
    private String email;
    @Schema(description = "Profile photo URL")
    private String profilePhoto;
    @Schema(description = "Whether email is verified")
    private Boolean isEmailVerified;
    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;
    @Schema(description = "Role", example = "MENTEE")
    private String role;
    @Schema(description = "Average mentor rating (1.0–5.0). Populated only on profile-detail "
                        + "endpoints (#237); null on list / search responses and for non-mentor "
                        + "users.", example = "4.6")
    private Double averageRating;
    @Schema(description = "Number of ratings the mentor has received (#237). 0 when "
                        + "averageRating is null.", example = "12")
    private Long ratingCount;
}
