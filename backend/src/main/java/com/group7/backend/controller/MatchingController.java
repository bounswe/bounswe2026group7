package com.group7.backend.controller;

import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
@Tag(name = "Matching", description = "Mentor matching endpoint for mentees")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/mentors")
    @PreAuthorize("hasRole('MENTEE')")
    @Operation(
            summary = "Get top mentor matches",
            description = "Returns up to 5 mentors ranked by compatibility score. "
                    + "Excludes full-capacity mentors and requires the caller to be a mentee without an active mentor. "
                    + "Optionally filter by keyword matched against expertise, field, interests, skills, and goals."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranked mentor list",
                    content = @Content(schema = @Schema(implementation = MentorMatchResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a mentee, or mentee already has an active mentor", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentee profile not found", content = @Content)
    })
    public ResponseEntity<List<MentorMatchResponse>> getTopMentors(
            @Parameter(description = "Optional keyword to filter mentors") @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        List<MentorMatchResponse> results = matchingService.getTopMentors(menteeId, keyword);
        return ResponseEntity.ok(results);
    }
}
