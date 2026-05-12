package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.docs.feed.FeedApiExamples;
import com.group7.backend.dto.request.CreateRepostRequest;
import com.group7.backend.dto.request.FeedCommentRequest;
import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.dto.response.FeedPostInteractionState;
import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.service.FeedInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for feed-post interactions (#347): like, comment, share,
 * bookmark.
 */
@RestController
@RequestMapping("/api/feed")
@Tag(name = "Feed Interactions",
        description = "Like / comment / share / bookmark endpoints over the social feed (#347).")
public class FeedInteractionController {

    private final FeedInteractionService interactionService;

    public FeedInteractionController(FeedInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    // ── Aggregate state ────────────────────────────────────────────────────

    @GetMapping("/posts/{id:\\d+}/interactions")
    @Operation(summary = "Read interaction state for a feed post",
            description = "Returns counts (likes, comments, shares, bookmarks) plus "
                    + "the viewer-relative toggle state (viewerHasLiked / viewerHasBookmarked). "
                    + "Lets the UI render the post detail without a follow-up call after "
                    + "every interaction. Companion to GET /api/feed/posts/{id}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current interaction state"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<FeedPostInteractionState> getInteractions(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        // Cache-Control: no-cache so likers' updates surface immediately and
        // viewer-relative flags (viewerHasLiked / viewerHasBookmarked) stay
        // fresh. Symmetric with the static GET /api/feed/posts/{id} endpoint
        // which uses ETag + private/max-age=30.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(interactionService.getInteractionState(id, viewerId));
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    @PostMapping("/posts/{id:\\d+}/like")
    @Operation(summary = "Toggle like on a feed post",
            description = "Idempotent toggle. Returns the updated interaction state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Toggle applied; current state returned",
                    content = @Content(examples = {
                            @ExampleObject(name = "now-liked",   value = FeedApiExamples.TOGGLE_LIKE_RESPONSE_LIKED),
                            @ExampleObject(name = "now-unliked", value = FeedApiExamples.TOGGLE_LIKE_RESPONSE_UNLIKED)
                    })),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<FeedPostInteractionState> toggleLike(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(interactionService.toggleLike(id, userId));
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @PostMapping("/posts/{id:\\d+}/bookmark")
    @Operation(summary = "Toggle bookmark on a feed post")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Toggle applied"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content)
    })
    public ResponseEntity<FeedPostInteractionState> toggleBookmark(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(interactionService.toggleBookmark(id, userId));
    }

    @GetMapping("/me/bookmarks")
    @Operation(summary = "Current user's bookmarked posts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged bookmarked posts"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Page<FeedPostListItem>> myBookmarks(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(interactionService.listBookmarks(userId, pageable));
    }

    // ── Shares ─────────────────────────────────────────────────────────────

    @PostMapping("/posts/{id:\\d+}/share")
    @Operation(summary = "Record a share event",
            description = "Append-only — every call records a new share event row. No fanout in #347.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Share recorded"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content)
    })
    public ResponseEntity<FeedPostInteractionState> recordShare(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            Authentication authentication) {
        Long sharerId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(interactionService.recordShare(id, sharerId));
    }

    @PostMapping("/posts/{id:\\d+}/reposts")
    @Operation(summary = "Repost or quote-share a feed post",
            description = "Empty body or null = bare repost; non-blank body = quote-share. "
                    + "Both fan out via STOMP to the sharer's followers and notify the original "
                    + "author (unless the sharer is the author). Repeating the same payload "
                    + "within the configured idempotency window (default 60s) collapses to the "
                    + "existing row without re-firing fanout. Distinct from POST /share, which "
                    + "is a silent analytics event and does not fan out.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = {
                            @ExampleObject(name = "bare-repost",
                                    summary = "Bare repost with no commentary",
                                    value = "{}"),
                            @ExampleObject(name = "quote-share",
                                    summary = "Quote-share with commentary",
                                    value = "{\"body\": \"Great take on this — fully agree.\"}")
                    })))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repost recorded; current interaction state returned"),
            @ApiResponse(responseCode = "400", description = "Body exceeds 2000 chars", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<FeedPostInteractionState> repost(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateRepostRequest request,
            Authentication authentication) {
        Long sharerId = (Long) authentication.getCredentials();
        CreateRepostRequest effective = request != null ? request : CreateRepostRequest.empty();
        return ResponseEntity.ok(interactionService.recordRepost(id, sharerId, effective));
    }

    // ── Comments ───────────────────────────────────────────────────────────

    @PostMapping("/posts/{id:\\d+}/comments")
    @Operation(summary = "Add a comment to a feed post",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "default",
                            value = FeedApiExamples.ADD_COMMENT_REQUEST))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment created",
                    content = @Content(examples = @ExampleObject(
                            name = "default",
                            value = FeedApiExamples.FEED_COMMENT_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content)
    })
    public ResponseEntity<FeedCommentResponse> addComment(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            @Valid @RequestBody FeedCommentRequest request,
            Authentication authentication) {
        Long authorId = (Long) authentication.getCredentials();
        FeedCommentResponse body = interactionService.addComment(id, authorId, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/posts/{id:\\d+}/comments")
    @Operation(summary = "List comments on a feed post (chronological)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged comments"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content)
    })
    public ResponseEntity<Page<FeedCommentResponse>> listComments(
            @Parameter(description = "Feed post id") @PathVariable Long id,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(interactionService.listComments(id, viewerId, pageable));
    }

    @PatchMapping("/comments/{id:\\d+}")
    @Operation(summary = "Edit a comment (author-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated comment"),
            @ApiResponse(responseCode = "400", description = "Validation failure", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Non-author cannot edit", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comment not found or soft-deleted", content = @Content)
    })
    public ResponseEntity<FeedCommentResponse> editComment(
            @Parameter(description = "Comment id") @PathVariable Long id,
            @Valid @RequestBody FeedCommentRequest request,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(interactionService.editComment(id, requesterId, request.body()));
    }

    @GetMapping("/comments/{id:\\d+}")
    @Operation(summary = "Get a single comment by id (permalink)",
            description = "Returns the comment if present, not soft-deleted, AND its parent "
                    + "post is still visible. 404 if any of those conditions fail.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment",
                    content = @Content(examples = @ExampleObject(
                            name = "default",
                            value = FeedApiExamples.FEED_COMMENT_RESPONSE))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comment soft-deleted or parent post not visible", content = @Content)
    })
    public ResponseEntity<FeedCommentResponse> getComment(
            @Parameter(description = "Comment id") @PathVariable Long id,
            Authentication authentication) {
        Long viewerId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(interactionService.getComment(id, viewerId));
    }

    @DeleteMapping("/comments/{id:\\d+}")
    @Operation(summary = "Soft-delete a comment (author-only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted (or already deleted)"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Non-author cannot delete", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comment not found", content = @Content)
    })
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "Comment id") @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = (Long) authentication.getCredentials();
        interactionService.deleteComment(id, requesterId);
        return ResponseEntity.noContent().build();
    }
}
