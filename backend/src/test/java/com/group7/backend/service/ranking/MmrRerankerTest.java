package com.group7.backend.service.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the generic MMR reranker. Exercises every branch of
 * the algorithm + boundary inputs to hit the ≥ 95% branch-coverage target
 * for new PR 2 code:
 *  - empty input
 *  - targetSize ≤ 0
 *  - λ clamp (negative / above 1)
 *  - lambda=1 (pure relevance — output must equal input order)
 *  - lambda=0 (pure diversity — output is greedy max-novelty)
 *  - diverse-pick flag set only when MMR rank &lt; relevance rank
 *  - targetSize larger than input
 */
class MmrRerankerTest {

    private final MmrReranker mmr = new MmrReranker();

    /** Cosine sim on equal-length float vectors; 0 for orthogonal, 1 for identical. */
    private static final ToDoubleBiFunction<float[], float[]> COSINE = (a, b) -> {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    };

    @Test
    void emptyInput_returnsEmptyOutput() {
        assertThat(mmr.rerank(List.of(), 10, 0.65, COSINE)).isEmpty();
    }

    @Test
    void nullInput_returnsEmptyOutput() {
        assertThat(mmr.rerank(null, 10, 0.65, COSINE)).isEmpty();
    }

    @Test
    void zeroTargetSize_returnsEmptyOutput() {
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("a", 1.0, new float[]{1, 0}));
        assertThat(mmr.rerank(in, 0, 0.65, COSINE)).isEmpty();
    }

    @Test
    void negativeTargetSize_returnsEmptyOutput() {
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("a", 1.0, new float[]{1, 0}));
        assertThat(mmr.rerank(in, -1, 0.65, COSINE)).isEmpty();
    }

    @Test
    void lambdaOne_equalsPureRelevanceOrder() {
        // Three items, decreasing relevance. λ=1 ignores diversity → output
        // must mirror relevance-sorted input.
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("low",    0.3, new float[]{1, 0, 0}),
                new MmrReranker.Item<>("high",   0.9, new float[]{1, 0, 0}),
                new MmrReranker.Item<>("middle", 0.6, new float[]{1, 0, 0}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 3, 1.0, COSINE);

        assertThat(out).extracting(MmrReranker.Reranked::value)
                .containsExactly("high", "middle", "low");
        // None should be flagged as diverse-pick — relevance rank == MMR rank.
        assertThat(out).extracting(MmrReranker.Reranked::diversePick)
                .containsExactly(false, false, false);
    }

    @Test
    void lambdaZero_picksDiversePathFromSecondSlotOn() {
        // Three items: "A" has highest relevance and is similar to "Asame";
        // "B" is orthogonal. λ=0 means after picking A first (highest
        // relevance), the second slot picks B (most novel) over Asame.
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("A",     1.0, new float[]{1, 0}),
                new MmrReranker.Item<>("Asame", 0.9, new float[]{1, 0}),
                new MmrReranker.Item<>("B",     0.5, new float[]{0, 1}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 3, 0.0, COSINE);

        assertThat(out).extracting(MmrReranker.Reranked::value)
                .containsExactly("A", "B", "Asame");
    }

    @Test
    void diversePickFlag_setWhenMmrLiftsItemAboveRelevanceRank() {
        // "B" has lower relevance than "Asame" but MMR with low λ should
        // promote it because Asame is highly similar to A (already picked).
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("A",     1.0, new float[]{1, 0}),
                new MmrReranker.Item<>("Asame", 0.9, new float[]{1, 0}),
                new MmrReranker.Item<>("B",     0.5, new float[]{0, 1}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 3, 0.3, COSINE);

        // A stays at index 0 (highest relevance, no penalty).
        // B is promoted from relevance-rank 2 → MMR-rank 1: diversePick=true.
        // Asame falls from relevance-rank 1 → MMR-rank 2: diversePick=false.
        assertThat(out).extracting(MmrReranker.Reranked::value)
                .containsExactly("A", "B", "Asame");
        assertThat(out.get(0).diversePick()).isFalse();
        assertThat(out.get(1).diversePick()).isTrue();
        assertThat(out.get(2).diversePick()).isFalse();
    }

    @Test
    void targetSizeLargerThanInput_returnsAllInputItems() {
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("only", 0.5, new float[]{1, 0}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 50, 0.65, COSINE);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).value()).isEqualTo("only");
    }

    @Test
    void lambdaClampedAboveOne_behavesLikeOne() {
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("low",  0.1, new float[]{1, 0}),
                new MmrReranker.Item<>("high", 0.9, new float[]{1, 0}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 2, 5.0, COSINE);

        assertThat(out).extracting(MmrReranker.Reranked::value)
                .containsExactly("high", "low");
    }

    @Test
    void lambdaClampedBelowZero_behavesLikeZero() {
        // λ=-3 clamps to 0 → pure diversity. With identical vectors the
        // first pick is still highest-relevance (no selection yet), then
        // ties on similarity, so order falls back to relevance.
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("a", 0.5, new float[]{1, 0}),
                new MmrReranker.Item<>("b", 0.7, new float[]{0, 1}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 2, -3.0, COSINE);

        assertThat(out).extracting(MmrReranker.Reranked::value)
                .containsExactly("b", "a");
    }

    @Test
    void singleItem_returnsItUnchanged() {
        List<MmrReranker.Item<String>> in = List.of(
                new MmrReranker.Item<>("alone", 0.42, new float[]{1, 1, 1}));

        List<MmrReranker.Reranked<String>> out = mmr.rerank(in, 1, 0.65, COSINE);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.value()).isEqualTo("alone");
            assertThat(r.relevance()).isEqualTo(0.42);
            assertThat(r.diversePick()).isFalse();
        });
    }
}
