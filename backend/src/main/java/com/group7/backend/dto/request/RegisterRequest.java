package com.group7.backend.dto.request;

import com.group7.backend.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registration payload")
public class RegisterRequest {
    @NotBlank(message = "First name is required")
    @Schema(description = "First name")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "Last name")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Email address", example = "user@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @ValidPassword
    @Schema(description = "Password (validated by backend rules)")
    private String password;

    @Schema(description = "Whether the user registers as a mentor")
    private Boolean isMentor;

    // Honeypot field (#345): legitimate UIs never render this. Bots that
    // auto-fill every input populate it and get rejected with a generic 400.
    // Hidden from Swagger so the public contract doesn't advertise it.
    @Schema(hidden = true)
    private String website;

    // Backend-issued HMAC-signed render timestamp round-tripped through a
    // hidden field. Verified on submit to enforce the minimum human-typing
    // duration (#345).
    @Schema(hidden = true)
    private String formToken;
}
