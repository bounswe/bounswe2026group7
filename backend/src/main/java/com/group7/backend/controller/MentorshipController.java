package com.group7.backend.controller;

import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.service.MentorshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mentorships")
@Tag(name = "Mentorships", description = "Active mentorship management")
public class MentorshipController {

    private final MentorshipService mentorshipService;

    public MentorshipController(MentorshipService mentorshipService) {
        this.mentorshipService = mentorshipService;
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
