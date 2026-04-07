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
}
