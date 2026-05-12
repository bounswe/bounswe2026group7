package com.group7.backend.controller;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.service.ConversationService;
import com.group7.backend.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant-visible ADMIN_DIRECT message surface. Unlike the admin-only
 * send endpoint, this lets the non-admin side discover, read, and reply to
 * the same conversation in their Messages tab.
 */
@RestController
@RequestMapping("/api/conversations/admin-direct/{otherUserId}/messages")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin Direct Messages",
        description = "Admin-to-user direct message thread for both participants")
public class AdminDirectMessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public AdminDirectMessageController(MessageService messageService,
                                        ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @PostMapping
    @Operation(summary = "Send a message in an admin direct thread",
            description = "Sends a message in an ADMIN_DIRECT conversation. The thread is "
                    + "created on first call as long as one side of the pair is an admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message sent",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Self-DM or pair without an admin",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<MessageResponse> send(
            @PathVariable Long otherUserId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long senderId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForAdminDirect(
                senderId, otherUserId);
        MessageResponse created = messageService.send(senderId, conversation.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List admin direct messages",
            description = "Returns paginated ADMIN_DIRECT message history with the specified "
                    + "other participant, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated messages"),
            @ApiResponse(responseCode = "400", description = "Self-DM or pair without an admin",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<Page<MessageResponse>> list(
            @PathVariable Long otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForAdminDirect(
                requesterId, otherUserId);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.list(requesterId, conversation.getId(), pageable));
    }

    @PatchMapping("/read")
    @Operation(summary = "Mark all admin direct messages as read",
            description = "Marks every unread ADMIN_DIRECT message in this thread that was "
                    + "not sent by the authenticated user as read. Returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Read receipts updated",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Self-DM or pair without an admin",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<Void> markAllRead(
            @PathVariable Long otherUserId,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForAdminDirect(
                requesterId, otherUserId);
        messageService.markAllRead(requesterId, conversation.getId());
        return ResponseEntity.noContent().build();
    }
}
