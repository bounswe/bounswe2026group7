package com.group7.backend.controller;

import com.group7.backend.dto.response.FeedUnreadCountResponse;
import com.group7.backend.service.FeedReadStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user feed read-state surface (#349). Companion to the WebSocket
 * STOMP fanout transport on {@code /topic/feed.{userId}}: when the
 * client reconnects after a STOMP disconnect, this surface tells the UI
 * how many posts arrived in the meantime.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /api/feed/mark-read} — set the viewer's cursor to
 *       {@code clock_timestamp()}. Idempotent.</li>
 *   <li>{@code GET /api/feed/unread-count} — count posts in the viewer's
 *       follow graph created after the cursor, capped at
 *       {@code app.feed.unread.cap} (default 99).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/feed")
@Tag(name = "Feed Read State",
        description = "Per-user feed-read cursor: mark-read + unread-count (#349). " +
                "Companion to the /topic/feed.{userId} STOMP push surface — gives the UI " +
                "an N-new-posts indicator for clients reconnecting after a disconnect.")
public class FeedReadStateController {

    private final FeedReadStateService feedReadStateService;

    public FeedReadStateController(FeedReadStateService feedReadStateService) {
        this.feedReadStateService = feedReadStateService;
    }

    @PostMapping("/mark-read")
    @Operation(summary = "Mark the feed as read up to the current server clock",
            description = "Sets the viewer's read cursor to clock_timestamp(). Idempotent — " +
                    "calling twice with no posts in between is a no-op-effective-state. " +
                    "Server-side timestamp; client does not supply one (avoids clock skew).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cursor updated"),
            @ApiResponse(responseCode = "403", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<Void> markRead(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        feedReadStateService.markRead(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count posts in the viewer's follow graph since the last read",
            description = "Returns posts in the viewer's follow graph created after the " +
                    "viewer's last mark-read timestamp, capped at app.feed.unread.cap " +
                    "(default 99). The cappedAtMax flag tells the UI whether to render '99+'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capped unread count"),
            @ApiResponse(responseCode = "403", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<FeedUnreadCountResponse> unreadCount(Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(feedReadStateService.unreadCount(userId));
    }
}
