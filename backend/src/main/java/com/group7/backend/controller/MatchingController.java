package com.group7.backend.controller;

import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matching")
@Tag(name = "Matching", description = "Matching endpoints for mentors and mentees")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/mentors")
    @PreAuthorize("hasRole('MENTEE')")
    @Operation(
            summary = "Get top mentor matches",
            description = "Returns mentors ranked by compatibility score with pagination support. "
                    + "Excludes full-capacity mentors and requires the caller to be a mentee without an active mentor. "
                    + "Optionally filter by keyword matched against expertise, field, interests, skills, and goals."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated ranked mentor list"),
            @ApiResponse(responseCode = "403", description = "Not a mentee, or mentee already has an active mentor", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentee profile not found", content = @Content)
    })
    public ResponseEntity<Page<MentorMatchResponse>> getTopMentors(
            @Parameter(description = "Optional keyword to filter mentors") @RequestParam(required = false) String keyword,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long menteeId = (Long) authentication.getCredentials();
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(matchingService.getTopMentors(menteeId, keyword, pageable));
    }

    @GetMapping("/mentees")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get candidate mentees",
            description = "Returns paginated mentee candidates whose interests, skills, or major align with the mentor's preferences. "
                    + "Excludes mentees who already have an active mentor. "
                    + "Requires the caller to be a mentor with available capacity. "
                    + "Optionally filter by keyword matched against goals, major, career interest, background, interests, and skills."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated candidate mentee list"),
            @ApiResponse(responseCode = "403", description = "Not a mentor, or mentor has reached maximum mentee capacity", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentor profile not found", content = @Content)
    })
    public ResponseEntity<Page<MenteeCandidateResponse>> getCandidateMentees(
            @Parameter(description = "Optional keyword to filter mentees") @RequestParam(required = false) String keyword,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long mentorId = (Long) authentication.getCredentials();
        Pageable pageable = clampPageable(page, size);
        return ResponseEntity.ok(matchingService.getCandidateMentees(mentorId, keyword, pageable));
    }

    private Pageable clampPageable(int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(Math.max(page, 0), clampedSize);
    }
}
