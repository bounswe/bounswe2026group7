package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.service.FeedReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read surface for the social feed (#350). Three sibling endpoints
 * sharing one DTO so the UI can switch tabs without reshaping the
 * response:
 *
 * <ul>
 *   <li>{@code GET /api/feed/for-you} — algorithmic ordering by the
 *       interest-overlap + time-decay + follow-boost ranker.</li>
 *   <li>{@code GET /api/feed/following} — chronological over posts
 *       authored by users the viewer follows.</li>
 *   <li>{@code GET /api/feed/search} — keyword + hashtag combined
 *       search; either filter is optional, both apply with AND
 *       semantics when present.</li>
 * </ul>
 *
 * <p>All three honour {@link PageableSupport#clampPageable} for the
 * page-size cap (1..100), matching the project convention from
 * {@code FollowController} and {@code MatchingController}.
 */
@RestController
@RequestMapping("/api/feed")
@Tag(name = "Feed Read",
        description = "For-You, Following, and search read surfaces over the social feed (#350).")
public class FeedReadController {

    private final FeedReadService feedReadService;

    public FeedReadController(FeedReadService feedReadService) {
        this.feedReadService = feedReadService;
    }

    @GetMapping("/for-you")
    @Operation(summary = "Algorithmic For-You feed",
            description = "Returns posts ranked by interest overlap, time decay, and "
                    + "follow-graph proximity. Excludes the viewer's own posts and "
                    + "soft-deleted posts. Pages beyond the candidate window return empty. "
                    + "Note: the response's totalElements reflects the candidate-window "
                    + "size (configured by app.feed.forYou.candidate-window, default 200), "
                    + "not the global post count — the For-You feed deliberately ranks a "
                    + "rolling window of recent candidates and caps at that size.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged ranked posts"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Page<FeedPostListItem>> forYou(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(feedReadService.forYouFeed(viewerId, pageable));
    }

    @GetMapping("/following")
    @Operation(summary = "Following feed",
            description = "Returns posts authored by users the viewer follows, ordered by "
                    + "creation time descending. Empty when the viewer follows nobody.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged followed-author posts"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Page<FeedPostListItem>> following(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(feedReadService.followingFeed(viewerId, pageable));
    }

    @GetMapping("/search")
    @Operation(summary = "Search feed posts by keyword and / or hashtag",
            description = "At least one of `q` or `hashtag` is required — both null returns "
                    + "400. Use `/api/feed/for-you` or `/api/feed/following` for the full "
                    + "feed without a filter. Keyword uses pg_trgm-accelerated LIKE on the "
                    + "body and is escaped at the service boundary so `%` and `_` cannot act "
                    + "as wildcards. Hashtag matches the normalised tag value (lowercased, "
                    + "leading-# stripped); a hashtag that fails normalisation returns an "
                    + "empty page rather than 400. Combined queries AND the two predicates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged matching posts"),
            @ApiResponse(responseCode = "400", description = "Both `q` and `hashtag` missing", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Page<FeedPostListItem>> search(
            @Parameter(description = "Free-text keyword (≥3 chars for index hit)")
            @RequestParam(name = "q", required = false) String keyword,
            @Parameter(description = "Single hashtag, with or without leading '#'")
            @RequestParam(name = "hashtag", required = false) String hashtag,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(feedReadService.search(keyword, hashtag, pageable));
    }
}
