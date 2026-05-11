package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A like on a feed post (#347). Composite PK {@code (postId, userId)}
 * makes the like idempotent — a duplicate insert hits the PK
 * constraint and the service uses {@code INSERT ... ON CONFLICT DO
 * NOTHING} (mirroring {@code FollowRepository.upsertFollow} from #343)
 * so concurrent toggles don't corrupt state.
 *
 * <p>Thin entity by design: no {@code @ManyToOne} to {@code FeedPost}
 * or {@code User}. Like-counts are derived via {@code COUNT(*)} on
 * {@code feed_post_likes WHERE post_id = ?}; cascade-on-delete is
 * handled by the FK at the DB layer.
 */
@Entity
@Table(name = "feed_post_likes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FeedPostLike {

    @EmbeddedId
    private FeedPostLikeId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
