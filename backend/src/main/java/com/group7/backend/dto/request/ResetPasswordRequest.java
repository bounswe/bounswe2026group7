package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reset password payload")
public class ResetPasswordRequest {
    @Schema(description = "Email address", example = "user@example.com")
    private String email;
    @Schema(description = "Password reset token")
    private String token;
    @Schema(description = "New password")
    private String newPassword;
}
