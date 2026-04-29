package com.group7.backend.event;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the MATCH_FOUND dedup window is exactly 24 hours before
 * the (clock-pinned) "now", in UTC, and matches createdAt storage
 * semantics — issue #272 acceptance criterion #3.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerClockTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private static final Instant FIXED = Instant.parse("2026-04-29T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Test
    void matchFoundDedup_queriesRepoWithFixedClockMinus24h() {
        NotificationEventListener listener =
                new NotificationEventListener(notificationRepository, userRepository, fixedClock);

        User recipient = new Mentee();
        recipient.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.existsByRecipient_IdAndTypeAndBodyAndCreatedAtAfter(
                anyLong(), any(NotificationType.class), anyString(), any(OffsetDateTime.class)))
                .thenReturn(true);

        listener.onNotificationCreated(new NotificationCreatedEvent(
                7L, NotificationType.MATCH_FOUND, "Match", "body"));

        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationRepository).existsByRecipient_IdAndTypeAndBodyAndCreatedAtAfter(
                eq(7L), eq(NotificationType.MATCH_FOUND), eq("body"), sinceCaptor.capture());

        OffsetDateTime expected = OffsetDateTime.ofInstant(FIXED, ZoneOffset.UTC).minusHours(24);
        assertThat(sinceCaptor.getValue().toInstant())
                .as("dedup window must be JVM-TZ-independent: clock.now() - 24h in UTC")
                .isEqualTo(expected.toInstant());
        assertThat(sinceCaptor.getValue().getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
