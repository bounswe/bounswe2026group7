package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for {@link ConversationParticipant}.
 *
 * <p>Class form rather than {@code record} because {@code @Embeddable} records
 * require Hibernate's {@code @EmbeddableInstantiator} for full support; the
 * codebase keeps the JPA surface conservative.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConversationParticipantId implements Serializable {

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;
}
