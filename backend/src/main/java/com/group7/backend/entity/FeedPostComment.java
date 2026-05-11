package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A comment on a feed post (#347). Soft-delete via {@code deletedAt} so
 * threaded UIs can render "[comment removed]" placeholders without
 * losing reply context. {@code parentCommentId} is reserved nullable
 * for forward-compat (v1 is flat).
 *
 * <p>{@code @Version} optimistic-locking matches {@link FeedPost} —
 * concurrent comment edits or edit-vs-delete races surface as 409 via
 * the global handler.
 */
@Entity
@Table(name = "feed_post_comments")
@Getter
@Setter
@NoArgsConstructor
public class FeedPostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    /**
     * Reserved for future threading — v1 leaves this null. The DB
     * column is nullable and FK-cascades on parent comment delete so
     * threaded conversations can be added without an entity refactor.
     */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public FeedPostComment(Long postId, Long authorId, String body) {
        this.postId = postId;
        this.authorId = authorId;
        this.body = body;
    }
}
