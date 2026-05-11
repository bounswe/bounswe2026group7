package com.group7.backend.controller;

import com.group7.backend.dto.request.CreateFeedPostRequest;
import com.group7.backend.dto.request.UpdateFeedPostRequest;
import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.service.FeedPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the social-feed posts core (#348).
 *
 * <p>Status code contract:
 * <ul>
 *   <li>{@code POST} → {@code 201 Created} with the new post body.
 *       Validation failures: {@code 400}. Admin requester: {@code 403}.</li>
 *   <li>{@code GET /{id:\d+}} → {@code 200} with the post body, {@code 404}
 *       if the post is missing or soft-deleted.</li>
 *   <li>{@code PATCH /{id:\d+}} → {@code 200} on successful update,
 *       {@code 403} non-author, {@code 404} missing / soft-deleted.</li>
 *   <li>{@code DELETE /{id:\d+}} → {@code 204} (idempotent on already-
 *       deleted), {@code 403} non-author, {@code 404} if the post id
 *       never existed.</li>
 * </ul>
 *
 * <p>The {@code {id:\\d+}} regex matches the {@code FollowController} /
 * {@code UserController} precedent and reserves non-numeric path segments
 * for any future routes (e.g. {@code /api/feed/posts/me} or
 * {@code /api/feed/posts/trending}).
 *
 * <p>Auth user id is read from {@code (Long) authentication.getCredentials()}
 * — the project's existing convention. Body-supplied user ids are never
 * trusted.
 */
@RestController
@RequestMapping("/api/feed/posts")
@Tag(name = "Feed Posts",
        description = "Author-owned social-feed posts (text + hashtags). Soft-delete preserves "
                + "referential integrity for downstream interactions and feed reads (#348).")
public class FeedPostController {

    private final FeedPostService feedPostService;

    public FeedPostController(FeedPostService feedPostService) {
        this.feedPostService = feedPostService;
    }

    @PostMapping
    @Operation(summary = "Create a feed post",
            description = "Creates a new feed post on behalf of the authenticated user. "
                    + "Mentors and mentees can post; admins are rejected (403). Hashtags "
                    + "are server-normalised (lowercase, leading '#' stripped, dedupe).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Post created"),
            @ApiResponse(responseCode = "400", description = "Validation failure (blank body, oversize, too many tags)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Admin requester (admins cannot post)", content = @Content)
    })
    public ResponseEntity<FeedPostResponse> create(
            @Valid @RequestBody CreateFeedPostRequest request,
            Authentication authentication) {
        Long authorId = (Long) authentication.getCredentials();
        FeedPostResponse body = feedPostService.create(authorId, request.body(), request.hashtags());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get a feed post by id",
            description = "Returns the post if present and not soft-deleted. The viewer's "
                    + "id is reflected in the response's isAuthor flag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post body"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<FeedPostResponse> getById(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(feedPostService.getById(id, viewerId));
    }

    @PatchMapping("/{id:\\d+}")
    @Operation(summary = "Update a feed post (author-only)",
            description = "Partial update. Null fields on the request body are not "
                    + "modified. Hashtags, when supplied, fully replace the existing set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated post"),
            @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Non-author cannot edit", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found or soft-deleted", content = @Content),
            @ApiResponse(responseCode = "409", description = "Concurrent modification — retry", content = @Content)
    })
    public ResponseEntity<FeedPostResponse> update(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            @Valid @RequestBody UpdateFeedPostRequest request,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(
                feedPostService.update(id, requesterId, request.body(), request.hashtags()));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Soft-delete a feed post (author-only)",
            description = "Sets deleted_at on the post. Idempotent: a second DELETE on the "
                    + "same id returns 204 without further state change.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted (or already deleted)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Non-author cannot delete", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post never existed", content = @Content),
            @ApiResponse(responseCode = "409", description = "Concurrent modification — retry", content = @Content)
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        feedPostService.delete(id, requesterId);
        return ResponseEntity.noContent().build();
    }
}
