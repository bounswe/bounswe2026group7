package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Append-only audit row written for every spam-bot signal observed at the
 * auth surface (#345, NFR 2.2.5). Rejection-path signals (honeypot, fast
 * timing, invalid form token) precede user creation and leave {@link #user}
 * null; post-commit suspicion signals carry the user that survived initial
 * validation. Rows older than {@code app.spam.signal-retention-days} are
 * purged by {@code BotSignalCleanupScheduler}; no API exposes this table.
 *
 * <p>{@code emailHash} and {@code payloadHash} are SHA-256 hex digests so
 * the raw inputs never persist beyond what {@code users} already stores.
 */
@Entity
@Table(name = "bot_signals")
@Getter
@Setter
@NoArgsConstructor
public class BotSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 40)
    private SignalType signalType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public enum SignalType {
        HONEYPOT,
        TIMING_TOO_FAST,
        FORM_TOKEN_INVALID,
        FORM_TOKEN_EXPIRED,
        EMAIL_LIMIT,
        IP_BURST
    }
}
