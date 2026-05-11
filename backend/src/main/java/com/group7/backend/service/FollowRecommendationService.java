package com.group7.backend.service;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.ViewerInteractionRepository;
import com.group7.backend.service.ranking.EngagementStats;
import com.group7.backend.service.ranking.EngagementStatsService;
import com.group7.backend.service.ranking.FollowRanker;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.MmrReranker;
import com.group7.backend.service.ranking.ScoreResult;
import com.group7.backend.service.ranking.coldstart.PopularityByMajorCache;
import com.group7.backend.service.ranking.graph.PersonalizedPageRankService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Follow-recommendation pipeline (#344). Orchestrates the load → score →
 * paginate flow that mirrors {@code MatchingService}: a fixed-size
 * candidate window is fetched from SQL (excluding admins, the viewer,
 * already-followed users, banned users, and mentees with private
 * profiles), the ranker scores them in memory using a pre-fetched
 * {@link FollowRecommendationContext}, and the paged page is sliced
 * from the in-memory ranking. Pages beyond the window return empty
 * content; the frontend should treat that as "end of results".
 *
 * <h2>Algorithm selection</h2>
 * Bound to {@code app.recommendations.follow.algorithm}:
 * <ul>
 *   <li><b>legacy</b> — pre-existing rule-based ranker; the {@code @Primary}
 *       advanced bean is absent, only {@code RuleBasedFollowRanker} is
 *       injected. Context contains the legacy fields only.</li>
 *   <li><b>advanced</b> — multi-signal ranker (#437). Pre-fetches PPR
 *       scores, engagement stats, viewer-interaction set, and (for
 *       cold-start) popularity-by-major. MMR diversifies the top-K when
 *       {@code app.recommendations.follow.mmr.enabled=true}.</li>
 * </ul>
 *
 * <h2>Query budget — legacy path</h2>
 * Three to four SQL statements per call regardless of page size:
 * viewer load, viewer's followees, optional second-hop edges, candidate
 * window.
 *
 * <h2>Query budget — advanced path</h2>
 * Legacy budget plus: one PPR Cypher (cached per-viewer for 10min),
 * one engagement aggregate (UNION ALL over feed_posts /
 * feed_post_comments / feed_post_shares), one viewer-interaction UNION
 * query, and one popularity-by-major query (cached per-major for 30min,
 * only when cold-start).
 */
@Service
public class FollowRecommendationService {

    /**
     * Hard cap on the viewer's followees expanded by the second-hop
     * query. A viewer who follows thousands of users would otherwise
     * push an unbounded {@code IN} list at Postgres; truncating to the
     * most-recent {@value} preserves the strongest-signal subset (per
     * {@link FollowRepository}'s "newest first" sort) while keeping the
     * query budget bounded.
     */
    static final int MAX_VIEWER_FOLLOWEES = 200;

    static final String ALGORITHM_ADVANCED = "advanced";

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FollowRanker ranker;
    private final Clock clock;
    private final FollowRecommendationProperties props;
    private final EngagementStatsService engagementService;
    private final ViewerInteractionRepository viewerInteractionRepository;
    private final PopularityByMajorCache popularityCache;
    private final MmrReranker mmr;
    /**
     * PPR service is gated behind {@code app.recommendations.follow.sync.enabled=true}
     * because it depends on the Neo4j follow-graph mirror. When the flag
     * is off the bean is absent — {@link ObjectProvider#getIfAvailable()}
     * returns null and the PPR signal degrades to "ppr-unavailable".
     */
    private final ObjectProvider<PersonalizedPageRankService> pprServiceProvider;
    private final int rankingWindow;

    public FollowRecommendationService(
            UserRepository userRepository,
            FollowRepository followRepository,
            FollowRanker ranker,
            Clock clock,
            FollowRecommendationProperties props,
            EngagementStatsService engagementService,
            ViewerInteractionRepository viewerInteractionRepository,
            PopularityByMajorCache popularityCache,
            MmrReranker mmr,
            ObjectProvider<PersonalizedPageRankService> pprServiceProvider,
            @Value("${app.matching.ranking-window:200}") int rankingWindow) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.ranker = ranker;
        this.clock = clock;
        this.props = props;
        this.engagementService = engagementService;
        this.viewerInteractionRepository = viewerInteractionRepository;
        this.popularityCache = popularityCache;
        this.mmr = mmr;
        this.pprServiceProvider = pprServiceProvider;
        this.rankingWindow = rankingWindow;
    }

    @Transactional(readOnly = true)
    public Page<FollowRecommendationResponse> recommend(Long viewerId, Pageable pageable) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + viewerId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewerId, now, PageRequest.of(0, rankingWindow));
        if (candidates.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        FollowRecommendationContext ctx = isAdvanced()
                ? buildAdvancedContext(viewer, candidates, now)
                : buildLegacyContext(viewer, now);

        List<FollowRecommendationResponse> ranked = candidates.stream()
                .map(c -> {
                    ScoreResult sr = ranker.score(c, ctx);
                    return FollowRecommendationResponse.from(c, sr);
                })
                .sorted(Comparator
                        .comparingInt(FollowRecommendationResponse::getScore).reversed()
                        .thenComparing(Comparator
                                .comparingLong(FollowRecommendationResponse::getId).reversed()))
                .toList();

        if (isAdvanced() && props.mmr().enabled() && ranked.size() > 1) {
            ranked = applyMmr(ranked, ctx);
        }

        return slicePage(ranked, pageable);
    }

    private boolean isAdvanced() {
        return ALGORITHM_ADVANCED.equalsIgnoreCase(props.algorithm());
    }

    // ── context builders ────────────────────────────────────────────────────

    private FollowRecommendationContext buildLegacyContext(User viewer, OffsetDateTime now) {
        Set<String> interestLabels = lowercasedInterests(viewer);
        Set<Long> followeeIds = loadFollowees(viewer.getId());
        Map<Long, Integer> secondHop = secondHopCount(viewer.getId(), followeeIds);
        // legacy() factory builds an empty-advanced-fields context.
        FollowRecommendationContext base = FollowRecommendationContext.legacy(
                viewer.getId(), interestLabels, followeeIds, secondHop);
        // Override now() so tests with a fixed Clock observe the same
        // timestamp the service computed.
        return new FollowRecommendationContext(
                base.viewerId(), base.viewerInterestLabels(), base.viewerFolloweeIds(),
                base.secondHopCount(), base.pprScores(), base.engagementByAuthor(),
                base.viewerInteractedAuthorIds(), base.popularityByMajor(),
                base.viewerInterestEmbedding(), base.coldStart(), now);
    }

    private FollowRecommendationContext buildAdvancedContext(User viewer,
                                                              List<User> candidates,
                                                              OffsetDateTime now) {
        Set<String> interestLabels = lowercasedInterests(viewer);
        Set<Long> followeeIds = loadFollowees(viewer.getId());
        Map<Long, Integer> secondHop = secondHopCount(viewer.getId(), followeeIds);
        Set<Long> candidateIds = new LinkedHashSet<>(candidates.size());
        for (User c : candidates) {
            candidateIds.add(c.getId());
        }

        Map<Long, Double> pprScores = Map.of();
        if (props.signals().pprEnabled()) {
            PersonalizedPageRankService ppr = pprServiceProvider.getIfAvailable();
            if (ppr != null) {
                pprScores = ppr.scoresFor(viewer.getId());
            }
        }

        Map<Long, EngagementStats> engagement = Map.of();
        if (props.signals().recentEngagementEnabled()) {
            OffsetDateTime since = now.minusDays(props.engagement().windowDays());
            engagement = engagementService.statsFor(candidateIds, since);
        }

        Set<Long> interacted = Set.of();
        if (props.signals().directInteractionEnabled()) {
            OffsetDateTime since = now.minusDays(props.coldStart().interactionWindowDays());
            interacted = Set.copyOf(viewerInteractionRepository.authorsInteractedWith(
                    viewer.getId(), since));
        }

        boolean coldStart = followeeIds.isEmpty();
        Map<Long, Long> popularity = Map.of();
        if (coldStart && props.signals().coldStartPopularityEnabled()) {
            String major = majorOf(viewer);
            if (major != null && !major.isBlank()) {
                popularity = popularityCache.forMajor(major);
            }
        }

        return new FollowRecommendationContext(
                viewer.getId(), interestLabels, followeeIds, secondHop,
                pprScores, engagement, interacted, popularity,
                /*viewerInterestEmbedding*/ null, coldStart, now);
    }

    private Set<Long> loadFollowees(Long viewerId) {
        // Viewer's followees, capped. The repo orders newest-first with
        // a stable id-tiebreaker; truncating the head preserves the
        // most-recent (and likely strongest-signal) subset.
        Pageable followeePage = PageRequest.of(0, MAX_VIEWER_FOLLOWEES);
        List<Follow> myFollows =
                followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                        viewerId, followeePage).getContent();
        Set<Long> followeeIds = new HashSet<>(myFollows.size());
        for (Follow f : myFollows) {
            followeeIds.add(f.getId().getFolloweeId());
        }
        return followeeIds;
    }

    private Map<Long, Integer> secondHopCount(Long viewerId, Set<Long> followeeIds) {
        if (followeeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> secondHop = new HashMap<>();
        List<Follow> hops = followRepository.findByIdFollowerIdIn(followeeIds);
        for (Follow f : hops) {
            Long target = f.getId().getFolloweeId();
            // Don't credit candidates that the viewer is already
            // following or that *are* the viewer.
            if (target.equals(viewerId) || followeeIds.contains(target)) {
                continue;
            }
            secondHop.merge(target, 1, Integer::sum);
        }
        return secondHop;
    }

    private static Set<String> lowercasedInterests(User user) {
        List<String> raw;
        if (user instanceof Mentor mentor) {
            raw = mentor.getInterests();
        } else if (user instanceof Mentee mentee) {
            raw = mentee.getInterests();
        } else {
            return Collections.emptySet();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>(raw.size());
        for (String label : raw) {
            if (label != null) {
                out.add(label.toLowerCase());
            }
        }
        return out;
    }

    /** Both Mentor.field and Mentee.major map to the popularity scope key. */
    private static String majorOf(User user) {
        if (user instanceof Mentor mentor) {
            return mentor.getField();
        }
        if (user instanceof Mentee mentee) {
            return mentee.getMajor();
        }
        return null;
    }

    // ── MMR ─────────────────────────────────────────────────────────────────

    /**
     * Applies MMR to the top-{@code mmr.topK} of the relevance-sorted
     * list, takes the top-{@code mmr.outputK} of the rerank, then
     * appends the un-reranked tail in its original relevance order.
     *
     * <p>Items the reranker promoted (MMR rank &lt; relevance rank) get a
     * {@code diverse-pick} factor appended to their response so the UI
     * can show a "Diverse pick" badge.
     *
     * <p>Diversity feature for each candidate is a bag-of-labels float[]
     * indexed by the set of unique interest labels across the top-K
     * input; similarity is Jaccard (computed inline from the bit values).
     */
    private List<FollowRecommendationResponse> applyMmr(
            List<FollowRecommendationResponse> ranked,
            FollowRecommendationContext ctx) {

        int topK = Math.min(props.mmr().topK(), ranked.size());
        int outputK = Math.min(props.mmr().outputK(), topK);
        if (outputK == 0) {
            return ranked;
        }

        List<FollowRecommendationResponse> head = ranked.subList(0, topK);

        // 1. Build the global label index from the head's response factors.
        //    We mine 'shared-interest:<Label>' factors emitted by the
        //    InterestOverlapFollowSignal / ColdStartPopularitySignal so
        //    the diversity vector reflects what the ranker considered.
        Map<String, Integer> labelIndex = new LinkedHashMap<>();
        for (FollowRecommendationResponse r : head) {
            for (String f : factorsOf(r)) {
                if (f != null && f.startsWith("shared-interest:")) {
                    String label = f.substring("shared-interest:".length());
                    labelIndex.computeIfAbsent(label, k -> labelIndex.size());
                }
            }
        }
        int dim = labelIndex.size();

        // 2. Build MMR items: dense float[dim] of 1.0 for each candidate's
        //    matching labels; relevance = the ranker's score scaled to [0,1].
        List<MmrReranker.Item<FollowRecommendationResponse>> mmrInput =
                new ArrayList<>(head.size());
        for (FollowRecommendationResponse r : head) {
            float[] vec = new float[dim];
            for (String f : factorsOf(r)) {
                if (f != null && f.startsWith("shared-interest:")) {
                    Integer idx = labelIndex.get(f.substring("shared-interest:".length()));
                    if (idx != null) vec[idx] = 1.0f;
                }
            }
            mmrInput.add(new MmrReranker.Item<>(r, r.getScore() / 100.0, vec));
        }

        List<MmrReranker.Reranked<FollowRecommendationResponse>> reranked =
                mmr.rerank(mmrInput, outputK, props.mmr().lambda(),
                        FollowRecommendationService::jaccardSimilarity);

        List<FollowRecommendationResponse> out = new ArrayList<>(ranked.size());
        for (MmrReranker.Reranked<FollowRecommendationResponse> r : reranked) {
            FollowRecommendationResponse resp = r.value();
            if (r.diversePick()) {
                // Response's factors list comes from List.copyOf(...) in
                // the ranker and is therefore immutable — defensively
                // rebuild with a mutable copy before appending.
                List<String> mutable = new ArrayList<>(factorsOf(resp));
                mutable.add("diverse-pick");
                resp.setFactors(mutable);
            }
            out.add(resp);
        }
        if (ranked.size() > topK) {
            out.addAll(ranked.subList(topK, ranked.size()));
        }
        return out;
    }

    private static List<String> factorsOf(FollowRecommendationResponse r) {
        return r.getFactors() == null ? List.of() : r.getFactors();
    }

    /**
     * Jaccard similarity over binary vectors — {@code |A ∩ B| / |A ∪ B|}.
     * Returns 0 for empty vectors. Used as the MMR similarity function
     * because our diversity features are binary label presence bits;
     * cosine on binary vectors would only differ by normalisation and
     * Jaccard reads more naturally for label-set comparisons.
     */
    static double jaccardSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0;
        int n = Math.min(a.length, b.length);
        int inter = 0;
        int union = 0;
        for (int i = 0; i < n; i++) {
            boolean ai = a[i] > 0.0f;
            boolean bi = b[i] > 0.0f;
            if (ai && bi) inter++;
            if (ai || bi) union++;
        }
        // Account for unequal-length tails (shouldn't happen in practice
        // since we use one shared label index, but defensively).
        for (int i = n; i < a.length; i++) if (a[i] > 0.0f) union++;
        for (int i = n; i < b.length; i++) if (b[i] > 0.0f) union++;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    /**
     * In-memory page slicer. Mirrors {@code MatchingService}'s private
     * helper; rule-of-two so each service keeps its own copy until a
     * third caller materialises and we extract to {@code support/}.
     */
    private static <T> Page<T> slicePage(List<T> ranked, Pageable pageable) {
        int total = ranked.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(ranked.subList(from, to), pageable, total);
    }
}
