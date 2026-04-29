package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.ConversationParticipantId;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle service for {@link Conversation}. Single responsibility: decide
 * whether a conversation should exist for a given participant set, create it
 * if not, return an existing one if so. Per-message operations live in
 * {@code MessageService}.
 *
 * <p>Each policy branch (one method per {@link ConversationKind}) encapsulates
 * "who is allowed to converse with whom" — the only place this logic lives.
 * Adding a new conversation kind is additive: new method, new policy, no edits
 * to existing branches.
 *
 * <h2>Race-safe creation</h2>
 * The find-or-create flow uses the canonical Spring pattern: the outer method
 * is <strong>non-transactional</strong>, so the read and the recovery path see
 * separate, fully-committed states. The actual create runs in
 * {@link Propagation#REQUIRES_NEW} — when the partial unique index on
 * {@code conversations.mentorship_id} catches a concurrent insert and Hibernate
 * raises {@link DataIntegrityViolationException}, only the inner transaction
 * rolls back; the outer method then re-reads and returns the winner. Wrapping
 * everything in a single {@code @Transactional} would mark that outer
 * transaction rollback-only and produce {@code UnexpectedRollbackException} at
 * commit.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MentorshipRepository mentorshipRepository;
    private final ApplicationContext applicationContext;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               MentorshipRepository mentorshipRepository,
                               ApplicationContext applicationContext) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.applicationContext = applicationContext;
    }

    /**
     * Returns the conversation for {@code mentorshipId}, creating it on first
     * call. Idempotent: concurrent first-message requests collapse to a single
     * conversation row via the partial unique index on
     * {@code conversations.mentorship_id}.
     *
     * @throws ResourceNotFoundException when the mentorship does not exist
     * @throws ProfileNotVisibleException when {@code requesterId} is neither
     *         the mentor nor the mentee of that mentorship
     */
    public Conversation findOrCreateForMentorship(Long mentorshipId, Long requesterId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));
        if (!isParticipant(mentorship, requesterId)) {
            throw new ProfileNotVisibleException(
                    "You are not a participant of this mentorship");
        }
        return conversationRepository.findByMentorshipId(mentorshipId)
                .orElseGet(() -> createOrRecover(mentorship));
    }

    /**
     * Attempts to create the conversation in a fresh inner transaction; if the
     * unique constraint catches a concurrent winner, re-reads and returns it.
     * Self-invocation goes through the bean factory so the {@code REQUIRES_NEW}
     * advice fires.
     */
    private Conversation createOrRecover(Mentorship mentorship) {
        ConversationService self = applicationContext.getBean(ConversationService.class);
        try {
            return self.createForMentorshipInNewTx(mentorship);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent conversation creation for mentorshipId={}; resolving via re-find",
                    mentorship.getId());
            return conversationRepository.findByMentorshipId(mentorship.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conversation creation race resolved with no row visible — "
                                    + "transaction isolation issue?", e));
        }
    }

    /**
     * Atomic create: conversation row + two participant rows in one transaction.
     * Public so the bean proxy intercepts the call when invoked via
     * {@link #createOrRecover(Mentorship)}; not part of the service's external
     * contract.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createForMentorshipInNewTx(Mentorship mentorship) {
        Conversation conversation = new Conversation();
        conversation.setKind(ConversationKind.MENTORSHIP);
        conversation.setMentorship(mentorship);
        Conversation saved = conversationRepository.save(conversation);
        participantRepository.save(participant(saved, mentorship.getMentor()));
        participantRepository.save(participant(saved, mentorship.getMentee()));
        log.info("Conversation created: id={}, kind=MENTORSHIP, mentorshipId={}",
                saved.getId(), mentorship.getId());
        return saved;
    }

    private static ConversationParticipant participant(Conversation conversation, User user) {
        ConversationParticipant cp = new ConversationParticipant();
        cp.setConversation(conversation);
        cp.setUser(user);
        cp.setId(new ConversationParticipantId(conversation.getId(), user.getId()));
        return cp;
    }

    private static boolean isParticipant(Mentorship mentorship, Long userId) {
        return mentorship.getMentor().getId().equals(userId)
                || mentorship.getMentee().getId().equals(userId);
    }
}
