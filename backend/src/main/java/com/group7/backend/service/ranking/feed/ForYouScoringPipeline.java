package com.group7.backend.service.ranking.feed;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.repository.AuthorAffinityRepository;
import com.group7.backend.repository.FeedEngagementCountsRepository;
import com.group7.backend.service.bandit.ThompsonSamplingService;
import com.group7.backend.service.embedding.FeedPostEmbeddingText;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.AdvancedForYouFeedRanker;
import com.group7.backend.service.ranking.FeedScoreResult;
import com.group7.backend.service.ranking.MmrReranker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end orchestrator for the advanced For-You feed: precompute →
 * score → sort → MMR → diversity floor → bandit slots → page slice.
 * Stateless and request-scoped via method-local maps (no
 * {@code @RequestScope} beans — those have AOP lifecycle gotchas under
 * async dispatch).
 *
 * <p>Bandit + diversity floor are page-0 contracts only. Later pages
 * run pure weighted+MMR; without impression tracking we can't stabilize
 * stochastic bandit picks across pages.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.feed.advanced.enabled", havingValue = "true")
public class ForYouScoringPipeline {

    private final AdvancedForYouFeedRanker ranker;
    private final FeedEngagementCountsRepository engagementCounts;
    private final AuthorAffinityRepository authorAffinity;
    private final SemanticSimilarityService semantic;
    private final MmrReranker mmr;
    private final DiversityFloorEnforcer diversityFloor;
    private final ThompsonSamplingService bandit;
    private final ForYouRecommendationProperties props;

    public ForYouScoringPipeline(AdvancedForYouFeedRanker ranker,
                                 FeedEngagementCountsRepository engagementCounts,
                                 AuthorAffinityRepository authorAffinity,
                                 SemanticSimilarityService semantic,
                                 MmrReranker mmr,
                                 DiversityFloorEnforcer diversityFloor,
                                 ThompsonSamplingService bandit,
                                 ForYouRecommendationProperties props) {
        this.ranker = ranker;
        this.engagementCounts = engagementCounts;
        this.authorAffinity = authorAffinity;
        this.semantic = semantic;
        this.mmr = mmr;
        this.diversityFloor = diversityFloor;
        this.bandit = bandit;
        this.props = props;
    }

