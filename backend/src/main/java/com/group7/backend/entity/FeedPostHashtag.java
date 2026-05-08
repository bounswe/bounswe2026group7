package com.group7.backend.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single hashtag attached to a {@link FeedPost}. Composite PK on
 * {@code (post_id, tag)} prevents duplicate tags on the same post and
 * indexes both directions.
 *
 * <p>Modelled as a separate entity (not a JPA {@code @ElementCollection}
 * value type) so future columns ({@code created_at}, {@code position},
 * {@code weight}) can be added without an entity refactor — the same
 * trap that hit {@code Mentor.interests} when it grew into
 * {@code TaggedTerm}.
 *
 * <p>Equality is defined by the composite id only ({@link #id}); the
 * back-reference to {@link FeedPost} is excluded to avoid cycles.
 */
@Entity
@Table(name = "feed_post_hashtags")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FeedPostHashtag {

    @EmbeddedId
    private FeedPostHashtagId id;

    /**
     * Back-reference to the parent post. {@link MapsId} derives the
     * embedded id's {@code postId} from {@link FeedPost#getId()}, so the
     * caller only needs to set the {@code post} reference and the {@code tag}
     * (via the constructor below).
     *
     * <p>Lazy fetch — accessing this from outside an open
     * {@code @Transactional} context will surface
     * {@code LazyInitializationException}; downstream services and mappers
     * keep DTO-mapping inside the service-layer transaction boundary.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("postId")
    @JoinColumn(name = "post_id", nullable = false)
    private FeedPost post;

    /**
     * Convenience constructor used by {@code FeedPostService} on the
     * create / update paths. Sets the back-reference and constructs the
     * embedded id with the parent's id and the tag string. The parent
     * must already be persisted (id non-null) — the create flow is
     * "save parent first, then build children with the now-known id."
     */
    public FeedPostHashtag(FeedPost post, String tag) {
        this.post = post;
        this.id = new FeedPostHashtagId(post.getId(), tag);
    }
}
