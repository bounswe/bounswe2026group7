package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login payload")
public class LoginRequest {
    @Schema(description = "Email address", example = "user@example.com")
    private String email;

    @Schema(description = "Password")
    private String password;
}
