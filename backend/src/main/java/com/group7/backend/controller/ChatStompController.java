package com.group7.backend.controller;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP entry point for messages sent over WebSocket. Mirrors
 * {@link MessageController#send} but reached via {@code SEND /app/chat.send/{id}}.
 * Both paths flow through the same {@link MessageService#send} call so that
 * persistence, notification, and broadcast logic stay in one place.
 *
 * <p>The persisted message is broadcast back to {@code /topic/mentorship/{id}}
 * by {@code MessageBroadcastListener} after the transaction commits — there is
 * no explicit response from this controller.
 */
@Controller
public class ChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

    private final MessageService messageService;

    public ChatStompController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send/{mentorshipId}")
    public void onSend(@DestinationVariable Long mentorshipId,
                       @Payload SendMessageRequest payload,
                       Principal principal) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getCredentials() instanceof Long senderId)) {
            log.warn("STOMP send rejected: no authenticated principal");
            throw new MessagingException("Authentication required");
        }
        messageService.send(senderId, mentorshipId, payload);
    }
}
