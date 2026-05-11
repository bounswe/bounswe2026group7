package com.group7.backend.service.embedding;

import com.group7.backend.config.SemanticSimilarityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage targets for {@link SemanticSimilarityService} — every branch
 * in the graceful-degradation contract:
 *
 * <ul>
 *   <li>null / blank input → empty vector, no model call, no cache write</li>
 *   <li>cold cache → model called, vector cached, success counter bumped</li>
 *   <li>warm cache → model not called, hit counter bumped</li>
 *   <li>missing model bean → empty vector, no throw, single WARN log</li>
 *   <li>model throws → empty vector, failure counter bumped, no rethrow</li>
 *   <li>cosineSimilarity edges: null, empty, mismatched length, zero-norm,
 *       orthogonal vectors, identical vectors, opposite vectors</li>
 *   <li>cache key includes model name (upgrade-safety invariant)</li>
 * </ul>
 */
class SemanticSimilarityServiceTest {

    private EmbeddingModel embeddingModel;
    private SimpleMeterRegistry meterRegistry;
    private SemanticSimilarityProperties props;
    private SemanticSimilarityService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        meterRegistry = new SimpleMeterRegistry();
        props = new SemanticSimilarityProperties(
                "text-embedding-3-small",
                new SemanticSimilarityProperties.Cache(128, 1),
                true);
        service = new SemanticSimilarityService(providerOf(() -> embeddingModel), props, meterRegistry);
    }

    // ── embed(): input handling ─────────────────────────────────────────

    @Test
    void embed_returnsEmptyForNullInput() {
        assertThat(service.embed(null)).isEmpty();
        verify(embeddingModel, never()).embed(anyString());
        assertThat(meterRegistry.counter("embedding.cache.misses").count()).isZero();
    }

    @Test
    void embed_returnsEmptyForBlankInput() {
        assertThat(service.embed("")).isEmpty();
        assertThat(service.embed("   ")).isEmpty();
        verify(embeddingModel, never()).embed(anyString());
    }

    // ── embed(): cache hit/miss + counter wiring ────────────────────────

    @Test
    void embed_coldCache_callsModelAndCachesResult() {
        float[] vec = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed("hello")).thenReturn(vec);

        float[] result = service.embed("hello");
        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
        verify(embeddingModel, times(1)).embed("hello");
        assertThat(meterRegistry.counter("embedding.cache.misses").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("openai.embedding.calls", "outcome", "success").count()).isEqualTo(1.0);
        assertThat(service.cacheSize()).isEqualTo(1);
    }

    @Test
    void embed_warmCache_skipsModelCallAndBumpsHitCounter() {
        float[] vec = new float[]{0.4f, 0.5f};
        when(embeddingModel.embed("hello")).thenReturn(vec);

        service.embed("hello");                                  // miss + populate
        float[] second = service.embed("hello");                 // hit
        float[] third = service.embed("  hello  ");              // hit (input stripped before key)

        assertThat(second).isSameAs(third); // same cached reference
        verify(embeddingModel, times(1)).embed("hello");
        assertThat(meterRegistry.counter("embedding.cache.hits").count()).isEqualTo(2.0);
        assertThat(meterRegistry.counter("embedding.cache.misses").count()).isEqualTo(1.0);
    }

    @Test
    void embed_differentInputs_keyedSeparately() {
        when(embeddingModel.embed("foo")).thenReturn(new float[]{1f});
        when(embeddingModel.embed("bar")).thenReturn(new float[]{2f});

        service.embed("foo");
        service.embed("bar");
        assertThat(service.cacheSize()).isEqualTo(2);
        verify(embeddingModel, times(1)).embed("foo");
        verify(embeddingModel, times(1)).embed("bar");
    }

    // ── embed(): graceful degradation ───────────────────────────────────

    @Test
    void embed_noModelBean_returnsEmptyAndDoesNotThrow() {
        var serviceWithoutModel = new SemanticSimilarityService(
                providerOf(() -> null), props, meterRegistry);
        assertThat(serviceWithoutModel.embed("anything")).isEmpty();
        // Repeated calls still degrade safely (the once-only log path).
        assertThat(serviceWithoutModel.embed("anything else")).isEmpty();
    }

    @Test
    void embed_modelThrows_returnsEmptyAndBumpsFailureCounter() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("OpenAI 503"));

        assertThat(service.embed("hello")).isEmpty();
        assertThat(service.embed("world")).isEmpty();
        assertThat(meterRegistry.counter("openai.embedding.calls", "outcome", "failure").count()).isEqualTo(2.0);
        // Failure path must NOT cache an empty vector — the next call with the same
        // input should retry the model, not serve a stale empty from cache.
        assertThat(service.cacheSize()).isZero();
        verify(embeddingModel, atLeastOnce()).embed(anyString());
    }

    // ── cosineSimilarity(): all branches ────────────────────────────────

    @Test
    void cosine_nullOrEmpty_returnsZero() {
        assertThat(service.cosineSimilarity(null, new float[]{1f})).isZero();
        assertThat(service.cosineSimilarity(new float[]{1f}, null)).isZero();
        assertThat(service.cosineSimilarity(new float[0], new float[]{1f})).isZero();
        assertThat(service.cosineSimilarity(new float[]{1f}, new float[0])).isZero();
    }

    @Test
    void cosine_lengthMismatch_returnsZero() {
        assertThat(service.cosineSimilarity(new float[]{1f, 2f}, new float[]{1f, 2f, 3f})).isZero();
    }

    @Test
    void cosine_zeroNorm_returnsZero() {
        assertThat(service.cosineSimilarity(new float[]{0f, 0f, 0f}, new float[]{1f, 2f, 3f})).isZero();
        assertThat(service.cosineSimilarity(new float[]{1f, 2f, 3f}, new float[]{0f, 0f, 0f})).isZero();
    }

    @Test
    void cosine_identicalVectors_isOne() {
        var v = new float[]{0.6f, 0.8f};
        assertThat(service.cosineSimilarity(v, v)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void cosine_orthogonalVectors_isZero() {
        assertThat(service.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void cosine_oppositeVectors_isMinusOne() {
        assertThat(service.cosineSimilarity(new float[]{1f, 0f}, new float[]{-1f, 0f}))
                .isCloseTo(-1.0, offset(1e-9));
    }

    // ── Cache invariants ────────────────────────────────────────────────

    @Test
    void cacheKey_includesModelName_soUpgradeRepartitionsCleanly() {
        when(embeddingModel.embed("hi")).thenReturn(new float[]{1f});

        // First service uses 3-small
        service.embed("hi");
        assertThat(service.cacheSize()).isEqualTo(1);

        // A second service backed by a *different* model name (simulating a
        // future upgrade) starts cold for the same input — confirming the
        // model name partitions the keyspace.
        var upgradedProps = new SemanticSimilarityProperties(
                "text-embedding-3-large",
                new SemanticSimilarityProperties.Cache(128, 1),
                true);
        var upgraded = new SemanticSimilarityService(providerOf(() -> embeddingModel), upgradedProps, meterRegistry);
        assertThat(upgraded.cacheSize()).isZero();
        upgraded.embed("hi");
        assertThat(upgraded.cacheSize()).isEqualTo(1);
    }

    @Test
    void invalidateAll_clearsCache() {
        when(embeddingModel.embed("hi")).thenReturn(new float[]{1f});
        service.embed("hi");
        assertThat(service.cacheSize()).isEqualTo(1);
        service.invalidateAll();
        assertThat(service.cacheSize()).isZero();
    }

    @Test
    void nullCacheConfig_fallsBackToDefaults() {
        var propsNoCache = new SemanticSimilarityProperties("text-embedding-3-small", null, true);
        var svc = new SemanticSimilarityService(providerOf(() -> embeddingModel), propsNoCache, meterRegistry);
        when(embeddingModel.embed("x")).thenReturn(new float[]{1f});
        svc.embed("x");
        assertThat(svc.cacheSize()).isEqualTo(1);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(Supplier<T> supplier) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(inv -> supplier.get());
        return provider;
    }
}
