package com.group7.backend.controller;

import com.group7.backend.dto.response.FeedTrendingHashtag;
import com.group7.backend.service.FeedTrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only HTTP surface for the trending-hashtag aggregate (#487).
 *
 * <p>Backed by an hourly-refreshed materialized view, so the endpoint
 * always serves a snapshot consistent across requests within the same
 * refresh window. Authenticated by the project's standard JWT filter
 * — there is no public/anonymous variant of this endpoint.
 *
 * <p>{@code @Validated} enables the {@code @Min}/{@code @Max} on the
 * {@code limit} request param to surface as 400 (otherwise Spring
 * silently passes invalid values straight through to the service,
 * which would clamp them but lose the error signal).
 */
@RestController
@RequestMapping("/api/feed/trending")
@Validated
@Tag(name = "Feed Trending",
        description = "Hourly-refreshed trending hashtag aggregates over a 24-hour window.")
public class FeedTrendingController {

    private final FeedTrendingService trendingService;

    public FeedTrendingController(FeedTrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @GetMapping("/hashtags")
    @Operation(summary = "Top trending hashtags from the last 24 hours",
            description = "Returns hashtags ordered by a composite engagement score "
                    + "(postCount + 2 * uniqueLikers + 3 * commentCount). The result "
                    + "reflects the most recent materialized-view refresh; the view "
                    + "refreshes hourly at five-past-the-hour by default.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trending hashtag list (newest refresh)"),
            @ApiResponse(responseCode = "400", description = "limit out of range (1..50)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<List<FeedTrendingHashtag>> hashtags(
            @Parameter(description = "Maximum number of hashtags to return (1..50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(trendingService.listTrendingHashtags(limit));
    }
}
