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
    public ResponseEntity<MilestoneDetailResponse> createMilestone(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createMilestone(id, userId, request));
    }

    @GetMapping("/mentorships/{id}/milestones")
    @Operation(summary = "List milestones", description = "List all milestones for a mentorship")
    public ResponseEntity<List<MilestoneSummaryResponse>> listMilestones(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.listMilestones(id, userId));
    }

    @GetMapping("/milestones/{id}")
    @Operation(summary = "Get milestone detail", description = "Get milestone description and all action items")
    public ResponseEntity<MilestoneDetailResponse> getMilestone(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.getMilestone(id, userId));
    }

    @PatchMapping("/milestones/{id}")
    @Operation(summary = "Update milestone", description = "Mentor updates milestone status or details")
    public ResponseEntity<MilestoneDetailResponse> updateMilestone(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.updateMilestone(id, userId, request));
    }

    @DeleteMapping("/milestones/{id}")
    @Operation(summary = "Delete milestone", description = "Mentor deletes a milestone")
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
    public ResponseEntity<MilestoneActionItemResponse> addActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneActionItemCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.addActionItem(id, userId, request));
    }

    @PatchMapping("/milestone-action-items/{id}")
    @Operation(summary = "Update action item", description = "Mentor updates text, either party toggles completion")
    public ResponseEntity<MilestoneActionItemResponse> updateActionItem(
            @PathVariable Long id,
            @Valid @RequestBody MilestoneActionItemUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(milestoneService.updateActionItem(id, userId, request));
    }

    @DeleteMapping("/milestone-action-items/{id}")
    @Operation(summary = "Delete action item", description = "Mentor deletes an action item")
    public ResponseEntity<Void> deleteActionItem(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        milestoneService.deleteActionItem(id, userId);
        return ResponseEntity.noContent().build();
    }
}
