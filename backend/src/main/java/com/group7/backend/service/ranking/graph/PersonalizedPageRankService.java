package com.group7.backend.service.ranking.graph;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.graph.UserScoreProjection;
import com.group7.backend.service.graph.FollowGraphWriter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Caffeine-cached façade in front of GDS Personalized PageRank.
 *
 * <p>Per-viewer cache: key = {@code viewerId}, value = {@code Map<candidateId, score>}.
 * TTL and size come from {@code app.recommendations.follow.ppr.cache-*}.
 * The cache is invalidated on every {@code FollowChangedEvent} so a fresh
 * follow doesn't sit behind stale PPR scores; if the deleted user case
 * fires ({@code USER_DELETED}), the cache is cleared entirely since any
 * viewer's PPR result could reference the now-gone user.
 *
 * <p>Activated only when {@code app.recommendations.follow.sync.enabled=true}
 * — without the sync layer there's no Neo4j mirror to read from.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class PersonalizedPageRankService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizedPageRankService.class);

    private final FollowGraphWriter graphWriter;
    private final FollowGraphProjectionService projection;
    private final FollowRepository follows;
    private final FollowRecommendationProperties props;
    private Cache<Long, Map<Long, Double>> cache;

    public PersonalizedPageRankService(FollowGraphWriter graphWriter,
                                       FollowGraphProjectionService projection,
                                       FollowRepository follows,
                                       FollowRecommendationProperties props) {
        this.graphWriter = graphWriter;
        this.projection = projection;
        this.follows = follows;
        this.props = props;
    }

    @PostConstruct
    void initCache() {
        cache = Caffeine.newBuilder()
                .maximumSize(props.ppr().cacheMaxSize())
                .expireAfterWrite(Duration.ofMinutes(props.ppr().cacheTtlMinutes()))
                .build();
    }

    /**
     * Returns the cached PPR map for {@code viewerId} or computes one. The
     * map is empty when:
     * <ul>
     *   <li>the GDS projection isn't ready yet,</li>
     *   <li>the viewer has zero followees (no seed → PPR has nothing to
     *       teleport from; the cold-start signal fires instead), or</li>
     *   <li>the Cypher call throws (caught here so the signal can degrade).</li>
     * </ul>
     */
    public Map<Long, Double> scoresFor(Long viewerId) {
        if (!projection.isReady()) {
            return Collections.emptyMap();
        }
        return cache.get(viewerId, this::computeUncached);
    }

    private Map<Long, Double> computeUncached(Long viewerId) {
        Set<Long> seeds = follows.findFolloweeIdsByFollowerId(viewerId);
        if (seeds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<UserScoreProjection> ranked = graphWriter.personalizedPageRank(
                    List.copyOf(seeds),
                    props.ppr().alpha(),
                    props.ppr().iterations());
            return ranked.stream()
                    .collect(Collectors.toMap(
                            UserScoreProjection::userId,
                            UserScoreProjection::score,
                            (a, b) -> a,
                            LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("PPR Cypher failed for viewer {} — degrading signal to no-op", viewerId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Invalidate cache on follow-graph mutations. Per-viewer for FOLLOWED /
     * UNFOLLOWED (only that viewer's PPR is affected by their own edge
     * changes); whole-cache for USER_DELETED because any viewer's cached map
     * may have referenced the now-missing user.
     */
    @EventListener
    public void onFollowChanged(FollowChangedEvent event) {
        if (cache == null) return;
        switch (event.type()) {
            case FOLLOWED, UNFOLLOWED -> cache.invalidate(event.followerId());
            case USER_DELETED -> cache.invalidateAll();
        }
    }
}
