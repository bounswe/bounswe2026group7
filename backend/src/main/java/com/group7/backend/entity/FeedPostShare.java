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
 * A share event on a feed post (#347). Append-only — sharing the same
 * post twice records two separate events, since shares are
 * fundamentally events ("user X shared this") not relationships
 * (unlike likes / bookmarks). No idempotency, no soft-delete.
 *
 * <p>Share-fanout (notifying the original author) is out of scope
 * here per the issue body — this entity just logs the event.
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

    public FeedPostShare(Long postId, Long sharerId) {
        this.postId = postId;
        this.sharerId = sharerId;
    }
}
