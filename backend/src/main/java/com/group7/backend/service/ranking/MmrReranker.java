package com.group7.backend.service.ranking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

/**
 * Maximal Marginal Relevance reranker (Carbonell &amp; Goldstein, 1998).
 * Diversifies a relevance-sorted list so the top-N isn't dominated by
 * near-duplicates — closes spec 1.1.2.4 for mentor matching:
 * "Occasionally surface mentors outside the mentee's primary goal for
 * diversity."
 *
 * <p>Greedy algorithm:
 * <pre>
 * selected ← {}
 * pool ← items sorted by relevance, descending
 * while |selected| &lt; targetSize and pool not empty:
 *     for each candidate c in pool:
 *         mmrScore(c) = λ · relevance(c)
 *                     − (1 − λ) · max(sim(c, s) for s ∈ selected)
 *                       (or 0 if selected is empty)
 *     pick argmax mmrScore; move from pool to selected
 * </pre>
 *
 * <p>{@code λ ∈ [0, 1]}: 1.0 = pure relevance (a no-op), 0.0 = pure
 * diversity (ignore the relevance score entirely). The plan calls for
 * 0.7 — a strong relevance bias with a measurable diversity nudge.
 *
 * <p><b>diverse-pick semantics.</b> A reranked item is marked
 * {@code diversePick} when its position in the MMR output is
 * <em>better</em> (lower index) than its position in the original
 * relevance ordering. The UI surfaces a "Diverse pick" pill when this
 * is true, so the user understands why an off-goal mentor appeared.
 *
 * <p>Pure, side-effect-free. Stateless. Pass {@code lambda} per call
 * rather than via constructor so the same instance is reusable across
 * different configurations (and trivially mockable).
 */
@Component
public final class MmrReranker {

    /** A scored item along with the dense vector used as its diversity feature. */
    public record Item<T>(T value, double relevance, float[] embedding) {}

    /** Output entry — the same {@code T}, plus the {@code diversePick} flag. */
    public record Reranked<T>(T value, double relevance, boolean diversePick) {}

    /**
     * Rerank {@code items} with MMR and return the top {@code targetSize}
     * (or fewer if the input is shorter). The result's {@code diversePick}
     * flag is true when the item's MMR rank is better than its raw
     * relevance rank.
     *
     * <p>Empty input → empty output. {@code targetSize ≤ 0} → empty
     * output. {@code lambda} is clamped to {@code [0, 1]}.
     */
    public <T> List<Reranked<T>> rerank(List<Item<T>> items,
                                        int targetSize,
                                        double lambda,
                                        ToDoubleBiFunction<float[], float[]> similarity) {
        if (items == null || items.isEmpty() || targetSize <= 0) {
            return List.of();
        }
        double l = clamp(lambda);

        // 1. Sort once by relevance (descending) — this is also the
        //    "original ordering" we compare against for diverse-pick.
        List<Item<T>> sortedByRelevance = new ArrayList<>(items);
        sortedByRelevance.sort(Comparator.comparingDouble(Item<T>::relevance).reversed());

        // Identity-based rank map: relevanceRank.get(item) = its index in
        // the raw-score order. We use System.identityHashCode-equivalent
        // semantics by referencing the same object across both lists, so
        // an index-array lookup is sufficient.
        int n = sortedByRelevance.size();
        int outSize = Math.min(targetSize, n);

        // Pool: indices into sortedByRelevance still up for selection.
        boolean[] picked = new boolean[n];
        int[] selectionOrder = new int[outSize];

        for (int slot = 0; slot < outSize; slot++) {
            int bestIdx = -1;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < n; i++) {
                if (picked[i]) continue;
                Item<T> cand = sortedByRelevance.get(i);

                double maxSimToSelected = 0.0;
                for (int s = 0; s < slot; s++) {
                    Item<T> sel = sortedByRelevance.get(selectionOrder[s]);
                    double sim = similarity.applyAsDouble(cand.embedding(), sel.embedding());
                    if (sim > maxSimToSelected) {
                        maxSimToSelected = sim;
                    }
                }

                double mmrScore = l * cand.relevance() - (1.0 - l) * maxSimToSelected;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    bestIdx = i;
                }
            }

            selectionOrder[slot] = bestIdx;
            picked[bestIdx] = true;
        }

        // Build output, marking diverse-pick when MMR rank < relevance rank.
        List<Reranked<T>> output = new ArrayList<>(outSize);
        for (int outRank = 0; outRank < outSize; outRank++) {
            int relevanceRank = selectionOrder[outRank];
            Item<T> picked2 = sortedByRelevance.get(relevanceRank);
            boolean diverse = outRank < relevanceRank;
            output.add(new Reranked<>(picked2.value(), picked2.relevance(), diverse));
        }
        return output;
    }

    private static double clamp(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }
}
