package com.group7.backend.service.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.group7.backend.config.SemanticSimilarityProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * OpenAI embeddings client wrapping {@code /v1/embeddings}.
 *
 * <p><b>Fail-open contract.</b> Any failure — missing API key, blank
 * input, 4xx / 5xx response, timeout, malformed response shape — returns
 * {@code float[0]}. The caller's cosine-similarity step then returns
 * {@code 0.0} and {@code SemanticAffinitySignal} emits
 * {@code semantic-unavailable}. The other six follow-signals carry the
 * recommendation unaffected and the API does not 5xx.
 *
 * <p><b>Cache.</b> Caffeine, keyed by {@code sha256(model + ":" + text)},
 * configured by {@code app.embedding.cache.*}. text-embedding-3-small
 * produces 1536-dim float vectors (~6 KB each); at the default 10,000
 * cache slots that's ~60 MB max — bounded and well within heap.
 *
 * <p><b>Gating.</b> {@code @ConditionalOnProperty} keeps the bean
 * absent until {@code semantic-affinity-enabled=true}. A deploy with the
 * flag off doesn't pay any cost, doesn't fail on a missing API key, and
 * doesn't surface the signal in recommendation factors.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.follow.signals.semantic-affinity-enabled",
        havingValue = "true")
public class SemanticSimilarityService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSimilarityService.class);

    private final RestClient openai;
    private final SemanticSimilarityProperties cfg;
    private Cache<String, float[]> cache;

    public SemanticSimilarityService(@Qualifier("openAiRestClient") RestClient openai,
                                     SemanticSimilarityProperties cfg) {
        this.openai = openai;
        this.cfg = cfg;
    }

    @PostConstruct
    void initCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(cfg.cache().maxSize())
                .expireAfterWrite(Duration.ofHours(cfg.cache().ttlHours()))
                .build();
    }

    /**
     * Synchronous embed call. Cache-aware; returns the same vector on
     * repeated calls for the same {@code text} within TTL.
     *
     * @return the embedding, or {@code float[0]} on any error.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        if (cfg.openai().apiKey() == null || cfg.openai().apiKey().isBlank()) {
            // Configured to be enabled but key not provisioned — fail-open
            // without retrying every call (cheap log once, then quiet).
            return new float[0];
        }
        String key = cacheKey(text);
        return cache.get(key, k -> doEmbed(text));
    }

    private float[] doEmbed(String text) {
        try {
            EmbeddingResponse resp = openai.post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + cfg.openai().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(cfg.openai().model(), text))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (resp == null || resp.data() == null || resp.data().isEmpty()
                    || resp.data().get(0).embedding() == null) {
                log.warn("OpenAI returned empty embedding payload; failing open");
                return new float[0];
            }
            return resp.data().get(0).embedding();
        } catch (Exception e) {
            log.warn("OpenAI embedding failed; degrading to empty vector: {}", e.getMessage());
            return new float[0];
        }
    }

    /**
     * Cosine similarity clamped to {@code [0,1]}. Returns 0 when either
     * vector is empty, when dimensions differ, or when either has zero
     * magnitude (e.g. all-zero vectors).
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na  += (double) a[i] * a[i];
            nb  += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        double raw = dot / (Math.sqrt(na) * Math.sqrt(nb));
        if (raw < 0.0) return 0.0;
        if (raw > 1.0) return 1.0;
        return raw;
    }

    /** Hashes model + text so whitespace-only variations don't blow the cache. */
    private String cacheKey(String text) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(cfg.openai().model().getBytes(StandardCharsets.UTF_8));
            sha.update((byte) ':');
            sha.update(text.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JDK contract; if it's missing,
            // fall through to a degraded (but still correct) cache key.
            return cfg.openai().model() + ':' + text.trim();
        }
    }

    // ── DTOs (private records, only Jackson sees them) ─────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingRequest(String model, String input) {}

    public record EmbeddingResponse(List<EmbeddingDatum> data) {}

    public record EmbeddingDatum(float[] embedding) {}
}
