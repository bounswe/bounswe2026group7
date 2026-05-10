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
 * Composite PK for {@link FeedPostBookmark} (#347) — same shape as
 * {@link FeedPostLikeId}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedPostBookmarkId implements Serializable {
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "user_id")
    private Long userId;
}
