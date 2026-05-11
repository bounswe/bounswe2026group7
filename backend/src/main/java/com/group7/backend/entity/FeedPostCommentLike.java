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
 * A like on a feed-post comment (#483). Composite PK
 * {@code (commentId, userId)} makes the like idempotent — a duplicate
 * insert hits the PK constraint and the service uses
 * {@code INSERT ... ON CONFLICT DO NOTHING} (mirroring
 * {@link FeedPostLike} from #347) so concurrent toggles don't corrupt
 * state.
 *
 * <p>Thin entity by design: no {@code @ManyToOne} to
 * {@link FeedPostComment} or {@code User}. Like-counts are derived via
 * {@code COUNT(*)} on {@code feed_post_comment_likes WHERE comment_id = ?};
 * cascade-on-delete is handled by the FK at the DB layer (V49).
 */
@Entity
@Table(name = "feed_post_comment_likes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FeedPostCommentLike {

    @EmbeddedId
    private FeedPostCommentLikeId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
