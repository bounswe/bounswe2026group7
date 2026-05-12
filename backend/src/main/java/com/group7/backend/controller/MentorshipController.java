package com.group7.backend.controller;

import com.group7.backend.dto.request.CancelMentorshipRequest;
import com.group7.backend.dto.request.CreateMentorRatingRequest;
import com.group7.backend.dto.request.EndMentorshipRequest;
import com.group7.backend.dto.request.ExtendMentorshipRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorRatingResponse;
import com.group7.backend.dto.response.MentorshipAuditLogResponse;
import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.dto.response.TimelineResponse;
import com.group7.backend.service.MentorRatingService;
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
    private final MentorRatingService mentorRatingService;

    public MentorshipController(MentorshipService mentorshipService,
                                MentorshipProgressService mentorshipProgressService,
                                MentorshipTimelineService mentorshipTimelineService,
                                MentorRatingService mentorRatingService) {
        this.mentorshipService = mentorshipService;
        this.mentorshipProgressService = mentorshipProgressService;
        this.mentorshipTimelineService = mentorshipTimelineService;
        this.mentorRatingService = mentorRatingService;
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

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel an active mentorship (mentee-only, #133/#237)",
            description = "Mentee cancels an active mentorship with a reason. Children " +
                    "(meetings, tasks, milestones, conversation/messages) are deleted; the " +
                    "mentorship row itself is retained with status=CANCELLED for audit and " +
                    "cool-down lookups. The mentor is notified. The cancellation is recorded " +
                    "against the auto-ban system (#134) and may impose a temporary ban once " +
                    "the threshold is crossed. Mentors must use PATCH /end instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentorship cancelled",
                    content = @Content(schema = @Schema(implementation = MentorshipResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Mentor attempted to cancel; use /end instead",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MentorshipResponse> cancelMentorship(
            @PathVariable Long id,
            @Valid @RequestBody CancelMentorshipRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.cancelMentorship(userId, id, request));
    }

    @PatchMapping("/{id}/end")
    @Operation(
            summary = "End an active mentorship gracefully (mentor-only, #237)",
            description = "Mentor closes an active mentorship — sets status=COMPLETED, stamps " +
                    "endDate=now, deletes children, audits the transition, and notifies the " +
                    "mentee. Reason is optional (mentor's wrap-up note, not a violation). " +
                    "Mentees must use POST /cancel instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentorship ended",
                    content = @Content(schema = @Schema(implementation = MentorshipResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Mentee attempted to end; use /cancel instead",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MentorshipResponse> endMentorship(
            @PathVariable Long id,
            @Valid @RequestBody EndMentorshipRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.endMentorship(userId, id, request));
    }

    @PatchMapping("/{id}/extend")
    @Operation(
            summary = "Extend duration of an active mentorship (mentor-only, #237)",
            description = "Pushes endDate forward by 1, 3, or 6 months and adds the same to " +
                    "duration. Records the extension in the audit trail and notifies the mentee."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentorship extended",
                    content = @Content(schema = @Schema(implementation = MentorshipResponse.class))),
            @ApiResponse(responseCode = "400", description = "additionalMonths is not 1, 3, or 6",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Mentee attempted to extend",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MentorshipResponse> extendMentorship(
            @PathVariable Long id,
            @Valid @RequestBody ExtendMentorshipRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.extendMentorship(userId, id, request));
    }

    @GetMapping("/{id}/rating")
    @Operation(
            summary = "Get the rating for this mentorship (mentor or mentee, #518)",
            description = "Returns the rating row submitted by this mentorship's mentee, "
                    + "if any. Visible to both the mentor and the mentee — non-participants "
                    + "get 404 (uniform with the rest of the mentorship surface). 404 also "
                    + "when the mentorship has no rating yet — lets the web client "
                    + "deterministically render the 'You rated …' block on first paint "
                    + "without depending on localStorage or a duplicate-POST probe."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rating found",
                    content = @Content(schema = @Schema(implementation = MentorRatingResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found, caller is not a participant, "
                    + "or no rating exists yet", content = @Content)
    })
    public ResponseEntity<MentorRatingResponse> getMentorshipRating(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorRatingService.getMentorshipRating(userId, id));
    }

    @PostMapping("/{id}/rating")
    @Operation(
            summary = "Submit a mentor rating (mentee-only, #237)",
            description = "Mentee submits a 1–5 score with an optional comment after the " +
                    "mentorship has terminated (status=COMPLETED or CANCELLED). One rating per " +
                    "mentorship — a second POST returns 409. The rating contributes to the " +
                    "mentor's averageRating / ratingCount on their profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rating created",
                    content = @Content(schema = @Schema(implementation = MentorRatingResponse.class))),
            @ApiResponse(responseCode = "400", description = "score outside 1–5 or comment too long",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Mentor attempted to rate", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship not yet terminated, or already rated",
                    content = @Content)
    })
    public ResponseEntity<MentorRatingResponse> rateMentor(
            @PathVariable Long id,
            @Valid @RequestBody CreateMentorRatingRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        MentorRatingResponse response = mentorRatingService.createRating(userId, id, request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Get mentorship audit trail (#133)",
            description = "Returns every recorded state transition for the mentorship in chronological " +
                    "order. The first row is the implicit creation transition (NULL → ACTIVE); " +
                    "user-driven transitions include the actor and reason. Only the mentor and " +
                    "mentee may read."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit trail",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MentorshipAuditLogResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content)
    })
    public ResponseEntity<List<MentorshipAuditLogResponse>> getAuditTrail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(mentorshipService.getAuditTrail(userId, id));
    }

    @DeleteMapping("/{id}/data")
    @Operation(
            summary = "Delete mentorship timeline/progress data for a past mentorship (#478)",
            description = "Deletes mentorship-scoped timeline/progress artifacts (tasks, milestones, meetings and "
                    + "their children). Only participants may call this endpoint, and only after the mentorship is "
                    + "no longer ACTIVE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mentorship data deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is still ACTIVE", content = @Content)
    })
    public ResponseEntity<Void> deleteMentorshipData(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        mentorshipService.deleteMentorshipData(userId, id);
        return ResponseEntity.noContent().build();
    }
}
