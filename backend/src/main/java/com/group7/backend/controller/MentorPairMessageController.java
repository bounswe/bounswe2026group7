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
 * Issue-spec REST surface for mentor-to-mentor (peer) chat. Each handler
 * resolves the mentor-pair {@link Conversation} via {@link ConversationService}
 * and then delegates to {@link MessageService} — the data model is
 * conversation-centric but the API surface stays user-id-centric per #284.
 *
 * <p>Class-level {@code @PreAuthorize("hasRole('MENTOR')")} is the role gate
 * for the caller; {@link ConversationService#findOrCreateForMentorPair} is the
 * gate for the target user (must also be a mentor) and for self-pair
 * rejection.
 */
@RestController
@RequestMapping("/api/conversations/mentor-pair/{otherMentorId}/messages")
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Pair Messages",
        description = "Peer messaging between mentors (no mentorship required)")
public class MentorPairMessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public MentorPairMessageController(MessageService messageService,
                                       ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @PostMapping
    @Operation(summary = "Send a message to a peer mentor",
            description = "Sends a chat message to another mentor. The conversation is "
                    + "created on first call (idempotent: both mentors POSTing first "
                    + "resolve to the same conversation row).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message sent",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Self-pair or non-mentor target",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<MessageResponse> send(
            @PathVariable Long otherMentorId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long senderId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorPair(
                senderId, otherMentorId);
        MessageResponse created = messageService.send(senderId, conversation.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List peer-mentor messages",
            description = "Returns paginated message history with the specified peer mentor, "
                    + "newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated messages"),
            @ApiResponse(responseCode = "400", description = "Self-pair or non-mentor target",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<Page<MessageResponse>> list(
            @PathVariable Long otherMentorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorPair(
                requesterId, otherMentorId);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.list(requesterId, conversation.getId(), pageable));
    }

    @PatchMapping("/read")
    @Operation(summary = "Mark all peer-mentor messages as read",
            description = "Marks every unread message in this peer-mentor thread that was NOT "
                    + "sent by the authenticated user as read. Returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Read receipts updated",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Self-pair or non-mentor target",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Other user not found",
                    content = @Content)
    })
    public ResponseEntity<Void> markAllRead(
            @PathVariable Long otherMentorId,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorPair(
                requesterId, otherMentorId);
        messageService.markAllRead(requesterId, conversation.getId());
        return ResponseEntity.noContent().build();
    }
}
