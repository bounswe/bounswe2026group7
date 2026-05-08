package com.group7.backend.repository;

import com.group7.backend.entity.LastFeedReadAt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for the per-user feed read cursor (#349).
 *
 * <p>The mutation path is the native {@link #markRead} below — not
 * {@code save()}. Two reasons:
 * <ol>
 *   <li><b>Server-side {@code clock_timestamp()}:</b> the cursor reflects
 *       the DB clock, not the JVM clock, so a future multi-instance deploy
 *       has a single source of truth for "last read" timestamps.</li>
 *   <li><b>Idempotent under contention:</b> two parallel mark-read calls
 *       both succeed and the later commit wins. Without the upsert, the
 *       second call would either need a load + save round-trip (race
 *       window) or surface a {@code DataIntegrityViolationException} on
 *       PK conflict.</li>
 * </ol>
 *
 * <p>Inherited {@link #findById(Object)} is the only read path; the
 * service maps a missing row to a Unix-epoch sentinel so the
 * unread-count query has no nullable-filter branching.
 */
@Repository
public interface LastFeedReadAtRepository extends JpaRepository<LastFeedReadAt, Long> {

    /**
     * Set the user's read cursor to the current statement-start wall
     * clock. Returns 1 on a fresh insert and 1 on an in-place update
     * (Postgres' {@code ON CONFLICT DO UPDATE} reports both as a single
     * affected row); never throws on conflict.
     *
     * <p><b>{@code clock_timestamp()}, not {@code NOW()}.</b> {@code NOW()}
     * is the start-of-transaction timestamp — two consecutive {@code
     * markRead} calls inside the same transaction would resolve to the
     * same value and the second wouldn't visibly bump the cursor.
     * {@code clock_timestamp()} is the wall-clock at statement execution,
     * so the cursor reflects the actual mark-read event regardless of
     * the surrounding transaction structure.
     *
     * <p>{@code flushAutomatically = true} ensures pending JPA writes flush
     * before the native upsert so a same-transaction read-after-write sees
     * the latest state. {@code clearAutomatically = false} keeps unrelated
     * entities in the persistence context valid.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO last_feed_read_at (user_id, last_read_at)
            VALUES (:userId, clock_timestamp())
            ON CONFLICT (user_id) DO UPDATE SET last_read_at = clock_timestamp()
            """, nativeQuery = true)
    int markRead(@Param("userId") Long userId);
}
