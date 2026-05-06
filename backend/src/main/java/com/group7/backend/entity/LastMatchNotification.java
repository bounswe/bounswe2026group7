package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Per-recipient dedup state for the scheduled match-found notification path (#273).
 * One row per user who has ever been notified, keyed by recipient {@code user_id}.
 * The {@code notified_match_user_id} column carries the id of whichever counterpart
 * (mentor for mentee recipients, mentee for mentor recipients) was the top match
 * at the time of the last publish.
 *
 * <p>The scheduler re-ranks each eligible user daily and compares the current
 * top match's id to {@code notifiedMatchUserId}. Equal → no notification. Different
 * (or row absent) → publish + upsert.
 *
 * <p>Deliberately flat: no {@code @ManyToOne} associations, no derived helpers.
 * The scheduler only needs equality comparison, so keeping the entity flat avoids
 * accidental lazy-load surprises in the per-user transaction.
 */
@Entity
@Table(name = "last_match_notifications")
@Getter
@Setter
@NoArgsConstructor
public class LastMatchNotification {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * The counterpart's id we last notified about. Nullable to defend the
     * table against a counterpart-row deletion that happens between two
     * scheduler runs (no FK on this column — see V20 migration comment for
     * the rationale). On a fresh row this is the current top-match id; if
     * a manual SQL clears it to NULL, the next run treats the recipient as
     * first-time and fires.
     */
    @Column(name = "notified_match_user_id")
    private Long notifiedMatchUserId;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    /**
     * Initialised to 0L explicitly so {@code Hibernate.isNew(entity)} returns
     * the same answer for both freshly-constructed and persisted entities,
     * making {@code save()} reliably take the merge() path. Concurrent
     * UPDATE from two scheduler instances → loser throws
     * {@code OptimisticLockException} → its transaction rolls back →
     * AFTER_COMMIT skips, no duplicate notification. PK uniqueness handles
     * the symmetric race for INSERTs.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
