package com.group7.backend.scheduler;

import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BanExpirySchedulerTest {

    @Mock private BanRepository banRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneOffset.UTC);
    private BanExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BanExpiryScheduler(
                banRepository, notificationEventPublisher, clock,
                "0 0 * * * *", "UTC");
    }

    @Test
    void sweepExpired_doesNothingWhenNoExpiredBans() {
        when(banRepository.findExpiredUnnotified(any(OffsetDateTime.class))).thenReturn(List.of());

        scheduler.sweepExpired();

        verify(notificationEventPublisher, never()).publishBanExpired(anyLong());
        verify(banRepository, never()).save(any(Ban.class));
    }

    @Test
    void sweepExpired_publishesAndMarksEachExpiredBan() {
        Ban ban1 = banFor(7L);
        Ban ban2 = banFor(8L);
        when(banRepository.findExpiredUnnotified(any(OffsetDateTime.class)))
                .thenReturn(List.of(ban1, ban2));

        scheduler.sweepExpired();

        verify(notificationEventPublisher).publishBanExpired(7L);
        verify(notificationEventPublisher).publishBanExpired(8L);
        assertThat(ban1.isExpiryNotified()).isTrue();
        assertThat(ban2.isExpiryNotified()).isTrue();
        verify(banRepository, times(2)).save(any(Ban.class));
    }

    @Test
    void sweepExpired_oneFailureDoesNotBlockOtherBans() {
        Ban ban1 = banFor(7L);
        Ban ban2 = banFor(8L);
        when(banRepository.findExpiredUnnotified(any(OffsetDateTime.class)))
                .thenReturn(List.of(ban1, ban2));
        doThrow(new RuntimeException("publish failed"))
                .when(notificationEventPublisher).publishBanExpired(7L);

        scheduler.sweepExpired();

        // Second ban still gets dispatched and marked.
        verify(notificationEventPublisher).publishBanExpired(8L);
        assertThat(ban2.isExpiryNotified()).isTrue();
    }

    private Ban banFor(Long userId) {
        User user = new Mentee();
        user.setId(userId);
        Ban ban = new Ban();
        ban.setId(userId * 10);
        ban.setUser(user);
        ban.setSource(BanSource.MENTEE_CANCELLATION);
        ban.setExpiresAt(OffsetDateTime.now(clock).minusMinutes(5));
        ban.setExpiryNotified(false);
        return ban;
    }
}
