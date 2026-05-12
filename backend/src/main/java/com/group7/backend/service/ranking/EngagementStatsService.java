package com.group7.backend.service.ranking;

import com.group7.backend.repository.EngagementStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin service wrapper that turns the native JDBC aggregate output of
 * {@link EngagementStatsRepository#aggregateByAuthor} into a typed
 * {@code Map<Long, EngagementStats>} keyed by candidate id.
 *
 * <p>Read-only — invoked once per recommendation request by
 * {@code FollowRecommendationService.buildAdvancedContext}.
 *
 * <p>Defensive: returns an empty map for an empty id collection rather
 * than letting the JPQL {@code IN ()} explode against a Postgres syntax
 * error.
 */
@Service
public class EngagementStatsService {

    private final EngagementStatsRepository repo;

    public EngagementStatsService(EngagementStatsRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Map<Long, EngagementStats> statsFor(Collection<Long> candidateIds,
                                               OffsetDateTime since) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, EngagementStats> out = new LinkedHashMap<>();
        for (Object[] row : repo.aggregateByAuthor(candidateIds, since)) {
            Long authorId = ((Number) row[0]).longValue();
            long posts    = ((Number) row[1]).longValue();
            long comments = ((Number) row[2]).longValue();
            long shares   = ((Number) row[3]).longValue();
            OffsetDateTime lastActive = toOffsetDateTime(row[4]);
            out.put(authorId, new EngagementStats(posts, comments, shares, lastActive));
        }
        return out;
    }

    /**
     * Hibernate's native query may return a {@code java.sql.Timestamp} or
     * an {@code OffsetDateTime} depending on the dialect; normalise here
     * so the consumer sees only {@code OffsetDateTime}.
     */
    private static OffsetDateTime toOffsetDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof OffsetDateTime odt) {
            return odt;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (raw instanceof java.time.Instant inst) {
            return inst.atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException(
                "Unexpected last_active column type: " + raw.getClass());
    }
}
