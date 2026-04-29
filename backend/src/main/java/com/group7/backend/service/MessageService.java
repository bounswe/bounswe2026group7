package com.group7.backend.service;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Message;
import com.group7.backend.entity.User;
import com.group7.backend.event.MessageSentEvent;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Per-message operations. Operates on {@code conversationId} — agnostic of
 * whether the conversation is mentorship-scoped or mentor-pair-scoped.
 *
 * <p>Authorization rule: the caller must be a participant of the conversation.
 * For {@link ConversationKind#MENTORSHIP} conversations, sends additionally
 * require the underlying mentorship to be {@link MentorshipStatus#ACTIVE} —
 * history reads still succeed on completed mentorships.
 *
 * <p>Has zero WebSocket types — broadcast is decoupled via
 * {@link MessageSentEvent} and a transactional event listener.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserRepository userRepository,
                          ApplicationEventPublisher applicationEventPublisher,
                          NotificationEventPublisher notificationEventPublisher,
                          Clock clock) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse send(Long senderId, Long conversationId, SendMessageRequest request) {
        Conversation conversation = loadAndAuthorize(conversationId, senderId);
        assertSendable(conversation);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setAttachmentUrl(request.getAttachmentUrl());
        message.setSentAt(OffsetDateTime.now(clock));
        Message saved = messageRepository.save(message);

        Long recipientId = participantRepository
                .findOtherParticipantUserIds(conversation.getId(), senderId)
                .stream()
                .findFirst()
                .orElse(null);
        applicationEventPublisher.publishEvent(
                new MessageSentEvent(saved.getId(), conversation.getId(), senderId, recipientId));
        if (recipientId != null) {
            notificationEventPublisher.publishNewMessage(recipientId, sender.getFirstName());
        }

        log.info("Message sent: messageId={}, conversationId={}, senderId={}",
                saved.getId(), conversation.getId(), senderId);
        return MessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> list(Long requesterId, Long conversationId, Pageable pageable) {
        loadAndAuthorize(conversationId, requesterId);
        return messageRepository
                .findByConversationIdOrderBySentAtDescIdDesc(conversationId, pageable)
                .map(MessageResponse::from);
    }

    @Transactional
    public int markAllRead(Long requesterId, Long conversationId) {
        loadAndAuthorize(conversationId, requesterId);
        int updated = messageRepository.markAllAsReadForReader(
                conversationId, requesterId, OffsetDateTime.now(clock));
        log.info("Marked messages as read: conversationId={}, readerId={}, count={}",
                conversationId, requesterId, updated);
        return updated;
    }

    /**
     * Loads the conversation and asserts {@code userId} is one of its participants.
     * Throws {@link ResourceNotFoundException} (404) if missing,
     * {@link ProfileNotVisibleException} (403) if not a participant.
     */
    private Conversation loadAndAuthorize(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ProfileNotVisibleException(
                    "You are not a participant of this conversation");
        }
        return conversation;
    }

    /**
     * For mentorship-scoped conversations, sending requires the mentorship to
     * be active. Other kinds (mentor pair) have no per-send gate.
     */
    private static void assertSendable(Conversation conversation) {
        if (conversation.getKind() == ConversationKind.MENTORSHIP) {
            if (conversation.getMentorship() == null
                    || conversation.getMentorship().getStatus() != MentorshipStatus.ACTIVE) {
                throw new MentorshipRequestException(
                        "Cannot send messages on a mentorship that is not active");
            }
        }
    }

}
