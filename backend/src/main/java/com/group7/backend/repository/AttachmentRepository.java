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
     * Counts uploads by {@code uploaderId} created strictly after {@code since}.
     * Backs the per-user hourly upload quota.
     */
    @Query("SELECT COUNT(a) FROM Attachment a "
            + "WHERE a.uploader.id = :uploaderId AND a.createdAt > :since")
    long countByUploaderSince(@Param("uploaderId") Long uploaderId,
                              @Param("since") OffsetDateTime since);

    /**
     * Returns attachments older than {@code cutoff} that are not referenced by
     * any persisted {@link com.group7.backend.entity.Message}. Used by the
     * orphan-cleanup scheduler to reclaim disk and DB space for uploads that
     * were never sent.
     *
     * <p>The 24-hour cutoff (set at the call site) is the fairness window: a
     * client may legitimately upload, then take some time to compose the
     * outgoing message before referencing the id. Sweeping immediately would
     * race the user-visible flow.
     */
    @Query("SELECT a FROM Attachment a "
            + "WHERE a.createdAt < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.attachment.id = a.id)")
    List<Attachment> findOrphansOlderThan(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Deletes the attachment row only if no message has come to reference it
     * since the last orphan scan. Returns the number of rows deleted (0 or 1).
     *
     * <p>This closes the race between {@link #findOrphansOlderThan} and the
     * scheduler's deletion: if a {@code POST /messages} lands in the gap and
     * persists a message with this attachment, the {@code NOT EXISTS} clause
     * rejects the delete and the just-attached message keeps its FK intact.
     */
    @Modifying
    @Query("DELETE FROM Attachment a "
            + "WHERE a.id = :id "
            + "AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.attachment.id = a.id)")
    int deleteIfStillOrphan(@Param("id") UUID id);
}
