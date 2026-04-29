package com.group7.backend.config.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.Objects;

/**
 * Caffeine-bounded cache of Bucket4j buckets.
 *
 * <p>Capped at {@value #MAX_BUCKETS} entries with idle eviction after
 * {@value #IDLE_HOURS}h to prevent key-rotation OOM attacks. Caffeine's
 * {@link Cache#get(Object, java.util.function.Function)} guarantees the loader
 * runs at most once per key, so concurrent misses don't double-create.
 *
 * <p>Buckets use {@code refillGreedy} (continuous regeneration) rather than
 * {@code refillIntervally} (all tokens at once at the boundary). Greedy is
 * smoother and avoids burst spikes at refill boundaries.
 */
public class BucketCache {

    private static final long MAX_BUCKETS = 10_000L;
    private static final long IDLE_HOURS = 1L;

    private final Cache<String, Bucket> buckets;
    private final TimeMeter timeMeter;

    public BucketCache(TimeMeter timeMeter) {
        this.timeMeter = Objects.requireNonNull(timeMeter, "timeMeter");
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterAccess(Duration.ofHours(IDLE_HOURS))
                .build();
    }

    /**
     * Returns the bucket for {@code compositeKey}, creating one from {@code rule}
     * on first access. Caffeine guarantees the loader runs at most once per key,
     * so concurrent misses don't double-create.
     */
    public Bucket getOrCreate(String compositeKey, RateLimitRule rule) {
        Objects.requireNonNull(compositeKey, "compositeKey");
        Objects.requireNonNull(rule, "rule");
        return buckets.get(compositeKey, k -> buildBucket(rule));
    }

    /**
     * Drops all buckets. Intended for tests that need fresh bucket state
     * between test methods without paying the cost of {@code @DirtiesContext}.
     * Should not be called from production code.
     */
    public void clear() {
        buckets.invalidateAll();
    }

    private Bucket buildBucket(RateLimitRule rule) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(rule.capacity())
                        .refillGreedy(rule.capacity(), rule.refill()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}
