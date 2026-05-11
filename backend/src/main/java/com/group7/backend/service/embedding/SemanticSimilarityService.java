package com.group7.backend.service.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.group7.backend.config.SemanticSimilarityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Caches OpenAI embeddings per ({@code model}, normalized-text). Drives
 * the {@code semantic-match} signal in the advanced mentor ranker;
 * reusable verbatim by the follow-recommendation and feed surfaces —
 * nothing here is mentor-specific.
 *
 * <p><b>Graceful-degradation contract.</b> If the {@code OPENAI_API_KEY}
 * is unset, the {@link EmbeddingModel} bean is absent, or the OpenAI
 * call throws, this service returns {@code new float[0]} and logs once
 * at WARN. Callers must treat an empty vector as "score this signal 0"
 * (the ranker emits a {@code semantic-unavailable} factor in that case).
 * The service never throws — a 500 from the matcher because OpenAI is
 * down is worse than a partial recommendation.
 *
 * <p><b>Cache key.</b> {@code <model-name>:<sha-256(normalized text)>}.
 * Including the model name means a future upgrade (3-small → 3-large)
 * partitions the cache cleanly; stale entries die out via Caffeine LRU.
 *
 * <p><b>Threading.</b> Caffeine's {@code put}/{@code getIfPresent} are
 * thread-safe; concurrent first-writes for the same key only race on
 * the cache entry, not on OpenAI calls (a tiny duplicate-cost window
 * exists but is negligible at our request volume — well below the
 * cost of synchronizing the OpenAI call itself).
 */
@Service
public class SemanticSimilarityService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSimilarityService.class);
    private static final float[] EMPTY = new float[0];

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final SemanticSimilarityProperties props;
    private final Cache<String, float[]> cache;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter embedSuccesses;
    private final Counter embedFailures;
    private volatile boolean degradedLogged = false;

    public SemanticSimilarityService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                     SemanticSimilarityProperties props,
                                     MeterRegistry meterRegistry) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.props = props;

        var cacheCfg = props.cache();
        int maxSize = (cacheCfg == null) ? 10_000 : cacheCfg.maxSize();
        int ttlHours = (cacheCfg == null) ? 24 : cacheCfg.ttlHours();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofHours(ttlHours))
                .recordStats()
                .build();

        this.cacheHits = meterRegistry.counter("embedding.cache.hits");
        this.cacheMisses = meterRegistry.counter("embedding.cache.misses");
        this.embedSuccesses = meterRegistry.counter("openai.embedding.calls", "outcome", "success");
        this.embedFailures = meterRegistry.counter("openai.embedding.calls", "outcome", "failure");
    }

    /**
     * Embeds {@code text} into a dense float vector. Returns {@link #EMPTY}
     * for null/blank input, when no {@link EmbeddingModel} bean is wired,
     * or when the model call throws. Never throws.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        String normalized = text.strip();
        String key = props.model() + ":" + sha256(normalized);

        // Pre-check for a hit so we can bump the hits counter accurately —
        // Caffeine's get(key, fn) doesn't distinguish hit from miss in its
        // load lambda. After this branch we use get(key, fn) for atomic
        // get-or-load: only one thread per key invokes the load function,
        // so 200 simultaneous requests for the same mentee text don't fire
        // 200 simultaneous OpenAI calls.
        float[] cached = cache.getIfPresent(key);
        if (cached != null) {
            cacheHits.increment();
            return cached;
        }
        cacheMisses.increment();

        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return degradeOnce("EmbeddingModel bean unavailable — semantic signal disabled");
        }
        // We track success vs failure outside the mappingFn so concurrent
        // racers that piggyback on the in-flight load don't double-count
        // their own outcome (they get the same returned vector, regardless).
        //
        // Catch Throwable to honour the class-level "never throws" contract:
        // an Error here (OOM, LinkageError, etc.) should degrade just like
        // a runtime failure — a 500 from the matcher because of an Error in
        // the embedding layer is worse than a partial recommendation. Errors
        // are rethrown to the JVM via no special handling here, but the
        // outer matching response still returns cleanly.
        float[] loaded;
        try {
            loaded = cache.get(key, k -> {
                try {
                    return model.embed(normalized);
                } catch (RuntimeException ex) {
                    // Propagate so the outer try catches it; don't cache failures.
                    throw new EmbeddingCallFailed(ex);
                }
            });
        } catch (EmbeddingCallFailed wrapper) {
            embedFailures.increment();
            // Drop ex.getMessage() — some OpenAI client exceptions embed the
            // request body (which contains user PII) in their message.
            return degradeOnce("Embedding call failed: " + wrapper.cause.getClass().getSimpleName());
        } catch (Throwable unexpected) {
            embedFailures.increment();
            return degradeOnce("Embedding call failed: " + unexpected.getClass().getSimpleName());
        }
        embedSuccesses.increment();
        // Reset the degraded-log gate so the next failure logs at WARN
        // rather than being silently demoted to DEBUG for the JVM lifetime.
        degradedLogged = false;
        return loaded == null ? EMPTY : loaded;
    }

    /** Lets {@link Cache#get} unwind without caching a failed load. */
    private static final class EmbeddingCallFailed extends RuntimeException {
        final RuntimeException cause;
        EmbeddingCallFailed(RuntimeException cause) { super(cause); this.cause = cause; }
    }

    /**
     * Cosine similarity in [-1, 1] (typically [0, 1] for embeddings).
     * Returns {@code 0.0} when either input is null, empty, of different
     * length, or zero-norm. Pure function — safe to call from anywhere.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double av = a[i];
            double bv = b[i];
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Visible-for-testing / monitoring hook — current cache size. */
    public long cacheSize() {
        return cache.estimatedSize();
    }

    /** Drops every cached entry. Used by tests; not wired to any endpoint. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private float[] degradeOnce(String reason) {
        if (!degradedLogged) {
            log.warn("SemanticSimilarityService degraded: {}", reason);
            degradedLogged = true;
        } else {
            log.debug("SemanticSimilarityService degraded: {}", reason);
        }
        return EMPTY;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable on JVM", impossible);
        }
    }
}
