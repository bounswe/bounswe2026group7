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
 * Composite PK for {@link FeedPostCommentLike} (#483) — pair
 * {@code (commentId, userId)} uniquely identifies a like on a comment.
 * Mirrors {@link FeedPostLikeId} exactly: Lombok-annotated,
 * {@link Serializable}, {@link EqualsAndHashCode} across all components
 * (JPA requirements for {@code @EmbeddedId}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedPostCommentLikeId implements Serializable {

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "user_id")
    private Long userId;
}
