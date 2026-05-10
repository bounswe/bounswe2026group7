package com.group7.backend.controller;

import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.dto.response.TimelineResponse;
import com.group7.backend.service.MentorshipProgressService;
import com.group7.backend.service.MentorshipService;
import com.group7.backend.service.MentorshipTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/mentorships")
@Tag(name = "Mentorships", description = "Active mentorship management")
public class MentorshipController {

    private final MentorshipService mentorshipService;
    private final MentorshipProgressService mentorshipProgressService;
    private final MentorshipTimelineService mentorshipTimelineService;

    public MentorshipController(MentorshipService mentorshipService,
                                MentorshipProgressService mentorshipProgressService,
                                MentorshipTimelineService mentorshipTimelineService) {
        this.mentorshipService = mentorshipService;
        this.mentorshipProgressService = mentorshipProgressService;
        this.mentorshipTimelineService = mentorshipTimelineService;
    }

    @GetMapping
    @Operation(
            summary = "List active mentorships",
            description = "Returns all active mentorships for the authenticated user, whether they are a mentor or mentee."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active mentorships",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MentorshipResponse.class))))
    })
    public ResponseEntity<List<MentorshipResponse>> getActiveMentorships(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.getActiveMentorships(userId));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a mentorship",
            description = "Returns the mentorship by id, including the goalDefined flag. " +
                    "Only the mentor and mentee may read; non-participants get 404 to avoid leaking ids."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentorship found",
                    content = @Content(schema = @Schema(implementation = MentorshipResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content)
    })
    public ResponseEntity<MentorshipResponse> getMentorship(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.getMentorship(userId, id));
    }

    @GetMapping("/{id}/progress")
    @Operation(
            summary = "Get mentorship progress",
            description = "Returns aggregated task + milestone progress for the mentorship. " +
                    "Only the mentor and mentee may read; non-participants get 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress payload",
                    content = @Content(schema = @Schema(implementation = MentorshipProgressResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content)
    })
    public ResponseEntity<MentorshipProgressResponse> getMentorshipProgress(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipProgressService.getProgress(userId, id));
    }

    @GetMapping("/{id}/timeline")
    @Operation(
            summary = "Get mentorship timeline",
            description = "Returns meetings, tasks, and milestones merged into a single " +
                    "chronologically-sorted feed, scoped to a window (default = mentorship " +
                    "startDate..endDate; max 24 months). Only the mentor and mentee may read; " +
                    "non-participants get 404."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline payload",
                    content = @Content(schema = @Schema(implementation = TimelineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid window: from > to, or window > 24 months",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content)
    })
    public ResponseEntity<TimelineResponse> getMentorshipTimeline(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipTimelineService.getTimeline(userId, id, from, to));
    }

    @PutMapping("/{id}/goal")
    @Operation(
            summary = "Set shared goal",
            description = "Sets or updates the shared goal for an active mentorship. Both mentor and mentee can call this."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal updated",
                    content = @Content(schema = @Schema(implementation = MentorshipResponse.class))),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MentorshipResponse> setSharedGoal(
            @PathVariable Long id,
            @Valid @RequestBody SharedGoalRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.setSharedGoal(userId, id, request));
    }
}
