package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.response.AdminDirectInboxItem;
import com.group7.backend.service.AdminDirectInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-direct inbox listing for the authenticated caller: their
 * conversations with platform admins (or, for an admin caller, the users
 * they have DMed), with last-message preview and unread count, paginated
 * newest-first.
 *
 * <p>Companion to the existing admin-direct write surface at
 * {@code POST /api/admin/messages/direct/{userId}}: writes flow through
 * that admin-only path; reads flow through this endpoint and the per-
 * conversation message endpoint at {@code /api/conversations/admin-direct/
 * {otherUserId}/messages}.
 *
 * <p>No role gate: a regular user receiving an admin DM must be able to
 * list it. The list is naturally scoped to conversations the caller is a
 * participant in.
 */
@RestController
@RequestMapping("/api/conversations/admin-direct")
@Tag(name = "Admin Direct Inbox",
        description = "Listing of the caller's admin-direct conversations")
public class AdminDirectInboxController {

    private final AdminDirectInboxService inboxService;

    public AdminDirectInboxController(AdminDirectInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    @Operation(summary = "List my admin-direct conversations",
            description = "Returns the caller's admin-direct conversations, paginated and "
                    + "ordered by most recent activity. Each item carries peer identity, a "
                    + "flag indicating whether the peer is an admin, the last message's "
                    + "content and timestamp (nullable when the conversation has no messages "
                    + "yet), and the caller's unread count.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated inbox"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated",
                    content = @Content)
    })
    public ResponseEntity<Page<AdminDirectInboxItem>> listInbox(
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
