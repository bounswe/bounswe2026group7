package com.group7.backend.repository;

import com.group7.backend.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Resolves the read-time ACL for an attachment download: returns true iff
     * {@code userId} is a participant of any conversation containing a message
     * that references the given attachment.
     *
     * <p>Native query so Postgres can short-circuit at the first matching row
     * via {@code EXISTS} rather than counting every match.
     */
    @Query(value =
            "SELECT EXISTS ("
            + "  SELECT 1 FROM messages m "
            + "  JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id "
            + "  WHERE m.attachment_id = :attachmentId AND cp.user_id = :userId"
            + ")",
            nativeQuery = true)
    boolean existsAttachmentVisibleToUser(@Param("attachmentId") UUID attachmentId,
                                          @Param("userId") Long userId);

    /**
     * Resolves the read-time gate for the feed-scoped download path: returns
     * true iff the attachment is referenced from any row in
     * {@code feed_post_attachments}. Lets {@code FeedMediaDownloadController}
     * reject chat-only uploads with a uniform 404 — no per-user ACL because
     * feed images are public to any authenticated viewer.
     */
    @Query(value =
            "SELECT EXISTS ("
            + "  SELECT 1 FROM feed_post_attachments fpa "
            + "  WHERE fpa.attachment_id = :attachmentId"
            + ")",
            nativeQuery = true)
    boolean existsAsFeedAttachment(@Param("attachmentId") UUID attachmentId);

    /**
     * Counts uploads by {@code uploaderId} created strictly after {@code since}.
     * Backs the per-user hourly upload quota.
     */
    @Query("SELECT COUNT(a) FROM Attachment a "
            + "WHERE a.uploader.id = :uploaderId AND a.createdAt > :since")
    long countByUploaderSince(@Param("uploaderId") Long uploaderId,
                              @Param("since") OffsetDateTime since);

    /**
     * Returns attachments older than {@code cutoff} that are not referenced
     * by any persisted message <i>or</i> feed post <i>or</i> task
     * assignment / submission. Used by the orphan-cleanup scheduler to
     * reclaim disk and DB space for uploads that were never referenced.
     *
     * <p>The 24-hour cutoff (set at the call site) is the fairness window: a
     * client may legitimately upload, then take some time to compose the
     * outgoing message or feed post before referencing the id. Sweeping
     * immediately would race the user-visible flow.
     *
     * <p>Native query because the junction tables ({@code feed_post_attachments},
     * {@code task_assignment_attachments}, {@code task_submission_attachments})
     * are not exposed as JPA entities; the links are materialised through
     * {@code @ManyToMany} relationships on the owning entities. Four FK
     * sources reference {@code attachments(id)} — keep all four
     * {@code NOT EXISTS} clauses in sync with the migrations:
     * <ul>
     *   <li>{@code messages.attachment_id} (V15, ON DELETE SET NULL)</li>
     *   <li>{@code feed_post_attachments.attachment_id} (V41, NO ACTION)</li>
     *   <li>{@code task_assignment_attachments.attachment_id} (V23, ON DELETE CASCADE)</li>
     *   <li>{@code task_submission_attachments.attachment_id} (V23, ON DELETE CASCADE)</li>
     * </ul>
     * Missing any of these from the sweep would silently delete task PDFs /
     * task submissions when their owning attachment row goes through the
     * orphan path (the CASCADE on the junction would drop the link too,
     * making the file vanish from the UI on both web and mobile).
     */
    @Query(value =
            "SELECT * FROM attachments a "
            + "WHERE a.created_at < :cutoff "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM messages m WHERE m.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM feed_post_attachments fpa WHERE fpa.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM task_assignment_attachments taa WHERE taa.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM task_submission_attachments tsa WHERE tsa.attachment_id = a.id"
            + "  )",
            nativeQuery = true)
    List<Attachment> findOrphansOlderThan(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Deletes the attachment row only if no message, feed post, or task
     * attachment has come to reference it since the last orphan scan.
     * Returns the number of rows deleted (0 or 1).
     *
     * <p>This closes the race between {@link #findOrphansOlderThan} and the
     * scheduler's deletion: if a {@code POST /messages}, feed-post create,
     * or task-assignment update lands in the gap and references this
     * attachment, the {@code NOT EXISTS} clauses reject the delete and the
     * new owner keeps its FK intact.
     *
     * <p>The four {@code NOT EXISTS} clauses mirror
     * {@link #findOrphansOlderThan} — see that method's javadoc for the
     * canonical list of FK references.
     */
    @Modifying
    @Query(value =
            "DELETE FROM attachments a "
            + "WHERE a.id = :id "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM messages m WHERE m.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM feed_post_attachments fpa WHERE fpa.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM task_assignment_attachments taa WHERE taa.attachment_id = a.id"
            + "  ) "
            + "  AND NOT EXISTS ("
            + "    SELECT 1 FROM task_submission_attachments tsa WHERE tsa.attachment_id = a.id"
            + "  )",
            nativeQuery = true)
    int deleteIfStillOrphan(@Param("id") UUID id);
}
