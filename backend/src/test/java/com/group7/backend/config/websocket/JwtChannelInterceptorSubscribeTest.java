package com.group7.backend.config.websocket;

import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-level coverage for {@link JwtChannelInterceptor#preSend} on
 * {@link StompCommand#SUBSCRIBE} frames. Issue #324 acceptance criteria
 * require ≥ 90% coverage on the SUBSCRIBE authorisation path; the
 * end-to-end {@code MessagingWebSocketIntegrationTest} only exercises the
 * authorised and unauthorised-participant branches, so the remaining three
 * (missing principal, malformed destination, out-of-prefix passthrough)
 * are covered here against mocked collaborators.
 */
class JwtChannelInterceptorSubscribeTest {

    private static final String TOPIC_PREFIX = "/topic/conversation/";

    private JwtService jwtService;
    private ConversationParticipantRepository participantRepository;
    private MessageChannel channel;
    private JwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        participantRepository = mock(ConversationParticipantRepository.class);
        channel = mock(MessageChannel.class);
        interceptor = new JwtChannelInterceptor(jwtService, participantRepository);
    }

    @Test
    void subscribePassesWhenPrincipalIsParticipant() {
        Long userId = 7L;
        Long conversationId = 42L;
        when(participantRepository.existsByConversationIdAndUserId(conversationId, userId))
                .thenReturn(true);

        Message<?> message = subscribeMessage(TOPIC_PREFIX + conversationId, authToken(userId));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository).existsByConversationIdAndUserId(conversationId, userId);
    }

    @Test
    void subscribeRejectedWhenPrincipalIsNotParticipant() {
        Long userId = 7L;
        Long conversationId = 42L;
        when(participantRepository.existsByConversationIdAndUserId(conversationId, userId))
                .thenReturn(false);

        Message<?> message = subscribeMessage(TOPIC_PREFIX + conversationId, authToken(userId));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void subscribeRejectedWhenPrincipalIsMissing() {
        Message<?> message = subscribeMessage(TOPIC_PREFIX + "1", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("authenticated session");
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void subscribeRejectedWhenDestinationIdIsMalformed() {
        Message<?> message = subscribeMessage(TOPIC_PREFIX + "abc", authToken(7L));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Malformed");
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void subscribePassesThroughWhenDestinationIsOutsideManagedPrefix() {
        Message<?> message = subscribeMessage("/topic/some-other-topic/1", authToken(7L));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void subscribePassesThroughWhenDestinationIsNull() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(authToken(7L));
        accessor.setSessionId("session-id");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void connectRejectedWhenTokenInvalid() {
        when(jwtService.isTokenValid(any())).thenReturn(false);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-id");
        accessor.setNativeHeader("Authorization", "Bearer not-a-real-token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Authentication required");
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void nonStompMessagePassesThrough() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository, never()).existsByConversationIdAndUserId(anyLong(), anyLong());
    }

    private static Message<?> subscribeMessage(String destination, UsernamePasswordAuthenticationToken user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("session-id");
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static UsernamePasswordAuthenticationToken authToken(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                "user@example.com",
                userId,
                List.of(new SimpleGrantedAuthority("ROLE_MENTOR")));
    }
}
