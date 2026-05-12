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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbox listing for ADMIN_DIRECT conversations. Visible to any authenticated
 * participant so admins and recipients can both discover the thread.
 */
@RestController
@RequestMapping("/api/conversations/admin-direct")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Admin Direct Inbox",
        description = "Listing of admin-to-user direct conversations visible to the caller")
public class AdminDirectInboxController {

    private final AdminDirectInboxService inboxService;

    public AdminDirectInboxController(AdminDirectInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    @Operation(summary = "List my admin direct conversations",
            description = "Returns the caller's ADMIN_DIRECT conversations ordered by most "
                    + "recent activity with unread counts and last-message preview.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated inbox"),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated",
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
