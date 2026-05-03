package com.group7.backend.config.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test helper. A {@link Clock} whose instant is held in an
 * {@link AtomicReference} so tests can advance it deterministically.
 *
 * <p>Used both by unit tests of rate-limit primitives and by
 * {@code RateLimitIntegrationTest} as a {@code @Primary} bean overriding the
 * production UTC clock.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    public MutableClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    public void advance(Duration delta) {
        now.updateAndGet(i -> i.plus(delta));
    }

    public void setNow(Instant instant) {
        now.set(instant);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
