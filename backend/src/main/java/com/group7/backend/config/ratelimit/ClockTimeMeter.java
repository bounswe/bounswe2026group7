package com.group7.backend.config.ratelimit;

import io.github.bucket4j.TimeMeter;

import java.time.Clock;
import java.time.Instant;

/**
 * Bridges Bucket4j's {@link TimeMeter} to the project's injected {@link Clock}
 * bean. Tests advance time by swapping the {@code Clock} for a mutable one;
 * production uses {@code Clock.systemUTC()} from {@code TimeConfig}.
 *
 * <p>Millisecond precision is used (Bucket4j requires nanos but our refill
 * windows are seconds-scale, so the extra resolution is unused).
 */
public class ClockTimeMeter implements TimeMeter {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final Clock clock;

    public ClockTimeMeter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long currentTimeNanos() {
        return Math.multiplyExact(Instant.now(clock).toEpochMilli(), NANOS_PER_MILLI);
    }

    @Override
    public boolean isWallClockBased() {
        return true;
    }
}
