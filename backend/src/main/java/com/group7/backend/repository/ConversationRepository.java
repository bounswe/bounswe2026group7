package com.group7.backend.repository;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Lookup the unique conversation attached to a mentorship. Backed by the
     * partial unique index {@code uq_conversations_mentorship}.
     */
    Optional<Conversation> findByMentorshipId(Long mentorshipId);

    /**
     * Looks up a conversation by its canonical pair of user ids and kind.
     * Callers MUST pre-order the ids so that {@code lowerId < higherId};
     * this method does not normalise. The only intended caller is
     * {@code ConversationService.findOrCreateForMentorPair}, which
     * encapsulates the ordering. Backed by the partial unique index
     * {@code uq_conversations_pair_kind}.
     */
    Optional<Conversation> findByPairAIdAndPairBIdAndKind(
            Long lowerId, Long higherId, ConversationKind kind);

    /**
     * Page of MENTOR_PAIR conversations the given user participates in,
     * ordered by most-recent activity (NULLS LAST) with a deterministic
     * tiebreaker on {@code c.id} so two conversations whose latest message
     * landed in the same millisecond don't shift between requests.
     *
     * <p>Lookup goes through the participant junction (uses
     * {@code idx_conversation_participants_user}) rather than
     * {@code (pair_a_id = X OR pair_b_id = X)} which would require a per-
     * column index on {@code pair_b_id} that doesn't exist today.
     *
     * <p>Spring Data's count-query auto-derivation around an order-by
     * subquery is brittle, so the count is specified explicitly via
     * {@code countQuery}.
     */
    @Query(
            value = "SELECT c FROM Conversation c "
                    + "JOIN ConversationParticipant cp ON cp.conversation = c "
                    + "WHERE cp.user.id = :userId "
                    + "AND c.kind = com.group7.backend.entity.ConversationKind.MENTOR_PAIR "
                    + "ORDER BY (SELECT MAX(m.sentAt) FROM Message m WHERE m.conversation = c) DESC NULLS LAST, "
                    + "c.id DESC",
            countQuery = "SELECT COUNT(c) FROM Conversation c "
                    + "WHERE c.kind = com.group7.backend.entity.ConversationKind.MENTOR_PAIR "
                    + "AND EXISTS (SELECT 1 FROM ConversationParticipant cp "
                    + "WHERE cp.conversation = c AND cp.user.id = :userId)")
    Page<Conversation> findMentorPairConversationsForUserOrderedByLastMessage(
            @Param("userId") Long userId, Pageable pageable);

    /**
     * Page of ADMIN_DIRECT conversations the given user participates in,
     * ordered by most-recent activity (NULLS LAST) with deterministic
     * tie-breaking on conversation id.
     */
    @Query(
            value = "SELECT c FROM Conversation c "
                    + "JOIN ConversationParticipant cp ON cp.conversation = c "
                    + "WHERE cp.user.id = :userId "
                    + "AND c.kind = com.group7.backend.entity.ConversationKind.ADMIN_DIRECT "
                    + "ORDER BY (SELECT MAX(m.sentAt) FROM Message m WHERE m.conversation = c) DESC NULLS LAST, "
                    + "c.id DESC",
            countQuery = "SELECT COUNT(c) FROM Conversation c "
                    + "WHERE c.kind = com.group7.backend.entity.ConversationKind.ADMIN_DIRECT "
                    + "AND EXISTS (SELECT 1 FROM ConversationParticipant cp "
                    + "WHERE cp.conversation = c AND cp.user.id = :userId)")
    Page<Conversation> findAdminDirectConversationsForUserOrderedByLastMessage(
            @Param("userId") Long userId, Pageable pageable);

    /**
     * Lookup the singleton {@link ConversationKind#ADMIN_BROADCAST} row, if it
     * has been created. Backed by the partial unique index
     * {@code uq_conversations_admin_broadcast_singleton}, so at most one row
     * matches and the query is constant-time.
     */
    Optional<Conversation> findFirstByKind(ConversationKind kind);
}
