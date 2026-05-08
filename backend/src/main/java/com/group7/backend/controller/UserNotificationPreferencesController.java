package com.group7.backend.controller;

import com.group7.backend.dto.request.NotificationPreferencesUpdateRequest;
import com.group7.backend.dto.response.UserNotificationPreferencesResponse;
import com.group7.backend.service.UserNotificationPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/notification-preferences")
@Tag(name = "Push Notifications",
        description = "Per-user push category toggles (#136).")
public class UserNotificationPreferencesController {

    private final UserNotificationPreferencesService service;

    public UserNotificationPreferencesController(UserNotificationPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get notification preferences",
            description = "Returns the caller's category-level push toggles. The row is "
                    + "lazily created with all toggles enabled on first access.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences returned",
                    content = @Content(schema = @Schema(implementation = UserNotificationPreferencesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<UserNotificationPreferencesResponse> get(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(service.get(userId));
    }

    @PatchMapping
    @Operation(summary = "Update notification preferences",
            description = "Partial update — omitted fields are left unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated",
                    content = @Content(schema = @Schema(implementation = UserNotificationPreferencesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<UserNotificationPreferencesResponse> update(
            @Valid @RequestBody NotificationPreferencesUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(service.update(userId, request));
    }
}
