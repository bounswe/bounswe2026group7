package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A single ban imposition (#134, req 2.2.4). Each ban event creates a new
 * row; the audit history of a user's bans is the {@code created_at}-ordered
 * list of rows for that user. A ban is active iff
 * {@code lifted_at IS NULL AND expires_at > now}.
 *
 * <p>{@code expiry_notified} is the idempotency flag for
 * {@link com.group7.backend.scheduler.BanExpiryScheduler} — once the
 * "your ban expired" notification is dispatched, the row is skipped
 * forever. Auto-expiry itself is a query concern; the timer running out
 * does not require a row mutation, only the optional notification does.
 */
@Entity
@Table(name = "bans")
@Getter
@Setter
@NoArgsConstructor
public class Ban {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String reason;

    /**
     * Discriminator for the imposition path (#345). Persisted as a string
     * so DB dumps stay legible and the enum can be widened without a data
     * migration. The "clear bot flag" admin flow filters by this column so
     * a system spam-ban lift cannot accidentally touch an admin ban.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BanSource source;

    @Column(name = "ban_count", nullable = false)
    private int banCount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "lifted_at")
    private OffsetDateTime liftedAt;

    @Column(name = "lifted_by_admin_id")
    private Long liftedByAdminId;

    @Column(name = "expiry_notified", nullable = false)
    private boolean expiryNotified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isActive(OffsetDateTime now) {
        return liftedAt == null && expiresAt.isAfter(now);
    }
}
