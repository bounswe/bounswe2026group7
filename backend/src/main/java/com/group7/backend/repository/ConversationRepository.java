package com.group7.backend.repository;

import com.group7.backend.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Lookup the unique conversation attached to a mentorship. Backed by the
     * partial unique index {@code uq_conversations_mentorship}.
     */
    Optional<Conversation> findByMentorshipId(Long mentorshipId);
}
