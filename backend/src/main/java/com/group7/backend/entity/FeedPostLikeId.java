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
 * Composite PK for {@link FeedPostLike} (#347) — pair {@code (postId, userId)}
 * uniquely identifies a like. Mirrors the {@code FollowId} / {@code FeedPostHashtagId}
 * shape: Lombok-annotated, {@code Serializable}, {@code @EqualsAndHashCode}
 * across all components (JPA requirements for {@code @EmbeddedId}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedPostLikeId implements Serializable {
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "user_id")
    private Long userId;
}
