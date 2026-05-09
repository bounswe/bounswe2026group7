package com.group7.backend.dto.feed;

/**
 * Single source of truth for the social-feed STOMP destination prefixes
 * used by the real-time push surface (#349). Lives in {@code dto/feed/}
 * so both the publisher side ({@link com.group7.backend.event.FeedFanoutListener}
 * in {@code event/}) and the security side
 * ({@link com.group7.backend.config.websocket.JwtChannelInterceptor} in
 * {@code config/websocket/}) depend on this package rather than reaching
 * into each other — keeps the dependency direction clean.
 */
public final class FeedTopics {

    /**
     * Per-user feed STOMP destination prefix. Concrete destination is
     * {@code /topic/feed.{userId}}; the {@code JwtChannelInterceptor}
     * SUBSCRIBE ACL enforces that the caller's userId equals the topic's
     * userId, so a session can only subscribe to its own feed topic.
     */
    public static final String FEED_PREFIX = "/topic/feed.";

    private FeedTopics() {
        // utility class — no instances
    }
}
