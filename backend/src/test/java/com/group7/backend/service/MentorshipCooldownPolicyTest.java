package com.group7.backend.service;

import com.group7.backend.config.ratelimit.MutableClock;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.repository.MentorshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipCooldownPolicyTest {

    private static final Duration COOLDOWN = Duration.ofDays(7);
    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");

    @Mock private MentorshipRepository mentorshipRepository;

    private MutableClock clock;
    private MentorshipCooldownPolicy policy;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        policy = new MentorshipCooldownPolicy(mentorshipRepository, clock, COOLDOWN);
    }

    @Test
    void allowsWhenPairHasNoPriorTermination() {
        when(mentorshipRepository.findLastTerminatedAtForPair(1L, 2L)).thenReturn(Optional.empty());

        assertThatCode(() -> policy.assertNotInCooldown(1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenLastTerminationIsInsideCooldown() {
        OffsetDateTime terminatedAt = OffsetDateTime.ofInstant(T0.minus(Duration.ofDays(1)), ZoneOffset.UTC);
        when(mentorshipRepository.findLastTerminatedAtForPair(1L, 2L))
                .thenReturn(Optional.of(terminatedAt));

        assertThatThrownBy(() -> policy.assertNotInCooldown(1L, 2L))
                .isInstanceOf(MentorshipRequestException.class)
                .hasMessageContaining("cool-down");
    }

    @Test
    void allowsWhenCooldownHasJustElapsed() {
        // Termination exactly COOLDOWN ago: eligibleAt == now, NOT after, so not blocked.
        OffsetDateTime terminatedAt = OffsetDateTime.ofInstant(T0.minus(COOLDOWN), ZoneOffset.UTC);
        when(mentorshipRepository.findLastTerminatedAtForPair(1L, 2L))
                .thenReturn(Optional.of(terminatedAt));

        assertThatCode(() -> policy.assertNotInCooldown(1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void blocksAtBoundaryMinusOneSecond() {
        OffsetDateTime terminatedAt = OffsetDateTime.ofInstant(
                T0.minus(COOLDOWN).plusSeconds(1), ZoneOffset.UTC);
        when(mentorshipRepository.findLastTerminatedAtForPair(1L, 2L))
                .thenReturn(Optional.of(terminatedAt));

        assertThatThrownBy(() -> policy.assertNotInCooldown(1L, 2L))
                .isInstanceOf(MentorshipRequestException.class);
    }

    @Test
    void allowsAfterClockAdvancesPastCooldown() {
        OffsetDateTime terminatedAt = OffsetDateTime.ofInstant(T0.minus(Duration.ofDays(1)), ZoneOffset.UTC);
        when(mentorshipRepository.findLastTerminatedAtForPair(1L, 2L))
                .thenReturn(Optional.of(terminatedAt));

        assertThatThrownBy(() -> policy.assertNotInCooldown(1L, 2L))
                .isInstanceOf(MentorshipRequestException.class);

        clock.advance(Duration.ofDays(7));

        assertThatCode(() -> policy.assertNotInCooldown(1L, 2L)).doesNotThrowAnyException();
    }
}