    /**
     * Rank the candidate window and return the page-N slice in pipeline
     * order. {@code pageNumber} is 0-indexed; bandit + diversity floor
     * apply on page 0 only.
     */
    public List<RankedFeedPost> rank(List<FeedPost> candidates,
                                     Long viewerId,
                                     Set<String> viewerInterestHashtags,
                                     Set<Long> viewerFollowedAuthorIds,
                                     OffsetDateTime now,
                                     int pageSize,
                                     int pageNumber) {
        if (candidates == null || candidates.isEmpty() || pageSize <= 0) {
            return List.of();
        }

        // ── 1. Precompute the per-request context ─────────────────────────
        FeedScoringContext ctx = buildContext(
                candidates, viewerId, viewerInterestHashtags,
                viewerFollowedAuthorIds, now);

        // ── 2. Score every candidate ──────────────────────────────────────
        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (FeedPost post : candidates) {
            FeedScoreResult r = ranker.score(post, ctx);
            scored.add(new ScoredCandidate(post, r.score(), new ArrayList<>(r.factors())));
        }

        // ── 3. Sort by score desc ─────────────────────────────────────────
        scored.sort(Comparator.comparingInt(ScoredCandidate::score).reversed());

        // ── 4. MMR rerank over the top window ─────────────────────────────
        int mmrWindow = Math.min(scored.size(), pageSize * props.mmr().windowMultiplier());
        List<MmrReranker.Item<ScoredCandidate>> mmrInput = new ArrayList<>(mmrWindow);
        for (int i = 0; i < mmrWindow; i++) {
            ScoredCandidate sc = scored.get(i);
            float[] embed = ctx.postEmbeddings().getOrDefault(sc.post.getId(), new float[0]);
            // Relevance is the integer score normalized to [0, 1] for MMR's
            // weighted-sum math; embedding empty → MMR similarity = 1, so
            // empty-vector posts cannot game the diversity dimension.
            mmrInput.add(new MmrReranker.Item<>(sc, sc.score / 100.0, embed));
        }
        List<MmrReranker.Reranked<ScoredCandidate>> reranked = props.mmr().enabled()
                ? mmr.rerank(mmrInput, mmrWindow, props.mmr().lambda(), this::embeddingSimilarity)
                : passthroughRerank(mmrInput);

        // Attach feed:diverse-pick to the SINGLE most-lifted item (matches
        // mentor-side single-argmax contract). Reranked.diversePick is true
        // for every item that moved up; we keep only the maximum lift.
        int bestLiftIdx = -1;
        int bestLift = 0;
        for (int i = 0; i < reranked.size(); i++) {
            if (!reranked.get(i).diversePick()) continue;
            // diversePick implies the item's pre-MMR index > its post-MMR
            // index; we don't have a direct lift number but the LATEST
            // promoted item (largest move up) tends to be the most surprising.
            int lift = i; // proxy — lower i = higher rank
            if (bestLiftIdx == -1 || lift < bestLift) {
                bestLift = lift;
                bestLiftIdx = i;
            }
        }
        List<DiversityFloorEnforcer.Placed> page = new ArrayList<>(reranked.size());
        for (int i = 0; i < reranked.size(); i++) {
            ScoredCandidate sc = reranked.get(i).value();
            List<String> factors = sc.factors;
            if (i == bestLiftIdx) {
                factors = new ArrayList<>(factors);
                factors.add("feed:diverse-pick");
            }
            page.add(new DiversityFloorEnforcer.Placed(sc.post, sc.score, factors));
        }

        // ── 5. Slice to page size before floor/bandit ─────────────────────
        int from = pageNumber * pageSize;
        int to = Math.min(from + pageSize, page.size());
        if (from >= page.size()) {
            return List.of();
        }
        List<DiversityFloorEnforcer.Placed> slice = new ArrayList<>(page.subList(from, to));

        if (pageNumber == 0) {
            // ── 6. Diversity floor ────────────────────────────────────────
            if (props.diversityFloor().enabled()) {
                slice = new ArrayList<>(diversityFloor.enforce(
                        slice, candidates, props.diversityFloor().topHashtagsCount()));
            }

            // ── 7. Bandit slot allocation ─────────────────────────────────
            if (props.bandit().enabled()) {
                slice = applyBanditSlots(slice, scored, viewerId);
            }
        }

        return slice.stream()
                .map(p -> new RankedFeedPost(p.post(), p.score(), p.factors()))
                .toList();
    }

    /** Per-request precompute: engagement counts, affinity, embeddings. */
    private FeedScoringContext buildContext(List<FeedPost> candidates,
                                            Long viewerId,
                                            Set<String> viewerInterestHashtags,
                                            Set<Long> followedAuthorIds,
                                            OffsetDateTime now) {
        List<Long> postIds = candidates.stream().map(FeedPost::getId).toList();

        Map<Long, Integer> engagement = engagementCounts.weightedCountsFor(postIds);
        double maxLog = 0.0;
        for (int w : engagement.values()) {
            double l = Math.log1p(w);
            if (l > maxLog) maxLog = l;
        }

        Map<Long, Integer> affinity = authorAffinity.weightedCountsByAuthor(
                viewerId, now.minusDays(props.affinity().windowDays()));

        String viewerText = FeedPostEmbeddingText.forViewerInterests(viewerInterestHashtags);
        float[] viewerEmbedding = viewerText.isEmpty() ? new float[0] : semantic.embed(viewerText);

        Map<Long, float[]> postEmbeddings = new HashMap<>(candidates.size());
        for (FeedPost post : candidates) {
            String text = FeedPostEmbeddingText.forPost(post);
            float[] embed = text.isEmpty() ? new float[0] : semantic.embed(text);
            postEmbeddings.put(post.getId(), embed);
        }

        return new FeedScoringContext(
                viewerId, viewerInterestHashtags, followedAuthorIds, now,
                engagement, maxLog, affinity, viewerEmbedding, postEmbeddings);
    }

