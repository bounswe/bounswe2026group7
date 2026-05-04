package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A binary attachment uploaded by a user and referenced from at most one or
 * more {@link Message} rows. The id is generated client-side as a UUID so
 * that {@code POST /api/messages/attachments} can return it before any
 * message references it; the same id is the public download key
 * (resolved at {@code GET /api/uploads/attachments/{id}}).
 *
 * <p>Provenance: the {@link #uploader} is captured at upload time and is
 * the basis for the "sender must equal uploader" gate enforced in
 * {@code MessageService.send}. Without a row to associate the file with the
 * uploading user, that gate would not be expressible.
 */
@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
