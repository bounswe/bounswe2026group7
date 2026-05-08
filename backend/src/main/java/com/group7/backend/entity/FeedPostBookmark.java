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
 * A bookmark on a feed post (#347). Same composite-PK pattern as
 * {@link FeedPostLike} — idempotent toggle, thin entity, cascade by FK.
 *
 * <p>{@code created_at} carries the bookmark moment so the user-scoped
 * "your bookmarks" listing can sort by recency.
 */
@Entity
@Table(name = "feed_post_bookmarks")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FeedPostBookmark {

    @EmbeddedId
    private FeedPostBookmarkId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
