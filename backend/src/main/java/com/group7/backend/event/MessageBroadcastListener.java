package com.group7.backend.event;

import com.group7.backend.entity.Message;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.service.MessageResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts a persisted message to subscribers of the mentorship topic
 * AFTER the publishing transaction commits. Runs synchronously in the
 * request thread — the broker dispatch is microseconds and an async hop
 * adds nondeterminism for testing without a meaningful latency win at
 * single-replica scale.
 */
@Component
public class MessageBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastListener.class);

    static final String DESTINATION_PREFIX = "/topic/conversation/";

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageResponseMapper responseMapper;

    public MessageBroadcastListener(MessageRepository messageRepository,
                                    SimpMessagingTemplate messagingTemplate,
                                    MessageResponseMapper responseMapper) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
        this.responseMapper = responseMapper;
    }

    // fallbackExecution stays at the default (false): the listener must run
    // only after a real transaction commits. If MessageService.send is ever
    // refactored to be invoked outside a transaction, we would rather fail
    // loudly here than silently broadcast a message that was never persisted
    // (findByIdForBroadcast would return empty and the recipient would never
    // see the message over the socket).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        // findByIdForBroadcast eager-fetches sender + conversation + mentorship
        // + attachment so the response mapper can build a MessageResponse
        // after the original transaction has closed without relying on
        // Open-Session-in-View for lazy initialization.
        Message message = messageRepository.findByIdForBroadcast(event.messageId()).orElse(null);
        if (message == null) {
            log.warn("Message vanished before broadcast: messageId={}", event.messageId());
            return;
        }
        String destination = DESTINATION_PREFIX + event.conversationId();
        messagingTemplate.convertAndSend(destination, responseMapper.toResponse(message));
    }
}
