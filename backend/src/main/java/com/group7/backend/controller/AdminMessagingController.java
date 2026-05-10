package com.group7.backend.controller;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.service.ConversationService;
import com.group7.backend.service.MessageService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only messaging surface (#280): direct messages to any user (no
 * mentorship required) and broadcast messages to every admin via the
 * singleton {@code ADMIN_BROADCAST} conversation.
 *
 * <p>Mirrors {@code MentorPairMessageController} in shape: resolve the
 * conversation via {@link ConversationService}, then delegate the actual
 * message insert to {@link MessageService}. The role gate is enforced by
 * {@code @PreAuthorize("hasRole('ADMIN')")} at class level; the conversation
 * service additionally enforces "at least one admin in the pair" for
 * defense in depth.
 */
@RestController
@RequestMapping("/api/admin/messages")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Messages",
        description = "Admin DMs to any user and broadcasts to every admin (#280)")
public class AdminMessagingController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public AdminMessagingController(MessageService messageService,
                                    ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @PostMapping("/direct/{userId}")
    @Operation(summary = "Send an admin DM to a user",
            description = "Creates an ADMIN_DIRECT conversation with the target user on first "
                    + "call and sends the message. No mentorship is required between admin "
                    + "and user; the conversation is reused on subsequent calls.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message sent",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Self-DM or non-admin sender",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Target user not found",
                    content = @Content)
    })
    public ResponseEntity<MessageResponse> direct(
            @Parameter(description = "Target user id") @PathVariable Long userId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForAdminDirect(adminId, userId);
        MessageResponse created = messageService.send(adminId, conversation.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast to all admins",
            description = "Posts the message into the singleton ADMIN_BROADCAST conversation. "
                    + "Every current admin is added as a participant on each broadcast send, "
                    + "so newly-promoted admins start seeing broadcasts on the next post "
                    + "without a separate registration step.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Broadcast sent",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin",
                    content = @Content)
    })
    public ResponseEntity<MessageResponse> broadcast(
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateAdminBroadcast();
        MessageResponse created = messageService.send(adminId, conversation.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