    /**
     * Bandit slot allocation: replace floor(0.1 · pageSize) of the lowest-
     * scoring placed slots with Thompson-sampled exploration picks. Skips
     * any post pinned by the diversity floor or already placed elsewhere.
     */
    private List<DiversityFloorEnforcer.Placed> applyBanditSlots(
            List<DiversityFloorEnforcer.Placed> slice,
            List<ScoredCandidate> sortedScored,
            Long viewerId) {

        int slotCount = (int) Math.floor(props.bandit().explorationRate() * slice.size());
        if (slotCount <= 0) {
            return slice;
        }

        // Per-request bandit-draw cache: one sample per distinct hashtag.
        Map<String, Double> drawCache = new HashMap<>();
        Set<String> distinctHashtags = collectDistinctHashtags(sortedScored);
        for (String tag : distinctHashtags) {
            drawCache.put(tag, bandit.samplePosterior(viewerId, tag));
        }

        // For each candidate NOT already in the page, compute the
        // max-sample over its tags. Rank by that, pick top-K.
        Set<Long> placedIds = slice.stream()
                .map(p -> p.post().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<ScoredCandidate> banditCandidates = new ArrayList<>();
        Map<Long, Double> banditScores = new HashMap<>();
        for (ScoredCandidate sc : sortedScored) {
            if (placedIds.contains(sc.post.getId())) continue;
            double best = -1.0;
            for (FeedPostHashtag h : sc.post.getHashtags()) {
                Double draw = drawCache.get(h.getId().getTag());
                if (draw != null && draw > best) best = draw;
            }
            if (best > 0) {
                banditCandidates.add(sc);
                banditScores.put(sc.post.getId(), best);
            }
        }
        banditCandidates.sort(Comparator.comparingDouble(
                (ScoredCandidate sc) -> banditScores.get(sc.post.getId())).reversed());

        // Replace the lowest-scoring placed slots (last items) with bandit
        // picks. Each replacement keeps the diversity-floor pin intact —
        // we don't touch posts already marked feed:outside-primary-goal.
        List<DiversityFloorEnforcer.Placed> out = new ArrayList<>(slice);
        int replaced = 0;
        for (int i = out.size() - 1; i >= 0 && replaced < slotCount && replaced < banditCandidates.size(); i--) {
            DiversityFloorEnforcer.Placed slot = out.get(i);
            if (slot.factors().contains("feed:outside-primary-goal")) continue;
            ScoredCandidate pick = banditCandidates.get(replaced);
            List<String> factors = new ArrayList<>(pick.factors);
            factors.add("feed:bandit-exploration");
            out.set(i, new DiversityFloorEnforcer.Placed(pick.post, pick.score, factors));
            replaced++;
        }
        return out;
    }

    private static Set<String> collectDistinctHashtags(Collection<ScoredCandidate> candidates) {
        Set<String> out = new HashSet<>();
        for (ScoredCandidate c : candidates) {
            for (FeedPostHashtag h : c.post.getHashtags()) {
                out.add(h.getId().getTag());
            }
        }
        return out;
    }

    private double embeddingSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0) {
            // Empty-vector posts can't be diversified against — treat them
            // as identical to anything else so MMR doesn't pick them out
            // for spurious "diversity."
            return 1.0;
        }
        return Math.max(0.0, semantic.cosineSimilarity(a, b));
    }

    /** MMR-off path: preserve relevance order, mark nothing as diversePick. */
    private static <T> List<MmrReranker.Reranked<T>> passthroughRerank(List<MmrReranker.Item<T>> input) {
        List<MmrReranker.Reranked<T>> out = new ArrayList<>(input.size());
        for (MmrReranker.Item<T> it : input) {
            out.add(new MmrReranker.Reranked<>(it.value(), it.relevance(), false));
        }
        return out;
    }

    /** Internal carrier — post + score + accumulating factor list. */
    private record ScoredCandidate(FeedPost post, int score, List<String> factors) {}

    /** Pipeline output: ready for FeedReadService to convert into FeedPostListItem. */
    public record RankedFeedPost(FeedPost post, int score, List<String> factors) {

        public RankedFeedPost {
            factors = (factors == null) ? List.of() : List.copyOf(factors);
        }
    }

    /** Hint for tests / metrics — reading the configured page-0 only flag. */
    @SuppressWarnings("unused")
    public boolean banditEnabled() {
        return props.bandit().enabled();
    }

    /** Visible for the test path: build the context exactly the way the
     *  pipeline does, so signal-aggregation tests can verify the
     *  precompute → score handoff without mocking the whole pipeline. */
    public FeedScoringContext debugBuildContext(List<FeedPost> candidates,
                                                Long viewerId,
                                                Set<String> viewerInterestHashtags,
                                                Set<Long> followedAuthorIds,
                                                OffsetDateTime now) {
        return buildContext(candidates, viewerId, viewerInterestHashtags, followedAuthorIds, now);
    }

}
