package com.group7.backend.service;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Message;
import com.group7.backend.entity.User;
import com.group7.backend.event.MessageSentEvent;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AttachmentRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    private final AttachmentRepository attachmentRepository;
    private final MessageResponseMapper responseMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserRepository userRepository,
                          AttachmentRepository attachmentRepository,
                          MessageResponseMapper responseMapper,
                          ApplicationEventPublisher applicationEventPublisher,
                          NotificationEventPublisher notificationEventPublisher,
                          Clock clock) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.responseMapper = responseMapper;
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
        if (request.getAttachmentId() != null) {
            message.setAttachment(loadAndAuthorizeAttachment(request.getAttachmentId(), senderId));
        }
        message.setSentAt(OffsetDateTime.now(clock));
        Message saved = messageRepository.save(message);

        // Notification fan-out: every non-sender participant gets a NEW_MESSAGE
        // notification (in-app row + FCM push when a device is registered).
        // For the existing 1:1 kinds (MENTORSHIP, MENTOR_PAIR, ADMIN_DIRECT)
        // the list has size 1 and this loop runs once. For ADMIN_BROADCAST it
        // runs once per other admin so the broadcast actually reaches every
        // admin's inbox, not just whichever participant happened to be first.
        List<Long> otherParticipantIds = participantRepository
                .findOtherParticipantUserIds(conversation.getId(), senderId);
        // MessageSentEvent.recipientId is forward-compatible-nullable per its
        // Javadoc; null signals "non-1:1, listeners route by topic" — the
        // STOMP listener broadcasts to /topic/conversation/{id} either way,
        // and the FCM fan-out below covers per-user push.
        Long eventRecipientId = conversation.getKind() == ConversationKind.ADMIN_BROADCAST
                ? null
                : otherParticipantIds.stream().findFirst().orElse(null);
        applicationEventPublisher.publishEvent(
                new MessageSentEvent(saved.getId(), conversation.getId(), senderId, eventRecipientId));
        for (Long rid : otherParticipantIds) {
            notificationEventPublisher.publishNewMessage(rid, sender.getFirstName());
        }

        log.info("Message sent: messageId={}, conversationId={}, senderId={}",
                saved.getId(), conversation.getId(), senderId);
        return responseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> list(Long requesterId, Long conversationId, Pageable pageable) {
        loadAndAuthorize(conversationId, requesterId);
        return messageRepository
                .findByConversationIdOrderBySentAtDescIdDesc(conversationId, pageable)
                .map(responseMapper::toResponse);
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
     * Resolves the requested attachment and enforces the upload-time gate:
     * the sender of a message must equal the uploader of the attachment.
     * That makes "Eve forwards Alice's uploaded URL to Bob" impossible
     * without Alice sending herself.
     *
     * @throws ResourceNotFoundException 404 — attachment id is unknown
     * @throws ProfileNotVisibleException 403 — sender is not the uploader
     */
    private Attachment loadAndAuthorizeAttachment(UUID attachmentId, Long senderId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        Long uploaderId = attachment.getUploader() != null ? attachment.getUploader().getId() : null;
        if (!Objects.equals(uploaderId, senderId)) {
            throw new ProfileNotVisibleException(
                    "You may only attach files you uploaded yourself");
        }
        return attachment;
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
