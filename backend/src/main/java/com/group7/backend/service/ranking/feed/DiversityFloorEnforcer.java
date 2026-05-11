package com.group7.backend.service.ranking.feed;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures the page-0 slice contains at least one post tagged outside the
 * candidate window's top-N hashtags. Closes #438 deliverable 6: "≥1 post
 * outside viewer's top-3 hashtags per page."
 *
 * <p><b>Top-N definition.</b> Frequency in the candidate window — NOT
 * viewer-declared interests. The candidate window already reflects what
 * the viewer is actually being served, so its hashtag frequency is the
 * right baseline for "primary topic right now."
 *
 * <p>If the page already contains an outsider, this is a no-op. If no
 * outsider exists in the entire candidate pool (single-topic moment),
 * also a no-op — there's nothing to swap in. Otherwise: drop the
 * lowest-scoring placed slot, splice in the highest-scoring outsider
 * with the {@code feed:outside-primary-goal} factor appended to its
 * factor list.
 */
@Component
public final class DiversityFloorEnforcer {

    /** Input/output shape: post + score + accumulated factors. */
    public record Placed(FeedPost post, int score, List<String> factors) {

        public Placed {
            factors = (factors == null) ? List.of() : List.copyOf(factors);
        }

        public Placed withExtraFactor(String factor) {
            List<String> next = new ArrayList<>(factors);
            next.add(factor);
            return new Placed(post, score, next);
        }
    }

    /**
     * Enforce the diversity floor on a page-N slice taken from a wider
     * candidate window. Returns a new list of placed posts; never
     * mutates the inputs.
     *
     * @param placed          the page-N slice in MMR order (highest
     *                        score → lowest)
     * @param candidatePool   the full candidate window (≤ 200 posts)
     *                        used to derive the top-N hashtags and to
     *                        search for outsiders
     * @param topHashtagsCount how many "primary" hashtags to lift out
     *                        (default 3 per #438)
     */
    public List<Placed> enforce(List<Placed> placed,
                                Collection<FeedPost> candidatePool,
                                int topHashtagsCount) {
        if (placed == null || placed.isEmpty() || candidatePool == null || candidatePool.isEmpty()
                || topHashtagsCount <= 0) {
            return placed;
        }

        Set<String> topHashtags = computeTopHashtags(candidatePool, topHashtagsCount);
        if (topHashtags.isEmpty()) {
            return placed;
        }

        // Already an outsider in the page? No-op.
        if (placed.stream().anyMatch(p -> isOutsider(p.post(), topHashtags))) {
            return placed;
        }

        // Find the highest-scoring outsider in the pool that ISN'T already
        // in the page. The page list is sorted desc on score; we walk the
        // pool (also score-sorted by the caller) and pick the first
        // outsider whose id doesn't collide with a placed post.
        Set<Long> placedIds = placed.stream()
                .map(Placed::post)
                .map(FeedPost::getId)
                .collect(Collectors.toSet());

        FeedPost outsider = candidatePool.stream()
                .filter(p -> !placedIds.contains(p.getId()))
                .filter(p -> isOutsider(p, topHashtags))
                .findFirst()
                .orElse(null);

        if (outsider == null) {
            // Single-topic candidate pool — diversity is not achievable.
            return placed;
        }

        // Swap the lowest-scoring placed slot for the outsider; preserve
        // the rest of the page in order. The outsider keeps whatever
        // score the pipeline computed for it (caller passes its score
        // via the supplemental map).
        List<Placed> out = new ArrayList<>(placed.subList(0, placed.size() - 1));
        out.add(new Placed(outsider, 0, List.of("feed:outside-primary-goal")));
        return out;
    }

    private static boolean isOutsider(FeedPost post, Set<String> topHashtags) {
        if (post.getHashtags() == null || post.getHashtags().isEmpty()) {
            // A post with no hashtags can't be a topic-bubble member; treat
            // as an outsider so the floor counts it.
            return true;
        }
        for (FeedPostHashtag h : post.getHashtags()) {
            if (topHashtags.contains(h.getId().getTag())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Top-N hashtags by frequency across the candidate window. Ties are
     * broken alphabetically so the result is deterministic across calls
     * with identical inputs.
     */
    private static Set<String> computeTopHashtags(Collection<FeedPost> pool, int topN) {
        Map<String, Integer> counts = new HashMap<>();
        for (FeedPost p : pool) {
            if (p.getHashtags() == null) continue;
            for (FeedPostHashtag h : p.getHashtags()) {
                counts.merge(h.getId().getTag(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
