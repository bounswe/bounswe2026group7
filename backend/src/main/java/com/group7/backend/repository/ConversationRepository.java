package com.group7.backend.repository;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
