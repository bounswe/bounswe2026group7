package com.group7.backend.service;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Message;
import com.group7.backend.entity.User;
import com.group7.backend.event.MessageSentEvent;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRepository;
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
 * Domain service for the messaging feature. Has zero WebSocket types — broadcast
 * is decoupled via {@link MessageSentEvent} and a transactional event listener,
 * mirroring the existing notification flow.
 *
 * <p>Authorization rule: only the mentor or mentee of the mentorship may
 * read/write its messages, and writes require the mentorship to be in
 * {@link MentorshipStatus#ACTIVE}.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final MentorshipRepository mentorshipRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public MessageService(MessageRepository messageRepository,
                          MentorshipRepository mentorshipRepository,
                          UserRepository userRepository,
                          ApplicationEventPublisher applicationEventPublisher,
                          NotificationEventPublisher notificationEventPublisher,
                          Clock clock) {
        this.messageRepository = messageRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse send(Long senderId, Long mentorshipId, SendMessageRequest request) {
        Mentorship mentorship = loadAndAuthorize(mentorshipId, senderId);
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MentorshipRequestException(
                    "Cannot send messages on a mentorship that is not active");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));

        Message message = new Message();
        message.setMentorship(mentorship);
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setAttachmentUrl(request.getAttachmentUrl());
        message.setSentAt(OffsetDateTime.now(clock));
        Message saved = messageRepository.save(message);

        Long recipientId = recipientIdOf(mentorship, senderId);
        applicationEventPublisher.publishEvent(
                new MessageSentEvent(saved.getId(), mentorship.getId(), senderId, recipientId));
        notificationEventPublisher.publishNewMessage(recipientId, sender.getFirstName());

        log.info("Message sent: messageId={}, mentorshipId={}, senderId={}",
                saved.getId(), mentorship.getId(), senderId);
        return MessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> list(Long requesterId, Long mentorshipId, Pageable pageable) {
        loadAndAuthorize(mentorshipId, requesterId);
        return messageRepository
                .findByMentorshipIdOrderBySentAtDescIdDesc(mentorshipId, pageable)
                .map(MessageResponse::from);
    }

    @Transactional
    public int markAllRead(Long requesterId, Long mentorshipId) {
        loadAndAuthorize(mentorshipId, requesterId);
        int updated = messageRepository.markAllAsReadForReader(
                mentorshipId, requesterId, OffsetDateTime.now(clock));
        log.info("Marked messages as read: mentorshipId={}, readerId={}, count={}",
                mentorshipId, requesterId, updated);
        return updated;
    }

    /**
     * Loads the mentorship and asserts {@code userId} is one of its participants.
     * Throws {@link ResourceNotFoundException} (404) if missing,
     * {@link ProfileNotVisibleException} (403) if not a participant.
     */
    private Mentorship loadAndAuthorize(Long mentorshipId, Long userId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));
        if (!isParticipant(mentorship, userId)) {
            throw new ProfileNotVisibleException(
                    "You are not a participant of this mentorship");
        }
        return mentorship;
    }

    private static boolean isParticipant(Mentorship mentorship, Long userId) {
        return mentorship.getMentor().getId().equals(userId)
                || mentorship.getMentee().getId().equals(userId);
    }

    private static Long recipientIdOf(Mentorship mentorship, Long senderId) {
        Long mentorId = mentorship.getMentor().getId();
        Long menteeId = mentorship.getMentee().getId();
        return mentorId.equals(senderId) ? menteeId : mentorId;
    }
}
