package com.group7.backend.controller;

import com.group7.backend.dto.request.MentorshipRequestCreateRequest;
import com.group7.backend.dto.response.MentorshipRequestResponse;
import com.group7.backend.service.MentorshipRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mentorship-requests")
@Tag(name = "Mentorship Requests", description = "Endpoints for creating and listing mentorship requests")
public class MentorshipRequestController {

    private final MentorshipRequestService mentorshipRequestService;

    public MentorshipRequestController(MentorshipRequestService mentorshipRequestService) {
        this.mentorshipRequestService = mentorshipRequestService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MENTEE')")
    @Operation(
            summary = "Send a mentorship request",
            description = "Creates a mentorship request from the authenticated mentee to the specified mentor. "
                    + "Validates that the mentee has no active mentor, the mentor has available capacity, "
                    + "and no duplicate pending request exists."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Request created successfully",
                    content = @Content(schema = @Schema(implementation = MentorshipRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentee or mentor not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Business rule violation (active mentor, capacity, or duplicate)", content = @Content)
    })
    public ResponseEntity<MentorshipRequestResponse> createRequest(
            @Valid @RequestBody MentorshipRequestCreateRequest request,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        MentorshipRequestResponse response = mentorshipRequestService.createRequest(menteeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sent")
    @PreAuthorize("hasRole('MENTEE')")
    @Operation(
            summary = "List sent mentorship requests",
            description = "Returns mentorship requests sent by the authenticated mentee, ordered by most recent first. "
                    + "Supports pagination via page and size query parameters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of sent requests"),
            @ApiResponse(responseCode = "404", description = "Mentee not found", content = @Content)
    })
    public ResponseEntity<Page<MentorshipRequestResponse>> getSentRequests(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorshipRequestService.getSentRequests(menteeId, pageable));
    }

    @GetMapping("/received")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "List received mentorship requests",
            description = "Returns mentorship requests received by the authenticated mentor, ordered by most recent first. "
                    + "Supports pagination via page and size query parameters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of received requests"),
            @ApiResponse(responseCode = "404", description = "Mentor not found", content = @Content)
    })
    public ResponseEntity<Page<MentorshipRequestResponse>> getReceivedRequests(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mentorshipRequestService.getReceivedRequests(mentorId, pageable));
    }
}
