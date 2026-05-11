package com.group7.backend.controller;

import com.group7.backend.dto.request.MilestoneActionItemCreateRequest;
import com.group7.backend.dto.request.MilestoneActionItemUpdateRequest;
import com.group7.backend.dto.request.MilestoneCreateRequest;
import com.group7.backend.dto.request.MilestoneUpdateRequest;
import com.group7.backend.dto.response.MilestoneActionItemResponse;
import com.group7.backend.dto.response.MilestoneDetailResponse;
import com.group7.backend.dto.response.MilestoneSummaryResponse;
import com.group7.backend.service.MilestoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Milestones", description = "Mentorship milestone management")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @PostMapping("/mentorships/{id}/milestones")
    @Operation(summary = "Create milestone", description = "Mentor creates a new milestone for the mentorship")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Milestone created",
                    content = @Content(schema = @Schema(implementation = MilestoneDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Target date outside mentorship range", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor of this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active, or shared goal not defined " +
                    "(body carries code=\"GOAL_REQUIRED\")", content = @Content)
    })
    public ResponseEntity<MilestoneDetailResponse> createMilestone(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createMilestone(id, userId, request));
    }

    @GetMapping("/mentorships/{id}/milestones")
    @Operation(summary = "List milestones", description = "List all milestones for a mentorship")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Milestones returned"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a participant in this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found", content = @Content)
    })
    public ResponseEntity<List<MilestoneSummaryResponse>> listMilestones(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.listMilestones(id, userId));
    }

    @GetMapping("/milestones/{id}")
    @Operation(summary = "Get milestone detail", description = "Get milestone description and all action items")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Milestone detail returned",
                    content = @Content(schema = @Schema(implementation = MilestoneDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a participant in this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Milestone not found", content = @Content)
    })
    public ResponseEntity<MilestoneDetailResponse> getMilestone(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.getMilestone(id, userId));
    }

    @PatchMapping("/milestones/{id}")
    @Operation(summary = "Update milestone", description = "Mentor updates milestone status or details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Milestone updated",
                    content = @Content(schema = @Schema(implementation = MilestoneDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Target date outside mentorship range", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor of this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Milestone not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MilestoneDetailResponse> updateMilestone(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.updateMilestone(id, userId, request));
    }

    @DeleteMapping("/milestones/{id}")
    @Operation(summary = "Delete milestone", description = "Mentor deletes a milestone")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Milestone deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor of this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Milestone not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<Void> deleteMilestone(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        milestoneService.deleteMilestone(id, userId);
        return ResponseEntity.noContent().build();
    }

    // --- Action Items ---

    @PostMapping("/milestones/{id}/action-items")
    @Operation(summary = "Add action item", description = "Mentor adds an action item to a milestone")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Action item created",
                    content = @Content(schema = @Schema(implementation = MilestoneActionItemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor of this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Milestone not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MilestoneActionItemResponse> addActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneActionItemCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.addActionItem(id, userId, request));
    }

    @PatchMapping("/milestone-action-items/{id}")
    @Operation(summary = "Update action item", description = "Mentor updates text, either party toggles completion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Action item updated",
                    content = @Content(schema = @Schema(implementation = MilestoneActionItemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a participant or only mentors can edit text", content = @Content),
            @ApiResponse(responseCode = "404", description = "Action item not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<MilestoneActionItemResponse> updateActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneActionItemUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.updateActionItem(id, userId, request));
    }

    @DeleteMapping("/milestone-action-items/{id}")
    @Operation(summary = "Delete action item", description = "Mentor deletes an action item")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Action item deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Not a mentor of this mentorship", content = @Content),
            @ApiResponse(responseCode = "404", description = "Action item not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Mentorship is not active", content = @Content)
    })
    public ResponseEntity<Void> deleteActionItem(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        milestoneService.deleteActionItem(id, userId);
        return ResponseEntity.noContent().build();
    }
}
