package com.group7.backend.controller;

import com.group7.backend.dto.request.RegisterDeviceRequest;
import com.group7.backend.dto.response.UserDeviceResponse;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.service.UserDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/devices")
@Tag(name = "Push Notifications",
        description = "FCM device token registration for the authenticated user (#136).")
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    public UserDeviceController(UserDeviceService userDeviceService) {
        this.userDeviceService = userDeviceService;
    }

    @PostMapping
    @Operation(summary = "Register a device token",
            description = "Registers an FCM token for push delivery. Idempotent on token: "
                    + "duplicate registrations refresh lastSeenAt. Per-user cap (default 5) "
                    + "is enforced with oldest-first eviction.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token registered",
                    content = @Content(schema = @Schema(implementation = UserDeviceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<UserDeviceResponse> register(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        UserDevice device = userDeviceService.register(userId, request.getToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDeviceResponse.from(device));
    }

    @DeleteMapping("/{token}")
    @Operation(summary = "Unregister a device token",
            description = "Removes the given token if it belongs to the authenticated user. "
                    + "Idempotent: 204 either way.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Token removed (or never existed)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Void> unregister(
            @Parameter(description = "FCM token to unregister") @PathVariable String token,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        userDeviceService.unregister(userId, token);
        return ResponseEntity.noContent().build();
    }
}
