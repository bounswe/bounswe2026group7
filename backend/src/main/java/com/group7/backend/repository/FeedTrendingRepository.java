package com.group7.backend.repository;

import com.group7.backend.repository.projection.TrendingHashtagTuple;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Repository for the {@code feed_trending_24h} materialized view (#487).
 * Not a {@code JpaRepository} interface — the materialized view has no
 * managed entity and we only need two operations: {@link #refreshConcurrently}
 * (DDL-ish) and {@link #findTopTrending} (a read-only ranked query).
 *
 * <p>{@link #refreshConcurrently} requires the unique index on
 * {@code (tag)} created by V45; without it Postgres rejects the
 * {@code CONCURRENTLY} form. The non-concurrent fallback would
 * {@code AccessExclusiveLock} the view against readers for the duration
 * of the refresh, which is exactly what the materialised-view
 * approach exists to avoid.
 *
 * <p>Read uses {@link Tuple} projection rather than a constructor
 * expression because the materialised view is unmanaged — Hibernate
 * cannot directly project a native query into a record without
 * extra mapping configuration. The mapping is done by hand in
 * {@link #findTopTrending}, which is concise and avoids a
 * {@code @SqlResultSetMapping} ceremony.
 */
@Repository
public class FeedTrendingRepository {

    private final EntityManager em;

    public FeedTrendingRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Refresh the materialized view without blocking concurrent reads.
     * Wrapped in its own transaction so the caller (a scheduler) does
     * not have to manage one.
     */
    @Transactional
    public void refreshConcurrently() {
        em.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY feed_trending_24h")
                .executeUpdate();
    }

    /**
     * Returns the top {@code limit} hashtags ranked by the score
     * expression (matching the V45 expression index so the read is
     * an index scan).
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TrendingHashtagTuple> findTopTrending(int limit) {
        List<Tuple> rows = em.createNativeQuery("""
                        SELECT tag, post_count, unique_likers, comment_count, latest_post_at
                        FROM feed_trending_24h
                        ORDER BY (post_count * 1.0 + unique_likers * 2.0 + comment_count * 3.0) DESC,
                                 latest_post_at DESC
                        LIMIT :lim
                        """, Tuple.class)
                .setParameter("lim", limit)
                .getResultList();
        return rows.stream()
                .map(t -> new TrendingHashtagTuple(
                        (String) t.get("tag"),
                        ((Number) t.get("post_count")).longValue(),
                        ((Number) t.get("unique_likers")).longValue(),
                        ((Number) t.get("comment_count")).longValue(),
                        toOffsetDateTime(t.get("latest_post_at"))))
                .toList();
    }

    private static OffsetDateTime toOffsetDateTime(Object raw) {
        // Postgres TIMESTAMPTZ surfaces as java.time.OffsetDateTime in
        // most setups, but some Hibernate dialects give us
        // java.sql.Timestamp. Handle both defensively so a future
        // dialect change does not break the trending endpoint.
        if (raw instanceof OffsetDateTime odt) {
            return odt;
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (raw instanceof java.time.Instant i) {
            return i.atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected latest_post_at type: " + raw.getClass());
    }
}
