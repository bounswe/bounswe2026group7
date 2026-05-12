package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A share event on a feed post. Append-only by design: sharing the same
 * post twice records two separate events, since shares are fundamentally
 * events ("user X shared this") not relationships (unlike likes /
 * bookmarks). No soft-delete column — to undo a share, hard-delete the
 * row (the FK is ON DELETE CASCADE from both post and sharer).
 *
 * <p>The row carries three sharing modes via two flags:
 * <ul>
 *   <li>{@code is_repost = FALSE, body = NULL} — silent analytics share.
 *       The {@code POST /api/feed/posts/{id}/share} endpoint writes this
 *       shape and notifies the original author, but does not fan out to
 *       the sharer's followers and does not appear in any Following
 *       feed.</li>
 *   <li>{@code is_repost = TRUE,  body = NULL} — bare repost. The
 *       {@code POST /api/feed/posts/{id}/reposts} endpoint writes this
 *       shape; the original post surfaces in the sharer's followers'
 *       Following feed with "shared by X" attribution.</li>
 *   <li>{@code is_repost = TRUE,  body = "<commentary>"} — quote-share.
 *       Same fanout as bare repost; the commentary travels in the STOMP
 *       payload and the Following-feed row.</li>
 * </ul>
 *
 * <p>Quote-share commentary length is capped at 2000 characters,
 * matching {@code FeedPost.body}. The DB CHECK constraint
 * {@code feed_post_shares_body_nonblank} additionally rejects
 * whitespace-only bodies as defence-in-depth against a malformed write
 * path; the service normalises blank input to NULL before persisting
 * so the constraint is only a backstop.
 */
@Entity
@Table(name = "feed_post_shares")
@Getter
@Setter
@NoArgsConstructor
public class FeedPostShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "sharer_id", nullable = false, updatable = false)
    private Long sharerId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Quote-share commentary. NULL on silent shares and bare reposts.
     * Length cap of 2000 chars enforced by DB CHECK + DTO {@code @Size};
     * blank-string inputs are normalised to NULL at the service layer
     * before persist, so the non-blank CHECK is purely a defence-in-depth
     * backstop.
     */
    @Column(length = 2000)
    private String body;

    /**
     * False for silent analytics shares (the existing /share endpoint),
     * true for reposts and quote-shares (the new /reposts endpoint).
     * The Following feed surfaces only rows where this flag is true.
     */
    @Column(name = "is_repost", nullable = false)
    private boolean repost = false;

    public FeedPostShare(Long postId, Long sharerId) {
        this.postId = postId;
        this.sharerId = sharerId;
    }
}
