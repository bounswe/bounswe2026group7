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
 * Issue-spec REST surface for mentor-mentee chat. Each handler resolves the
 * mentorship to its underlying {@link Conversation} via {@link ConversationService}
 * before delegating to {@link MessageService} — the data model is conversation-
 * centric but the API surface stays mentorship-centric per #245.
 */
@RestController
@RequestMapping("/api/mentorships/{mentorshipId}/messages")
@Tag(name = "Messages", description = "Mentor/mentee chat messages within a mentorship")
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public MessageController(MessageService messageService,
                             ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @PostMapping
    @Operation(summary = "Send a message",
            description = "Sends a chat message within an active mentorship. "
                    + "Authenticated user must be one of the mentorship participants.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message sent",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship not active", content = @Content)
    })
    public ResponseEntity<MessageResponse> send(
            @PathVariable Long mentorshipId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Long senderId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorship(mentorshipId, senderId);
        MessageResponse created = messageService.send(senderId, conversation.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List messages",
            description = "Returns paginated message history for a mentorship, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated messages"),
            @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content)
    })
    public ResponseEntity<Page<MessageResponse>> list(
            @PathVariable Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorship(mentorshipId, requesterId);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(messageService.list(requesterId, conversation.getId(), pageable));
    }

    @PatchMapping("/read")
    @Operation(summary = "Mark all messages as read",
            description = "Marks every unread message in this mentorship that was NOT sent by "
                    + "the authenticated user as read. Returns 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Read receipts updated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content)
    })
    public ResponseEntity<Void> markAllRead(
            @PathVariable Long mentorshipId,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Conversation conversation = conversationService.findOrCreateForMentorship(mentorshipId, requesterId);
        messageService.markAllRead(requesterId, conversation.getId());
        return ResponseEntity.noContent().build();
    }
}
