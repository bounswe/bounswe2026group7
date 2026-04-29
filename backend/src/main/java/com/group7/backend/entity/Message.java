package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A single chat message scoped to a mentorship. Append-only — no edit/delete
 * column today (issue #245 doesn't ask for it). The {@code readAt} column is
 * the only mutable field after persistence; bulk updated by the
 * {@code PATCH /messages/read} endpoint.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id", nullable = false)
    private Mentorship mentorship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url", length = 512)
    private String attachmentUrl;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
