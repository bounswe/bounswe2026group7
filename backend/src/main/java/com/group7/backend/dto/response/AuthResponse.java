package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication result")
public class AuthResponse {
    @Schema(description = "JWT token used for authenticated requests", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String sessionToken;

    @Schema(description = "User role", example = "MENTOR")
    private String role;

    @Schema(description = "Authenticated user id", example = "1")
    private Long userId;
}
