package com.group7.backend.config.websocket;

import com.group7.backend.dto.feed.FeedTopics;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Authenticates STOMP {@code CONNECT} frames against the project JWT and
 * authorises {@code SUBSCRIBE} frames against two managed prefixes:
 * <ul>
 *   <li>{@code /topic/conversation/{id}} — caller must be a
 *       {@link com.group7.backend.entity.Conversation} participant
 *       (#245 messaging).</li>
 *   <li>{@code /topic/feed.{userId}} — caller's user id must equal the
 *       topic's user id (#349 social-feed real-time push). Hard
 *       separation: a session can only subscribe to its own feed topic.</li>
 * </ul>
 *
 * <p>The auth token is read from the {@code Authorization} STOMP native header
 * — never from URL query parameters — matching the Spring Framework reference's
 * recommended pattern. The resulting {@link Principal} is attached to the STOMP
 * session so {@code @MessageMapping} controllers can read it.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);
    private static final String CONVERSATION_PREFIX = "/topic/conversation/";

    private final JwtService jwtService;
    private final ConversationParticipantRepository participantRepository;

    public JwtChannelInterceptor(JwtService jwtService,
                                 ConversationParticipantRepository participantRepository) {
        this.jwtService = jwtService;
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            default -> {
                // SEND, UNSUBSCRIBE, DISCONNECT etc. — no extra checks here.
                // Spring Security's WebSocketAuthorizationManager enforces
                // authenticated() for any frame requiring it.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = bearerToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || !jwtService.isTokenValid(token)) {
            log.warn("STOMP CONNECT rejected: missing or invalid Bearer token");
            throw new MessagingException("Authentication required");
        }
        String email = jwtService.extractEmail(token);
        Long userId = jwtService.extractUserId(token);
        String role = jwtService.extractRole(token);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                userId,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        accessor.setUser(auth);
        log.debug("STOMP CONNECT authenticated: userId={}, role={}", userId, role);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)) {
            throw new MessagingException("Subscribe requires an authenticated session");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Long userId = (Long) auth.getCredentials();

        if (destination.startsWith(CONVERSATION_PREFIX)) {
            authorizeConversationSubscribe(destination, userId);
            return;
        }
        if (destination.startsWith(FeedTopics.FEED_PREFIX)) {
            authorizeFeedSubscribe(destination, userId);
            return;
        }
        // Subscriptions outside our managed prefixes carry no ACL here.
        // Spring Security's WebSocketAuthorizationManager still enforces
        // authenticated() for any frame that needs it.
    }

    private void authorizeConversationSubscribe(String destination, Long userId) {
        Long conversationId = parseSuffixAsLong(destination, CONVERSATION_PREFIX);
        if (conversationId == null) {
            throw new MessagingException("Malformed subscription destination: " + destination);
        }
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            log.warn("STOMP SUBSCRIBE rejected: userId={}, destination={}", userId, destination);
            throw new MessagingException("You are not a participant of this conversation");
        }
    }

    private void authorizeFeedSubscribe(String destination, Long userId) {
        Long topicUserId = parseSuffixAsLong(destination, FeedTopics.FEED_PREFIX);
        if (topicUserId == null) {
            throw new MessagingException("Malformed subscription destination: " + destination);
        }
        if (!topicUserId.equals(userId)) {
            log.warn("STOMP SUBSCRIBE rejected: userId={} cannot subscribe to {}",
                    userId, destination);
            throw new MessagingException("Cannot subscribe to another user's feed topic");
        }
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7).trim();
    }

    private static Long parseSuffixAsLong(String destination, String prefix) {
        try {
            return Long.parseLong(destination.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
