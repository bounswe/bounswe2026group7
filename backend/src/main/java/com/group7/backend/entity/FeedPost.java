package com.group7.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authored social-feed post (#348). Foundational entity for the social
 * feed surface — interactions ({@code #347}), feed reads ({@code #350}),
 * and real-time fanout ({@code #349}) all reference this row through
 * {@code feed_posts(id)} with {@code ON DELETE CASCADE}.
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li><b>Author is a {@code Long} id, not a {@code @ManyToOne}.</b>
 *       Mirrors {@link Follow} to keep list reads N+1-free; downstream
 *       feed endpoints in {@code #350} batch-fetch author display names
 *       in a single {@code findAllById} call.</li>
 *   <li><b>Soft-delete via {@link #deletedAt}.</b> {@code NULL} = visible.
 *       Read paths filter explicitly (no {@code @Where} or
 *       {@code @SQLDelete}) to avoid Hibernate's documented
 *       inconsistencies with {@code @ManyToOne} associations to
 *       soft-deleted entities — relevant here because
 *       {@link FeedPostHashtag} has a {@code @ManyToOne} back-reference.</li>
 *   <li><b>{@link Version @Version} optimistic locking.</b> Defends
 *       against concurrent PATCHes that mutate the {@link #hashtags}
 *       collection (Hibernate strategy is delete-all + reinsert) and
 *       PATCH-vs-DELETE races. Mirrors {@code User.version} (V11) and
 *       {@code LastMatchNotification.version} (V20).</li>
 *   <li><b>{@code body} is stored as raw text.</b> No HTML sanitization
 *       on store; XSS prevention is the frontend's responsibility on
 *       render. Consistent with existing {@code Message.body} handling.</li>
 *   <li><b>No {@code @PreUpdate} callback.</b> The service explicitly
 *       sets {@link #updatedAt} on every state change — this is the
 *       only correct way given that {@code @PreUpdate} does not fire when
 *       only the {@link #hashtags} collection (not the parent row)
 *       changes.</li>
 * </ul>
 *
 * <p><b>Two-phase persist for hashtags on create:</b> the parent
 * {@code FeedPost} must be saved first (so its id is generated) before
 * any {@link FeedPostHashtag} children can be built, because
 * {@code FeedPostHashtag.@MapsId("postId")} derives the embedded id's
 * {@code postId} from the parent's id. Pattern:
 * <pre>{@code
 *   FeedPost post = repository.save(new FeedPost(authorId, body));
 *   for (String tag : tags) {
 *       post.getHashtags().add(new FeedPostHashtag(post, tag));
 *   }
 *   // cascade = PERSIST flushes the children at @Transactional commit
 * }</pre>
 */
@Entity
@Table(name = "feed_posts")
@Getter
@Setter
@NoArgsConstructor
public class FeedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner of the post. Stored as a flat {@code Long} (not a
     * {@code @ManyToOne}) — see class javadoc for rationale.
     * {@code updatable = false}: the author of a post never changes; if
     * it ever needs to (e.g., admin-driven re-attribution), introduce a
     * dedicated migration path.
     */
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    /**
     * Post body. {@code TEXT} on the DB side (length up to 2000 chars
     * enforced by {@code feed_posts_body_length} CHECK + DTO {@code @Size}).
     * Default JPA mapping for {@code String} would generate
     * {@code VARCHAR(255)}, which would fail {@code ddl-auto=validate}
     * against the migration's {@code TEXT} — the explicit
     * {@code columnDefinition} keeps the entity in sync with the schema.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Server-side creation timestamp. Set by the service on create and
     * never updated thereafter — {@code updatable = false} keeps Hibernate
     * from emitting it in any UPDATE statement.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Server-side last-update timestamp. Service explicitly sets this on
     * every state change; no {@code @PreUpdate} callback (would not fire
     * for hashtag-only edits because the parent row is not dirty).
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Soft-delete flag. {@code null} = visible; non-null = deleted at the
     * given instant. Public read paths ({@code GET}) treat non-null as
     * gone (404). Author-load paths ({@code PATCH}/{@code DELETE}) load
     * the row regardless and inspect this field after the author check.
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * Optimistic-lock version. Hibernate auto-bumps on any persist that
     * touches this entity, including changes to the owning-side
     * {@link #hashtags} collection. Concurrent PATCHes / PATCH-vs-DELETE
     * races surface {@code ObjectOptimisticLockingFailureException} →
     * 409 via the global handler.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    /**
     * Hashtags attached to this post. Order on read is alphabetical
     * ({@code @OrderBy("id.tag ASC")}) — relying on insertion order from
     * a {@link LinkedHashSet} field doesn't survive the JPA round-trip
     * (Hibernate loads collections in DB-row order which is undefined
     * without an explicit {@code ORDER BY}). Alphabetical ordering gives
     * a stable, predictable response that's also human-friendly in the UI.
     *
     * <p>Cascade is narrowed to {@code PERSIST + MERGE + REMOVE} —
     * {@code CascadeType.ALL} would also include {@code REFRESH},
     * {@code DETACH}, and {@code REPLICATE} which are not desired here.
     * {@code orphanRemoval = true} ensures that removing a tag from the
     * collection deletes the underlying row at flush time.
     *
     * <p>Field-initialised as {@link LinkedHashSet} so the in-memory
     * shape remains stable while building a fresh post (before save +
     * reload), even though the on-read order is overridden by
     * {@code @OrderBy}.
     *
     * <p>{@code @BatchSize(100)} collapses the otherwise-N-queries lazy
     * fetch when an iteration accesses {@code post.getHashtags()} across
     * a list of posts (the For-You ranker loop in {@code #350} is the
     * primary call site). Without it, ranking 200 candidate posts would
     * fire 200 extra hashtag-collection queries.
     */
    @OneToMany(
            mappedBy = "post",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("id.tag ASC")
    @BatchSize(size = 100)
    private Set<FeedPostHashtag> hashtags = new LinkedHashSet<>();

    /**
     * Image attachments attached to this post. Owns the
     * {@code feed_post_attachments} junction; the {@link Attachment} entity
     * itself is uploaded and lifecycle-managed by
     * {@code AttachmentStorageService}, so there is deliberately no cascade
     * here — saving the post must not create or merge an attachment row, it
     * must only manage the junction rows.
     *
     * <p>Position is written by Hibernate via {@code @OrderColumn}. Only
     * {@code clear() + addAll()} mutations preserve the column's invariants;
     * partial mutations ({@code list.set(int, x)}, {@code list.remove(int)})
     * desynchronise the position. The service paths in
     * {@code FeedPostService.create / update} use only that pattern.
     *
     * <p>{@code @BatchSize(100)} collapses the otherwise-N-queries lazy fetch
     * across the For-You / Following / search list endpoints — for a page of
     * 20 posts Hibernate issues a single junction-join query instead of one
     * per post. Mirrors {@link #hashtags}.
     *
     * <p>Soft-delete leaves this collection untouched; the junction rows
     * survive a {@code DELETE /api/feed/posts/{id}} so a future restore
     * would recover the images. Hard-delete (cascade from user delete or
     * the planned 30-day purge in {@code #487}) drops the junction rows via
     * the DB-level {@code ON DELETE CASCADE} on {@code post_id}, and the
     * orphan-cleanup scheduler then reclaims the now-unreferenced
     * {@link Attachment} rows.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "feed_post_attachments",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "attachment_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 100)
    private List<Attachment> attachments = new ArrayList<>();

    /**
     * Convenience constructor for the create flow. Sets identity fields
     * and content; timestamps and version are populated by the service
     * before save (and by the DB DEFAULT as a defence-in-depth backstop).
     */
    public FeedPost(Long authorId, String body) {
        this.authorId = authorId;
        this.body = body;
    }
}
