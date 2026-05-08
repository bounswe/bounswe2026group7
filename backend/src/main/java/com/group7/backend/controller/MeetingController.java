package com.group7.backend.controller;

import com.group7.backend.dto.request.*;
import com.group7.backend.dto.response.*;
import com.group7.backend.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Meetings", description = "Mentorship meeting scheduling")
@PreAuthorize("isAuthenticated()")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping("/mentorships/{id}/meetings")
    @Operation(summary = "Schedule meetings",
            description = "Schedules one-time or recurring meetings for a mentorship (mentor-only).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Meetings scheduled",
                    content = @Content(schema = @Schema(implementation = MeetingCreateResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflict", content = @Content)
    })
    public ResponseEntity<MeetingCreateResponse> createMeetings(
            @Parameter(description = "Mentorship ID") @PathVariable Long id,
            @Valid @RequestBody MeetingCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        MeetingCreateResponse response = meetingService.createMeetings(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mentorships/{id}/meetings")
    @Operation(summary = "List mentorship meetings",
            description = "Lists meetings for the mentorship (mentor and mentee only).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meeting list",
                    content = @Content(schema = @Schema(implementation = MeetingSummaryResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content)
    })
    public ResponseEntity<List<MeetingSummaryResponse>> listMeetings(
            @Parameter(description = "Mentorship ID") @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.listMeetings(id, userId));
    }

    @GetMapping("/meetings/{id}")
    @Operation(summary = "Get meeting details",
            description = "Returns meeting details, notes, action items, and pending reschedule request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meeting details",
                    content = @Content(schema = @Schema(implementation = MeetingDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Meeting not found", content = @Content)
    })
    public ResponseEntity<MeetingDetailResponse> getMeeting(
            @Parameter(description = "Meeting ID") @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.getMeeting(id, userId));
    }

    @PostMapping("/meetings/{id}/confirm")
    @Operation(summary = "Confirm meeting", description = "Mentee confirms a pending meeting.")
    public ResponseEntity<MeetingSummaryResponse> confirmMeeting(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.confirmMeeting(id, userId));
    }

    @PostMapping("/meetings/{id}/decline")
    @Operation(summary = "Decline meeting", description = "Mentee declines a pending meeting.")
    public ResponseEntity<MeetingSummaryResponse> declineMeeting(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.declineMeeting(id, userId));
    }

    @PostMapping("/meetings/{id}/reschedule-requests")
    @Operation(summary = "Request reschedule", description = "Either party requests a reschedule.")
    public ResponseEntity<MeetingRescheduleRequestResponse> requestReschedule(
            @PathVariable Long id,
            @Valid @RequestBody MeetingRescheduleCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        MeetingRescheduleRequestResponse response = meetingService.requestReschedule(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/meetings/{id}/reschedule-requests/{rid}/approve")
    @Operation(summary = "Approve reschedule", description = "Counterpart approves the reschedule request.")
    public ResponseEntity<MeetingRescheduleRequestResponse> approveReschedule(
            @PathVariable Long id,
            @PathVariable Long rid,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.approveReschedule(id, rid, userId));
    }

    @PostMapping("/meetings/{id}/reschedule-requests/{rid}/reject")
    @Operation(summary = "Reject reschedule", description = "Counterpart rejects the reschedule request.")
    public ResponseEntity<MeetingRescheduleRequestResponse> rejectReschedule(
            @PathVariable Long id,
            @PathVariable Long rid,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.rejectReschedule(id, rid, userId));
    }

    @DeleteMapping("/meetings/{id}")
    @Operation(summary = "Cancel meeting", description = "Mentor cancels a meeting.")
    public ResponseEntity<MeetingSummaryResponse> cancelMeeting(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.cancelMeeting(id, userId));
    }

    @PatchMapping("/meetings/{id}/notes")
    @Operation(summary = "Update meeting notes", description = "Both parties update meeting notes.")
    public ResponseEntity<MeetingDetailResponse> updateNotes(
            @PathVariable Long id,
            @Valid @RequestBody MeetingNotesRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.updateNotes(id, userId, request));
    }

    @PostMapping("/meetings/{id}/action-items")
    @Operation(summary = "Add action item", description = "Both parties add an action item.")
    public ResponseEntity<MeetingActionItemResponse> addActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MeetingActionItemCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        MeetingActionItemResponse response = meetingService.addActionItem(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/meeting-action-items/{id}")
    @Operation(summary = "Update action item", description = "Update action item text or completion status.")
    public ResponseEntity<MeetingActionItemResponse> updateActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MeetingActionItemUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(meetingService.updateActionItem(id, userId, request));
    }

    @DeleteMapping("/meeting-action-items/{id}")
    @Operation(summary = "Delete action item", description = "Creator deletes an action item.")
    @ApiResponse(responseCode = "204", description = "Action item deleted")
    public ResponseEntity<Void> deleteActionItem(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        meetingService.deleteActionItem(id, userId);
        return ResponseEntity.noContent().build();
    }
}
