package com.group7.backend.controller;

import com.group7.backend.dto.response.NotificationResponse;
import com.group7.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notification endpoints")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List notifications", description = "Returns notifications for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification list",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class))))
    })
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @Parameter(description = "When true, returns only unread notifications")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(notificationService.getNotifications(userId, unreadOnly));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark as read", description = "Marks one notification as read for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked read",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found", content = @Content)
    })
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(notificationService.markAsRead(userId, id));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all as read", description = "Marks all unread notifications as read for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Notifications updated")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        int updatedCount = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("updatedCount", updatedCount));
    }
}