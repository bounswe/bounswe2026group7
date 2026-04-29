package com.group7.backend.event;

import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Message;
import com.group7.backend.repository.MessageRepository;
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

    public MessageBroadcastListener(MessageRepository messageRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageSent(MessageSentEvent event) {
        // findByIdForBroadcast eager-fetches sender + conversation + mentorship so
        // MessageResponse.from(message) does not depend on Open-Session-in-View
        // for lazy initialization. Listener is robust to OSIV being disabled.
        Message message = messageRepository.findByIdForBroadcast(event.messageId()).orElse(null);
        if (message == null) {
            log.warn("Message vanished before broadcast: messageId={}", event.messageId());
            return;
        }
        String destination = DESTINATION_PREFIX + event.conversationId();
        messagingTemplate.convertAndSend(destination, MessageResponse.from(message));
    }
}
