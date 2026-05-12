package com.group7.backend.service;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final ConversationParticipantRepository participantRepository;
    private final ConversationCreator conversationCreator;

    public ConversationService(ConversationRepository conversationRepository,
                               MentorshipRepository mentorshipRepository,
                               UserRepository userRepository,
                               ConversationParticipantRepository participantRepository,
                               ConversationCreator conversationCreator) {
        this.conversationRepository = conversationRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
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

        // Resolve the canonical key first so we can short-circuit on the
        // common idempotent path (existing conversation) without paying for
        // two userRepository lookups. Loading the User entities is only
        // needed when we actually have to insert participant rows.
        long lower = Math.min(requesterId, otherMentorId);
        long higher = Math.max(requesterId, otherMentorId);
        Optional<Conversation> existing = conversationRepository
                .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.MENTOR_PAIR);
        if (existing.isPresent()) {
            // History survives role changes intentionally: even if a former
            // mentor was demoted, their existing peer conversations remain
            // readable. The role check below only gates new-conversation
            // creation.
            return existing.get();
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User other = userRepository.findById(otherMentorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!(requester instanceof Mentor) || !(other instanceof Mentor)) {
            throw new IllegalArgumentException(
                    "Both participants must be mentors");
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

    // ── Admin direct + broadcast (#280) ─────────────────────────────────────

    /**
     * Returns the {@link ConversationKind#ADMIN_DIRECT} conversation between
     * the calling admin and {@code otherUserId}, creating it on first call.
     * Same race-recovery shape as {@link #findOrCreateForMentorPair} — the
     * find/create flow stays non-transactional and the
     * {@link DataIntegrityViolationException} on the unique-index race is
     * resolved by re-read.
     *
     * <p>Authorization: at least one side of the pair must be an
     * {@link Admin}. Admin <-> admin is allowed (both sides admins). The
     * mentorship requirement is intentionally absent — that's the whole
     * point of this kind.
     *
     * @throws IllegalArgumentException 400 — same id on both sides, or neither
     *         side is an admin (admin DM requires admin authority).
     * @throws ResourceNotFoundException 404 — either user id is unknown.
     */
    public Conversation findOrCreateForAdminDirect(Long adminId, Long otherUserId) {
        if (Objects.equals(adminId, otherUserId)) {
            throw new IllegalArgumentException(
                    "Cannot start a conversation with yourself");
        }

        long lower = Math.min(adminId, otherUserId);
        long higher = Math.max(adminId, otherUserId);
        Optional<Conversation> existing = conversationRepository
                .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.ADMIN_DIRECT);
        if (existing.isPresent()) {
            return existing.get();
        }

        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!(adminUser instanceof Admin) && !(other instanceof Admin)) {
            throw new IllegalArgumentException(
                    "Admin direct conversations require at least one admin participant");
        }

        try {
            return conversationCreator.createForAdminDirectInNewTx(adminUser, other, lower, higher);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent admin-direct creation for ({}, {}); resolving via re-find",
                    lower, higher);
            return conversationRepository
                    .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.ADMIN_DIRECT)
                    .orElseThrow(() -> {
                        log.error("Admin-direct conversation race resolved with no row visible "
                                + "(pair=({}, {})); transaction isolation misconfigured?",
                                lower, higher, e);
                        return new IllegalStateException(
                                "Admin-direct creation race resolved with no row visible — "
                                        + "transaction isolation issue?", e);
                    });
        }
    }

    /**
     * Read-only lookup of an existing {@link ConversationKind#ADMIN_DIRECT}
     * row for the given pair, or empty if none exists. Used by the read
     * endpoints that must NOT auto-create a conversation on first access —
     * a recipient opening their inbox should not synthesise a thread.
     *
     * @param userIdA one side of the pair (order does not matter; this method
     *                normalises to {@code min/max})
     * @param userIdB the other side
     */
    public Optional<Conversation> findAdminDirectByPair(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || Objects.equals(userIdA, userIdB)) {
            return Optional.empty();
        }
        long lower = Math.min(userIdA, userIdB);
        long higher = Math.max(userIdA, userIdB);
        return conversationRepository
                .findByPairAIdAndPairBIdAndKind(lower, higher, ConversationKind.ADMIN_DIRECT);
    }

    /**
     * Read-side resolution of the singleton {@link ConversationKind#ADMIN_BROADCAST}
     * conversation. Returns {@code Optional.empty()} when no broadcast has ever
     * been sent — so a fresh-install GET doesn't synthesise a stub row.
     *
     * <p>When the singleton exists, runs a targeted "ensure caller is a
     * participant" check: a single {@code existsByConversationIdAndUserId}
     * probe, followed by the full participant resync only when the calling
     * user is missing from the participant list. That keeps the common
     * already-a-participant GET path at one extra query instead of N (one per
     * admin), while still adding a newly promoted admin on their first read so
     * they see the full backlog rather than a 403.
     *
     * <p>Callers should be the broadcast read endpoints — the write path
     * continues to call {@link #findOrCreateAdminBroadcast} which both creates
     * the singleton on first send and unconditionally resyncs.
     */
    public Optional<Conversation> findAdminBroadcastForReader(Long callerId) {
        Optional<Conversation> existing = conversationRepository.findFirstByKind(
                ConversationKind.ADMIN_BROADCAST);
        if (existing.isEmpty() || callerId == null) {
            return existing;
        }
        Conversation broadcast = existing.get();
        if (!participantRepository.existsByConversationIdAndUserId(
                broadcast.getId(), callerId)) {
            syncAdminBroadcastParticipants(broadcast);
        }
        return existing;
    }

    /**
     * Returns the singleton {@link ConversationKind#ADMIN_BROADCAST}
     * conversation, creating it on first call and re-syncing its participant
     * list to include every current admin. Re-syncing on each access is what
     * lets a newly-added admin see broadcasts going forward without a
     * dedicated "register-admin" hook — the cost is one extra query per
     * broadcast, dominated by the message insert that follows.
     *
     * <p>Race recovery: the partial unique index pins the broadcast row to
     * exactly one; concurrent first-call inserts collapse via the same
     * insert-then-fallback shape as the other find-or-create methods.
     */
    public Conversation findOrCreateAdminBroadcast() {
        Optional<Conversation> existing = conversationRepository.findFirstByKind(
                ConversationKind.ADMIN_BROADCAST);
        Conversation conversation;
        if (existing.isPresent()) {
            conversation = existing.get();
        } else {
            List<User> admins = userRepository.findAllAdmins();
            try {
                conversation = conversationCreator.createForAdminBroadcastInNewTx(admins);
            } catch (DataIntegrityViolationException e) {
                log.warn("Concurrent admin-broadcast creation; resolving via re-find");
                conversation = conversationRepository.findFirstByKind(
                        ConversationKind.ADMIN_BROADCAST)
                        .orElseThrow(() -> {
                            log.error("Admin-broadcast conversation race resolved with no row "
                                    + "visible; transaction isolation misconfigured?", e);
                            return new IllegalStateException(
                                    "Admin-broadcast creation race resolved with no row "
                                            + "visible — transaction isolation issue?", e);
                        });
            }
        }
        syncAdminBroadcastParticipants(conversation);
        return conversation;
    }

    /**
     * Adds any current admin who is not yet a participant of the broadcast
     * conversation. Idempotent: existing participants are left alone, so
     * re-running this on every send is cheap.
     */
    @Transactional
    public void syncAdminBroadcastParticipants(Conversation broadcast) {
        List<User> admins = userRepository.findAllAdmins();
        for (User admin : admins) {
            if (!participantRepository.existsByConversationIdAndUserId(
                    broadcast.getId(), admin.getId())) {
                conversationCreator.addParticipantInNewTx(broadcast, admin);
            }
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static boolean isParticipant(Mentorship mentorship, Long userId) {
        return mentorship.getMentor().getId().equals(userId)
                || mentorship.getMentee().getId().equals(userId);
    }
}
