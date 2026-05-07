package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.response.FollowEdgeResponse;
import com.group7.backend.dto.response.UserSummary;
import com.group7.backend.entity.User;
import com.group7.backend.service.FollowResult;
import com.group7.backend.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the follow graph (#343). Lives alongside {@link UserController}
 * under {@code /api/users}; the two controllers' mappings are disjoint
 * ({@code /{id}/follow}, {@code /{id}/followers}, {@code /{id}/following}
 * here vs. profile / list endpoints there) so Spring routes them without
 * collision.
 *
 * <p>Status codes are deliberate: a fresh follow returns {@code 201}
 * (resource created), an idempotent re-follow returns {@code 200} (no new
 * resource but the call succeeded). The {@code DELETE} is always {@code 204}
 * regardless of whether an edge existed — that's the standard idempotent-DELETE
 * shape and matches the spec for #343.
 *
 * <p>The {@code {id:\\d+}} regex on the path matches the precedent at
 * {@code UserController} and keeps {@code /api/users/me} from being captured
 * by the numeric-id route.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Follow Graph",
        description = "Directional follow / unfollow surface and follower / following listings (#343).")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{id:\\d+}/follow")
    @Operation(summary = "Follow a user",
            description = "Creates the directional edge (caller → target) if absent. "
                    + "Idempotent: returns 201 with the new edge when fresh, 200 with the "
                    + "existing edge on a duplicate POST. Self-follow returns 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New follow edge created"),
            @ApiResponse(responseCode = "200", description = "Edge already existed (idempotent)"),
            @ApiResponse(responseCode = "400", description = "Self-follow rejected", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Target user not found", content = @Content)
    })
    public ResponseEntity<FollowEdgeResponse> follow(
            @Parameter(description = "User id to follow") @PathVariable Long id,
            Authentication authentication) {
        Long followerId = (Long) authentication.getCredentials();
        FollowResult result = followService.follow(followerId, id);
        FollowEdgeResponse body = new FollowEdgeResponse(result.followerId(), result.followeeId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    @DeleteMapping("/{id:\\d+}/follow")
    @Operation(summary = "Unfollow a user",
            description = "Removes the directional edge (caller → target) if present. "
                    + "Idempotent: 204 either way.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Edge removed (or never existed)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Void> unfollow(
            @Parameter(description = "User id to unfollow") @PathVariable Long id,
            Authentication authentication) {
        Long followerId = (Long) authentication.getCredentials();
        followService.unfollow(followerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id:\\d+}/followers")
    @Operation(summary = "List followers of a user",
            description = "Paged list of users following the given user, newest follow first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged followers list"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Target user not found", content = @Content)
    })
    public ResponseEntity<Page<UserSummary>> listFollowers(
            @Parameter(description = "User id whose followers to list") @PathVariable Long id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        Page<User> followers = followService.listFollowers(id, pageable);
        return ResponseEntity.ok(followers.map(UserSummary::from));
    }

    @GetMapping("/{id:\\d+}/following")
    @Operation(summary = "List users a user is following",
            description = "Paged list of users that the given user follows, newest follow first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged following list"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Target user not found", content = @Content)
    })
    public ResponseEntity<Page<UserSummary>> listFollowing(
            @Parameter(description = "User id whose following list to return") @PathVariable Long id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        Page<User> following = followService.listFollowing(id, pageable);
        return ResponseEntity.ok(following.map(UserSummary::from));
    }
}
