package com.group7.backend.dto.request;

import com.group7.backend.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reset password payload")
public class ResetPasswordRequest {
    @Schema(description = "Email address", example = "user@example.com")
    private String email;

    @NotBlank(message = "Token is required")
    @Schema(description = "Password reset token")
    private String token;

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(description = "New password")
    private String newPassword;
}
