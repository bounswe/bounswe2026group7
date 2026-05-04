package com.group7.backend.config.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BucketCacheTest {

    private static final RateLimitRule RULE_A = new RateLimitRule(
            HttpMethod.POST, "/api/a", KeyStrategy.IP, 5, Duration.ofMinutes(1));
    private static final RateLimitRule RULE_B = new RateLimitRule(
            HttpMethod.POST, "/api/b", KeyStrategy.IP, 5, Duration.ofMinutes(1));

    @Test
    void returnsSameBucketForSameKey() {
        BucketCache cache = newCache();

        Bucket first = cache.getOrCreate("rule-a:ip:1.1.1.1", RULE_A);
        Bucket second = cache.getOrCreate("rule-a:ip:1.1.1.1", RULE_A);

        assertThat(first).isSameAs(second);
    }

    @Test
    void differentRulesGetIndependentBuckets() {
        BucketCache cache = newCache();
        // exhaust rule A
        Bucket bucketA = cache.getOrCreate("rule-a:ip:1.1.1.1", RULE_A);
        for (int i = 0; i < 5; i++) {
            assertThat(bucketA.tryConsume(1)).isTrue();
        }
        assertThat(bucketA.tryConsume(1)).isFalse();

        // rule B for same key still has full capacity
        Bucket bucketB = cache.getOrCreate("rule-b:ip:1.1.1.1", RULE_B);
        assertThat(bucketB.tryConsume(1)).isTrue();
    }

    @Test
    void differentKeysGetIndependentBuckets() {
        BucketCache cache = newCache();
        Bucket bucketA = cache.getOrCreate("rule-a:ip:1.1.1.1", RULE_A);
        for (int i = 0; i < 5; i++) {
            bucketA.tryConsume(1);
        }
        assertThat(bucketA.tryConsume(1)).isFalse();

        Bucket bucketC = cache.getOrCreate("rule-a:ip:2.2.2.2", RULE_A);
        assertThat(bucketC.tryConsume(1)).isTrue();
    }

    @Test
    void concurrentGetOrCreateReturnsSingleInstance() {
        BucketCache cache = newCache();
        Set<Bucket> seen = ConcurrentHashMap.newKeySet();

        IntStream.range(0, 100).parallel().forEach(i ->
                seen.add(cache.getOrCreate("rule-a:ip:concurrent", RULE_A))
        );

        assertThat(seen).hasSize(1);
    }

    @Test
    void bucketRefillsWhenClockAdvances() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        BucketCache cache = new BucketCache(new ClockTimeMeter(clock));
        Bucket bucket = cache.getOrCreate("rule-a:ip:1.1.1.1", RULE_A);

        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1)).isTrue();
        }
        assertThat(bucket.tryConsume(1)).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(bucket.tryConsume(1)).isTrue();
    }

    private static BucketCache newCache() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        return new BucketCache(new ClockTimeMeter(clock));
    }
}
