package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.ConversationParticipantId;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.User;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic conversation creation in a fresh transaction. This bean exists
 * solely so {@link ConversationService} can call into a
 * {@link Propagation#REQUIRES_NEW} transactional method through a normally
 * injected dependency, without using {@code ApplicationContext.getBean(...)}
 * or {@code @Lazy} self-injection — both work but couple the service to
 * Spring internals and complicate unit testing.
 *
 * <p>The two methods are intentionally narrow: each does exactly one
 * conversation insert plus its participant rows, in a brand-new transaction.
 * If a unique-constraint violation surfaces (concurrent first-write race),
 * Hibernate raises {@link org.springframework.dao.DataIntegrityViolationException}
 * and only this transaction rolls back; the caller in {@code ConversationService}
 * recovers via a re-read.
 */
@Component
public class ConversationCreator {

    private static final Logger log = LoggerFactory.getLogger(ConversationCreator.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;

    public ConversationCreator(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createForMentorPairInNewTx(User requester, User other,
                                                   long lower, long higher) {
        Conversation conversation = new Conversation();
        conversation.setKind(ConversationKind.MENTOR_PAIR);
        conversation.setPairAId(lower);
        conversation.setPairBId(higher);
        Conversation saved = conversationRepository.save(conversation);
        participantRepository.save(participant(saved, requester));
        participantRepository.save(participant(saved, other));
        log.info("Conversation created: id={}, kind=MENTOR_PAIR, pair=({}, {})",
                saved.getId(), lower, higher);
        return saved;
    }

    /**
     * Admin DM conversation (#280). Same canonical-pair shape as MENTOR_PAIR;
     * the role gate (one side must be admin) is enforced upstream in
     * {@link ConversationService}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createForAdminDirectInNewTx(User adminSide, User otherSide,
                                                    long lower, long higher) {
        Conversation conversation = new Conversation();
        conversation.setKind(ConversationKind.ADMIN_DIRECT);
        conversation.setPairAId(lower);
        conversation.setPairBId(higher);
        Conversation saved = conversationRepository.save(conversation);
        participantRepository.save(participant(saved, adminSide));
        participantRepository.save(participant(saved, otherSide));
        log.info("Conversation created: id={}, kind=ADMIN_DIRECT, pair=({}, {})",
                saved.getId(), lower, higher);
        return saved;
    }

    /**
     * Singleton admin-broadcast conversation (#280). Concurrent first-broadcast
     * requests collapse to a single row via
     * {@code uq_conversations_admin_broadcast_singleton}. Initial participants
     * are seeded from the snapshot {@code admins} the caller passed in;
     * subsequent broadcasts re-sync the participant set.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation createForAdminBroadcastInNewTx(java.util.List<User> admins) {
        Conversation conversation = new Conversation();
        conversation.setKind(ConversationKind.ADMIN_BROADCAST);
        Conversation saved = conversationRepository.save(conversation);
        for (User admin : admins) {
            participantRepository.save(participant(saved, admin));
        }
        log.info("Conversation created: id={}, kind=ADMIN_BROADCAST, initialParticipants={}",
                saved.getId(), admins.size());
        return saved;
    }

    /**
     * Adds a single participant to an existing conversation in a fresh
     * transaction. Race-tolerant: a concurrent insert of the same
     * {@code (conversationId, userId)} pair raises a primary-key violation
     * that the caller can ignore via re-check.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addParticipantInNewTx(Conversation conversation, User user) {
        try {
            participantRepository.save(participant(conversation, user));
            log.info("Conversation participant added: conversationId={}, userId={}",
                    conversation.getId(), user.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("Concurrent participant insert raced — already a member: "
                    + "conversationId={}, userId={}", conversation.getId(), user.getId());
        }
    }

    private static ConversationParticipant participant(Conversation conversation, User user) {
        ConversationParticipant cp = new ConversationParticipant();
        cp.setConversation(conversation);
        cp.setUser(user);
        cp.setId(new ConversationParticipantId(conversation.getId(), user.getId()));
        return cp;
    }
}
