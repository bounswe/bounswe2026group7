package com.group7.backend.controller;

import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.ConversationService;
import com.group7.backend.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read surface for the authenticated caller's admin-direct thread with
 * {@code otherUserId}. Mirrors {@link MentorPairMessageController} in shape;
 * the only structural difference is that this controller does NOT expose a
 * POST — writes flow through the admin-only path at
 * {@code POST /api/admin/messages/direct/{userId}}. A non-admin recipient
 * cannot reply through this surface today; widening that is captured as a
 * follow-up if/when the product calls for two-way admin DMs.
 *
 * <p>Conversation lookup is read-only ({@link ConversationService#findAdminDirectByPair}):
 * a recipient opening a thread that has never been DMed gets a clean 404
 * rather than synthesising an empty conversation row.
 */
@RestController
@RequestMapping("/api/conversations/admin-direct/{otherUserId}/messages")
@Tag(name = "Admin Direct Messages",
        description = "Read-side surface for the caller's admin-direct thread")
public class AdminDirectMessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public AdminDirectMessageController(MessageService messageService,
                                        ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @GetMapping
    @Operation(summary = "List admin-direct messages",
            description = "Returns paginated message history of the caller's admin-direct "
                    + "conversation with {otherUserId}, newest first. 404 if no such "
                    + "conversation exists for the pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated messages"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No admin-direct conversation for the pair",
                    content = @Content)
    })
    public ResponseEntity<Page<MessageResponse>> list(
            @PathVariable Long otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = resolveOrThrow(requesterId, otherUserId);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.list(requesterId, conversation.getId(), pageable));
    }

    @PatchMapping("/read")
    @Operation(summary = "Mark all admin-direct messages as read",
            description = "Marks every unread message in this admin-direct thread that was "
                    + "NOT sent by the authenticated caller as read. Returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Read receipts updated",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No admin-direct conversation for the pair",
                    content = @Content)
    })
    public ResponseEntity<Void> markAllRead(
            @PathVariable Long otherUserId,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = resolveOrThrow(requesterId, otherUserId);
        messageService.markAllRead(requesterId, conversation.getId());
        return ResponseEntity.noContent().build();
    }

    private Conversation resolveOrThrow(Long requesterId, Long otherUserId) {
        return conversationService.findAdminDirectByPair(requesterId, otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No admin-direct conversation between users " + requesterId
                                + " and " + otherUserId));
    }
}
