package com.group7.backend.service.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage targets for {@link MmrReranker}:
 *
 * <ul>
 *   <li>empty input / non-positive targetSize → empty output (no-op)</li>
 *   <li>{@code lambda = 1.0} → output order matches raw relevance
 *       (pure-relevance no-op); no item is marked diverse</li>
 *   <li>{@code lambda} clamped from {@code &lt; 0} or {@code &gt; 1}</li>
 *   <li>{@code lambda = 0.0} pushes a low-relevance but maximally
 *       different item up</li>
 *   <li>diverse-pick flag set when MMR rank is better than relevance rank</li>
 *   <li>targetSize larger than input → return whatever's available</li>
 *   <li>identical embeddings → diversity term is constant, MMR ≡ relevance</li>
 * </ul>
 */
class MmrRerankerTest {

    /** Cosine similarity for 2-D unit-ish vectors — used in tests. */
    private static final ToDoubleBiFunction<float[], float[]> COSINE = (a, b) -> {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    };

    private final MmrReranker reranker = new MmrReranker();

    // ── No-op branches ──────────────────────────────────────────────────

    @Test
    void nullInput_returnsEmpty() {
        assertThat(reranker.rerank(null, 5, 0.7, COSINE)).isEmpty();
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertThat(reranker.rerank(List.of(), 5, 0.7, COSINE)).isEmpty();
    }

    @Test
    void zeroTargetSize_returnsEmpty() {
        List<MmrReranker.Item<String>> input = List.of(
                new MmrReranker.Item<>("a", 10.0, new float[]{1, 0}));
        assertThat(reranker.rerank(input, 0, 0.7, COSINE)).isEmpty();
        assertThat(reranker.rerank(input, -3, 0.7, COSINE)).isEmpty();
    }

    @Test
    void targetSizeLargerThanInput_returnsAllAvailable() {
        List<MmrReranker.Item<String>> input = List.of(
                new MmrReranker.Item<>("a", 10.0, new float[]{1, 0}),
                new MmrReranker.Item<>("b",  5.0, new float[]{0, 1}));
        var out = reranker.rerank(input, 100, 0.7, COSINE);
        assertThat(out).hasSize(2);
    }

    // ── Pure-relevance (lambda = 1.0) ───────────────────────────────────

    @Test
    void lambdaOne_isPureRelevance_orderMatchesRawScore() {
        List<MmrReranker.Item<String>> input = List.of(
                new MmrReranker.Item<>("low",  1.0, new float[]{1, 0}),
                new MmrReranker.Item<>("mid",  5.0, new float[]{1, 0}),
                new MmrReranker.Item<>("top", 10.0, new float[]{1, 0}));
        var out = reranker.rerank(input, 3, 1.0, COSINE);
        assertThat(out).extracting(MmrReranker.Reranked::value).containsExactly("top", "mid", "low");
        assertThat(out).allMatch(r -> !r.diversePick()); // nothing lifted vs raw order
    }

    @Test
    void lambdaClampedAboveOne_behavesLikeOne() {
        List<MmrReranker.Item<String>> input = List.of(
                new MmrReranker.Item<>("a", 1.0, new float[]{1, 0}),
                new MmrReranker.Item<>("b", 2.0, new float[]{0, 1}));
        var out = reranker.rerank(input, 2, 1.5, COSINE);
        assertThat(out.get(0).value()).isEqualTo("b");
    }

    @Test
    void lambdaClampedBelowZero_behavesLikeZero() {
        // λ=0 → pure-diversity. First pick is still the top-relevance
        // item (no competition), second pick is whichever maximises
        // distance from the first.
        List<MmrReranker.Item<String>> input = List.of(
                new MmrReranker.Item<>("near",   5.0, new float[]{0.99f, 0.01f}),
                new MmrReranker.Item<>("far",    1.0, new float[]{0,     1   }),
                new MmrReranker.Item<>("anchor",10.0, new float[]{1,     0   }));
        var out = reranker.rerank(input, 2, -1.0, COSINE);
        assertThat(out.get(0).value()).isEqualTo("anchor");
        assertThat(out.get(1).value()).isEqualTo("far");
    }

    // ── Diversity behaviour ─────────────────────────────────────────────

    @Test
    void diversitySignalLiftsDistantLowerRelevanceItem() {
        // Three items in relevance order: A (10) ≈ B (9) > C (7).
        // A and B share the same embedding (full overlap, cos = 1.0);
        // C is orthogonal (cos = 0.0). After picking A, MMR at λ=0.3
        // scores:
        //   B  = 0.3·9 − 0.7·1.0 = 2.0
        //   C  = 0.3·7 − 0.7·0.0 = 2.1
        // C wins the diversity tie-break and gets lifted above B.
        var A = new MmrReranker.Item<>("A", 10.0, new float[]{1, 0});
        var B = new MmrReranker.Item<>("B",  9.0, new float[]{1, 0}); // near-duplicate of A
        var C = new MmrReranker.Item<>("C",  7.0, new float[]{0, 1}); // orthogonal

        var out = reranker.rerank(List.of(A, B, C), 3, 0.3, COSINE);

        assertThat(out.get(0).value()).isEqualTo("A");
        assertThat(out.get(1).value()).isEqualTo("C");  // lifted past B
        assertThat(out.get(2).value()).isEqualTo("B");

        // C was relevance-rank 2 but ended at MMR-rank 1 → diversePick = true.
        assertThat(out.get(1).diversePick()).isTrue();
        // A and B retained or fell to their own relevance rank → not lifted.
        assertThat(out.get(0).diversePick()).isFalse();
        assertThat(out.get(2).diversePick()).isFalse();
    }

    @Test
    void identicalEmbeddings_pureRelevanceOrderEvenWithLowLambda() {
        // When every embedding is identical, the diversity term is
        // constant (every pairwise sim is 1.0); the ordering reduces to
        // pure relevance regardless of λ.
        var items = List.of(
                new MmrReranker.Item<>("x", 3.0, new float[]{1, 1}),
                new MmrReranker.Item<>("y", 7.0, new float[]{1, 1}),
                new MmrReranker.Item<>("z", 5.0, new float[]{1, 1}));
        var out = reranker.rerank(items, 3, 0.2, COSINE);
        assertThat(out).extracting(MmrReranker.Reranked::value).containsExactly("y", "z", "x");
        assertThat(out).allMatch(r -> !r.diversePick());
    }

    // ── Output integrity ────────────────────────────────────────────────

    @Test
    void rerank_preservesValuesAndRelevanceScores() {
        var items = List.of(
                new MmrReranker.Item<>("a", 4.2, new float[]{1, 0}),
                new MmrReranker.Item<>("b", 1.7, new float[]{0, 1}));
        var out = reranker.rerank(items, 2, 0.5, COSINE);
        assertThat(out).extracting(MmrReranker.Reranked::value).containsExactlyInAnyOrder("a", "b");
        // Scores preserved verbatim from the input items, no double conversion or rounding.
        assertThat(out).extracting(MmrReranker.Reranked::relevance).containsExactlyInAnyOrder(4.2, 1.7);
    }
}
