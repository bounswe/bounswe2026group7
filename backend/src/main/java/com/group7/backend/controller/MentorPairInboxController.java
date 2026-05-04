package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.response.MentorPairInboxItem;
import com.group7.backend.service.MentorPairInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mentor-pair inbox listing for the authenticated mentor: their peer-mentor
 * conversations with last-message preview and unread count, paginated
 * newest-first.
 *
 * <p>Companion to {@code MentorPairMessageController} (per-conversation
 * messaging surface). Class-level {@code @PreAuthorize} mirrors that
 * controller's role gate.
 */
@RestController
@RequestMapping("/api/conversations/mentor-pair")
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Pair Inbox",
        description = "Listing of the caller's mentor-pair conversations")
public class MentorPairInboxController {

    private final MentorPairInboxService inboxService;

    public MentorPairInboxController(MentorPairInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    @Operation(summary = "List my mentor-pair conversations",
            description = "Returns the caller's mentor-pair conversations, paginated and "
                    + "ordered by most recent activity. Each item carries peer identity, "
                    + "the last message's content and timestamp (nullable when the "
                    + "conversation has no messages yet), and the caller's unread count.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated inbox"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor",
                    content = @Content)
    })
    public ResponseEntity<Page<MentorPairInboxItem>> listInbox(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(inboxService.listInbox(requesterId, pageable));
    }
}
