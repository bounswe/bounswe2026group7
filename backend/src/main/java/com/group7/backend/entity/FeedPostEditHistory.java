package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Append-only audit of a {@link FeedPost} edit. Written by
 * {@code FeedPostService.update} immediately before any body or hashtag
 * mutation, so {@link #previousBody} and {@link #previousHashtags}
 * capture the row's prior state inside the same transaction. The
 * post-update mutation and this history insert commit together, or
 * neither commits.
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li><b>No {@code @Version}.</b> The table is append-only; rows are
 *       never UPDATEd, only INSERTed and (via post hard-delete cascade)
 *       removed.</li>
 *   <li><b>{@link #previousHashtags} stored as JSONB</b> via Hibernate 6's
 *       native {@code @JdbcTypeCode(SqlTypes.JSON)}. Storing the snapshot
 *       inside the row (rather than a many-to-many to {@code feed_post_hashtags})
 *       means a later reset of the post's hashtag set does not corrupt
 *       the audit trail.</li>
 *   <li><b>{@link #editorId} nullable</b> + FK {@code ON DELETE SET NULL}:
 *       preserves the audit row if the editor's account is removed.
 *       Today the editor is always the post's author (so the post itself
 *       cascades first via {@code post_id}), but the column shape
 *       reserves the audit-preservation behaviour for a future
 *       moderator-edit feature.</li>
 *   <li><b>{@code editedAt} server-set</b> — the service injects
 *       {@code OffsetDateTime.now()} explicitly; the {@link #onCreate}
 *       callback is a defence-in-depth backstop for callers that forget
 *       to set the field.</li>
 * </ul>
 */
@Entity
@Table(name = "feed_post_edit_history")
@Getter
@Setter
@NoArgsConstructor
public class FeedPostEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    /**
     * Snapshot of the editor at edit time. Nullable: the FK is
     * {@code ON DELETE SET NULL} so the audit row survives an editor
     * account removal.
     */
    @Column(name = "editor_id")
    private Long editorId;

    @Column(name = "previous_body", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String previousBody;

    /**
     * JSONB array of the post's hashtag values at the moment of the
     * edit. Persisted as a JSONB column via Hibernate 6 native binding
     * — unrelated to {@code feed_post_hashtags} so that subsequent
     * mutations to the post's hashtag set do not retroactively rewrite
     * history rows.
     */
    @Column(name = "previous_hashtags", nullable = false, updatable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> previousHashtags = List.of();

    @Column(name = "edited_at", nullable = false, updatable = false)
    private OffsetDateTime editedAt;

    @PrePersist
    void onCreate() {
        if (editedAt == null) {
            editedAt = OffsetDateTime.now();
        }
    }
}
