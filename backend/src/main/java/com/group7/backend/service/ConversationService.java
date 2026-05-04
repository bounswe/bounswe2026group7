package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.util.Objects;
import java.util.Optional;

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
 * The find-or-create flow uses the canonical Spring pattern: this service
 * stays <strong>non-transactional</strong>, so the read and the recovery path
 * see separate, fully-committed states. The actual creation is delegated to
 * {@link ConversationCreator}, whose methods run with
 * {@link Propagation#REQUIRES_NEW}. When a partial unique index catches a
 * concurrent insert and Hibernate raises {@link DataIntegrityViolationException},
 * only the inner transaction rolls back; this service then re-reads and
 * returns the winner. Wrapping these methods in a single {@code @Transactional}
 * would mark the outer transaction rollback-only and produce
 * {@code UnexpectedRollbackException} at commit instead of a clean re-find.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MentorshipRepository mentorshipRepository;
    private final UserRepository userRepository;
    private final ConversationCreator conversationCreator;

    public ConversationService(ConversationRepository conversationRepository,
                               MentorshipRepository mentorshipRepository,
                               UserRepository userRepository,
                               ConversationCreator conversationCreator) {
        this.conversationRepository = conversationRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userRepository = userRepository;
        this.conversationCreator = conversationCreator;
    }

    // ── Mentorship-scoped conversations (issue #245) ────────────────────────

    /**
     * Returns the conversation for {@code mentorshipId}, creating it on first
     * call. Idempotent: concurrent first-message requests collapse to a single
     * conversation row via the partial unique index on
     * {@code conversations.mentorship_id}.
     *
     * <p>Creation is gated on the mentorship being {@link MentorshipStatus#ACTIVE}.
     * If the mentorship is not active and no conversation exists yet, this
     * throws {@link ResourceNotFoundException}: read paths
     * ({@code GET /messages}, {@code PATCH /read}) must not silently insert
     * empty conversation rows for rejected/completed mentorships nor leak
     * participation by returning 200. An already-existing conversation
     * (created while the mentorship was active) is always returned so history
     * remains readable after the mentorship reaches a terminal state.
     *
     * @throws ResourceNotFoundException when the mentorship does not exist,
     *         or when no conversation exists yet for a non-active mentorship
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
        Optional<Conversation> existing = conversationRepository.findByMentorshipId(mentorshipId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        try {
            return conversationCreator.createForMentorshipInNewTx(mentorship);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent conversation creation for mentorshipId={}; resolving via re-find",
                    mentorshipId);
            return conversationRepository.findByMentorshipId(mentorshipId)
                    .orElseThrow(() -> {
                        log.error("Mentorship conversation race resolved with no row visible "
                                + "(mentorshipId={}); transaction isolation misconfigured?",
                                mentorshipId, e);
                        return new IllegalStateException(
                                "Conversation creation race resolved with no row visible — "
                                        + "transaction isolation issue?", e);
                    });
        }
    }

    // ── Mentor-pair conversations (issue #284) ─────────────────────────────

    /**
     * Returns the mentor-pair conversation for the unordered pair
     * {@code (requesterId, otherMentorId)}, creating it on first call.
     * Idempotent: concurrent first-message requests collapse via
     * {@code uq_conversations_pair_kind}, the same insert-then-fallback shape
     * used for mentorship conversations.
     *
     * <p>Both participants must be {@link Mentor} instances. The role check
     * fires regardless of which side initiated, so the ordering of the
     * arguments does not matter for authorization purposes — only for the
     * canonical key, which the method computes internally. Callers never pass
     * raw ordered ids to the repository.
     *
     * <p>Like {@link #findOrCreateForMentorship}, this method MUST stay
     * non-transactional. Wrapping it in {@code @Transactional} would cause the
     * inner-tx {@link DataIntegrityViolationException} on the race path to
     * mark the outer transaction rollback-only, producing
     * {@code UnexpectedRollbackException} at commit instead of a clean
     * re-find.
     *
     * @throws IllegalArgumentException 400 — same id on both sides, or either
     *         user is not a mentor (the action is invalid input, not an
     *         authorization mismatch).
     * @throws ResourceNotFoundException 404 — either user id is unknown.
     */
    public Conversation findOrCreateForMentorPair(Long requesterId, Long otherMentorId) {
        if (Objects.equals(requesterId, otherMentorId)) {
            throw new IllegalArgumentException(
                    "Cannot start a conversation with yourself");
        }
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User other = userRepository.findById(otherMentorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!(requester instanceof Mentor) || !(other instanceof Mentor)) {
            throw new IllegalArgumentException(
                    "Both participants must be mentors");
        }

        long lower = Math.min(requesterId, otherMentorId);
        long higher = Math.max(requesterId, otherMentorId);
        Optional<Conversation> existing = conversationRepository
                .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.MENTOR_PAIR);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return conversationCreator.createForMentorPairInNewTx(requester, other, lower, higher);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent mentor-pair creation for ({}, {}); resolving via re-find",
                    lower, higher);
            return conversationRepository
                    .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.MENTOR_PAIR)
                    .orElseThrow(() -> {
                        log.error("Mentor-pair conversation race resolved with no row visible "
                                + "(pair=({}, {})); transaction isolation misconfigured?",
                                lower, higher, e);
                        return new IllegalStateException(
                                "Mentor-pair creation race resolved with no row visible — "
                                        + "transaction isolation issue?", e);
                    });
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static boolean isParticipant(Mentorship mentorship, Long userId) {
        return mentorship.getMentor().getId().equals(userId)
                || mentorship.getMentee().getId().equals(userId);
    }
}
