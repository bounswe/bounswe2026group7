package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Register an FCM device token for the authenticated user.")
public class RegisterDeviceRequest {

    @NotBlank
    @Size(max = 512)
    @Schema(description = "FCM registration token from the mobile / web client.")
    private String token;
}
