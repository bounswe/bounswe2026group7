package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.service.FollowRecommendationService;
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
 * Read-only REST surface for follow recommendations (#344). Lives under
 * the same {@code /api/users} namespace as {@link FollowController} but
 * uses a {@code /me/} segment so it doesn't collide with the
 * {@code /{id:\\d+}/...} numeric-id routes there.
 *
 * <p>No {@code @PreAuthorize}: the global SecurityConfig already
 * requires authentication for any non-permitAll path, and any
 * authenticated user can fetch their own recommendations regardless of
 * role.
 */
@RestController
@RequestMapping("/api/users/me/follow-recommendations")
@Tag(name = "Follow Recommendations",
        description = "Suggested users to follow with explanation factors (#344).")
public class FollowRecommendationController {

    private final FollowRecommendationService service;

    public FollowRecommendationController(FollowRecommendationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List follow recommendations for the caller",
            description = "Paged list of suggested users to follow, sorted by recommendation score "
                    + "(higher first). Excludes the caller, users they already follow, and admins. "
                    + "Each entry carries a `factors` array explaining why it surfaced "
                    + "(shared interests, follow-graph proximity).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged recommendations"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Caller user not found", content = @Content)
    })
    public ResponseEntity<Page<FollowRecommendationResponse>> recommend(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(service.recommend(viewerId, pageable));
    }
}
