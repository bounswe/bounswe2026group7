package com.group7.backend.service.ranking.coldstart;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.repository.PopularityByMajorRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caffeine-backed cache of the top-50 most-followed users per major.
 * The underlying query is moderately expensive (full scan of
 * {@code follows} grouped by viewer-major) and the result is identical
 * for any cold-start user with the same major, so caching at the
 * major level is the right granularity.
 *
 * <p>TTL is configured via
 * {@code app.recommendations.follow.cold-start.cache-ttl-minutes}
 * (default 30). New mentor signups don't immediately surface — the
 * cache window bounds the staleness. For the cold-start path this is
 * an acceptable trade-off (new users see "popular in your major" as
 * "popular within the last 30 minutes' snapshot").
 *
 * <p>Returns an immutable {@link Map} keyed by candidate user id with
 * the raw follower count as the value. The signal is responsible for
 * normalising into {@code [0,1]}.
 */
@Service
public class PopularityByMajorCache {

    private final PopularityByMajorRepository repo;
    private final FollowRecommendationProperties props;
    private Cache<String, Map<Long, Long>> cache;

    public PopularityByMajorCache(PopularityByMajorRepository repo,
                                  FollowRecommendationProperties props) {
        this.repo = repo;
        this.props = props;
    }

    /**
     * Cache build moved to a {@code @PostConstruct} so it sees the fully
     * validated properties (including {@code cacheMaxSize} +
     * {@code cacheTtlMinutes}). Field-initialiser would have been earlier
     * in the bean lifecycle than constructor injection.
     */
    @PostConstruct
    void initCache() {
        FollowRecommendationProperties.ColdStart cs = props.coldStart();
        this.cache = Caffeine.newBuilder()
                .maximumSize(cs.cacheMaxSize())
                .expireAfterWrite(Duration.ofMinutes(cs.cacheTtlMinutes()))
                .build();
    }

    /**
     * Returns the popularity map for the given major. Empty map when
     * {@code major} is null or blank, or no users are scoped to that
     * major — never null.
     */
    public Map<Long, Long> forMajor(String major) {
        if (major == null || major.isBlank()) {
            return Collections.emptyMap();
        }
        return cache.get(major, this::loadFromRepo);
    }

    /** Hook for tests to clear the cache between cases. */
    public void invalidateAll() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private Map<Long, Long> loadFromRepo(String major) {
        Map<Long, Long> out = new LinkedHashMap<>();
        for (Object[] row : repo.topByMajor(major)) {
            Long userId = ((Number) row[0]).longValue();
            long count  = ((Number) row[1]).longValue();
            out.put(userId, count);
        }
        return Collections.unmodifiableMap(out);
    }
}
