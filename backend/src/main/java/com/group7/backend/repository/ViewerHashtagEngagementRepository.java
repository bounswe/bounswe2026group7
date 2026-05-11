package com.group7.backend.repository;

import com.group7.backend.entity.ViewerHashtagEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link ViewerHashtagEngagement}. The α-update path
 * goes through {@link #incrementAlpha(Long, String)} — a single atomic
 * UPSERT that's safe under concurrent engagements on the same
 * {@code (viewer, hashtag)} pair. The JPA {@code merge}/{@code save} path
 * is read-modify-write and WOULD lose updates under contention.
 */
@Repository
public interface ViewerHashtagEngagementRepository
        extends JpaRepository<ViewerHashtagEngagement, ViewerHashtagEngagement.Id> {

    /**
     * Atomic batched α-increment via Postgres UPSERT. One round-trip per
     * post regardless of tag count. Initial insert sets α=2.0
     * (1.0 prior + 1.0 engagement) so the first engagement counts in one
     * round-trip; the {@code ON CONFLICT DO UPDATE} branch adds 1.0 to
     * the existing α — note {@code viewer_hashtag_engagement.alpha + 1},
     * NOT {@code EXCLUDED.alpha + 1} (the latter would clamp α to 3.0
     * forever since EXCLUDED.alpha is the proposed-insert value 2.0).
     *
     * <p><b>Caller contract:</b> {@code hashtags} MUST be normalized
     * (via {@code HashtagNormalizer}) and deduplicated upstream.
     * Postgres raises
     * {@code "ON CONFLICT DO UPDATE command cannot affect row a second
     * time"} if {@code unnest(hashtags)} produces the same
     * {@code (user_id, hashtag)} key twice — i.e., if the input array
     * contains duplicate post-normalization tags.
     *
     * <p>{@code flushAutomatically/clearAutomatically} ensure that any
     * same-transaction read of the entity (rare in production, possible
     * in tests) doesn't return a stale persistence-context value.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO viewer_hashtag_engagement (user_id, hashtag, alpha, beta)
            SELECT :userId, t, 2.0, 1.0
            FROM unnest(CAST(:hashtags AS varchar[])) AS t
            ON CONFLICT (user_id, hashtag)
            DO UPDATE SET alpha = viewer_hashtag_engagement.alpha + 1,
                          last_updated = now()
            """, nativeQuery = true)
    void incrementAlphaBatch(@Param("userId") Long userId,
                             @Param("hashtags") String[] hashtags);

    /**
     * Single-row PK lookup for the sampler. Returns empty when the row
     * doesn't exist yet — the caller treats absent rows as the Beta(1,1)
     * prior and does NOT insert. Insertion happens only on engagement
     * via {@link #incrementAlpha}.
     */
    Optional<ViewerHashtagEngagement> findByUserIdAndHashtag(Long userId, String hashtag);
}
