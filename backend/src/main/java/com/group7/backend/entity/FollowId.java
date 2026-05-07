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
 * Composite primary key for {@link Follow} — the ordered pair
 * {@code (followerId, followeeId)} that uniquely identifies a directional
 * follow edge. Order matters: {@code (A, B)} and {@code (B, A)} are two
 * distinct rows, representing A-follows-B and B-follows-A respectively.
 *
 * <p>Class form rather than {@code record} because {@code @Embeddable} records
 * require Hibernate's {@code @EmbeddableInstantiator} for full support; the
 * codebase's {@link ConversationParticipantId} sets the precedent for using
 * a Lombok-annotated class instead.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FollowId implements Serializable {

    @Column(name = "follower_id")
    private Long followerId;

    @Column(name = "followee_id")
    private Long followeeId;
}
