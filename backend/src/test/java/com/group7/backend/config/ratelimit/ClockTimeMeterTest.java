package com.group7.backend.config.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClockTimeMeterTest {

    @Test
    void readsTimeFromInjectedClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        ClockTimeMeter meter = new ClockTimeMeter(clock);

        long before = meter.currentTimeNanos();
        clock.advance(Duration.ofSeconds(30));
        long after = meter.currentTimeNanos();

        assertThat(after - before).isEqualTo(30L * 1_000_000_000L);
    }

    @Test
    void isWallClockBased() {
        ClockTimeMeter meter = new ClockTimeMeter(new MutableClock(Instant.EPOCH));
        assertThat(meter.isWallClockBased()).isTrue();
    }
}
