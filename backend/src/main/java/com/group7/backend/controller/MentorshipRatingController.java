package com.group7.backend.controller;

import com.group7.backend.dto.request.CreateRatingRequest;
import com.group7.backend.dto.response.RatingResponse;
import com.group7.backend.service.MentorshipRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mentorships/{id}/ratings")
@Tag(name = "Mentorship Ratings", description = "Submit and read mentorship ratings (issue #254)")
@PreAuthorize("isAuthenticated()")
public class MentorshipRatingController {

    private final MentorshipRatingService ratingService;

    public MentorshipRatingController(MentorshipRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @Operation(summary = "Submit a mentorship rating",
            description = "Either participant submits a 1–5 star rating (with optional comment) of the counterpart. "
                    + "One rating per participant per mentorship.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rating created",
                    content = @Content(schema = @Schema(implementation = RatingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Caller has already rated this mentorship",
                    content = @Content)
    })
    public ResponseEntity<RatingResponse> create(@PathVariable Long id,
                                                 @Valid @RequestBody CreateRatingRequest request,
                                                 Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.create(userId, id, request));
    }

    @GetMapping
    @Operation(summary = "List mentorship ratings",
            description = "Returns ratings for the mentorship; visible to both participants only (404 to others).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ratings list",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RatingResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Mentorship not found or caller is not a participant",
                    content = @Content)
    })
    public ResponseEntity<List<RatingResponse>> list(@PathVariable Long id,
                                                     Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(ratingService.list(userId, id));
    }
}
