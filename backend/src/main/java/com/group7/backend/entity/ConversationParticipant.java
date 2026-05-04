package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Membership of a {@link User} in a {@link Conversation}. Junction entity with
 * a composite primary key. Currently every conversation has exactly two
 * participants (1:1 chat); the model supports N-way without schema change.
 */
@Entity
@Table(name = "conversation_participants")
@Getter
@Setter
@NoArgsConstructor
public class ConversationParticipant {

    @EmbeddedId
    private ConversationParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (this.id == null && this.conversation != null && this.user != null) {
            this.id = new ConversationParticipantId(this.conversation.getId(), this.user.getId());
        }
    }
}
