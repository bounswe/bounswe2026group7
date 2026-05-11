package com.group7.backend.controller;

import com.group7.backend.dto.response.MenteeStatsResponse;
import com.group7.backend.dto.response.MentorStatsResponse;
import com.group7.backend.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Stats", description = "Aggregated dashboard statistics for mentors and mentees")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/mentor/me")
    @Operation(
            summary = "Get mentor dashboard stats",
            description = "Returns aggregated counts for the authenticated mentor: mentees, "
                    + "mentorship lifecycle counts, task progress, completed meeting hours, and "
                    + "pending requests. The response is implicitly scoped to the caller — there "
                    + "is no path parameter, so cross-user access is not possible. A user with "
                    + "no mentor activity gets a zero-valued payload (200, not 404)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentor stats payload",
                    content = @Content(schema = @Schema(implementation = MentorStatsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<MentorStatsResponse> getMentorStats(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(statsService.getMentorStats(userId));
    }

    @GetMapping("/mentee/me")
    @Operation(
            summary = "Get mentee dashboard stats",
            description = "Returns aggregated counts for the authenticated mentee: active "
                    + "mentorships, task progress, upcoming meetings, distinct mentors worked "
                    + "with, and total requests sent. Implicit caller scope, same as the mentor "
                    + "endpoint. A user with no mentee activity gets a zero-valued payload."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentee stats payload",
                    content = @Content(schema = @Schema(implementation = MenteeStatsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<MenteeStatsResponse> getMenteeStats(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(statsService.getMenteeStats(userId));
    }
}
