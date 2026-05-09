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
 * Composite primary key for {@link FeedPostHashtag} — the pair
 * {@code (postId, tag)} that uniquely identifies a single hashtag attached
 * to a single post.
 *
 * <p>Mirrors the existing {@code ConversationParticipantId} and
 * {@code FollowId} shape in the codebase: Lombok-annotated class
 * (not a {@code record}), {@link Serializable}, {@link EqualsAndHashCode}
 * across all components — JPA requires both equals/hashCode and
 * Serializable on {@link Embeddable} composite keys.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedPostHashtagId implements Serializable {

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "tag", length = 50)
    private String tag;
}
