package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
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

    @Size(max = 2048, message = "Profile photo URL must not exceed 2048 characters")
    @Pattern(regexp = "^https?://.*", message = "Profile photo must be a valid HTTP(S) URL")
    @Schema(description = "Profile photo URL", example = "https://example.com/photo.jpg")
    private String profilePhoto;
}
