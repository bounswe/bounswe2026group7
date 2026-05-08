package com.group7.backend.service;

import com.group7.backend.config.BanProperties;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BanServiceTest {

    @Mock private BanRepository banRepository;
    @Mock private MenteeRepository menteeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    private BanService service;
    private BanProperties properties;
    private Mentee mentee;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new BanProperties();
        // Defaults: threshold=3, firstBanHours=24, factor=2, maxBanHours=720.
        service = new BanService(banRepository, menteeRepository, userRepository,
                notificationEventPublisher, properties, clock);

        mentee = new Mentee();
        mentee.setId(7L);
        mentee.setFirstName("Mentee");
        mentee.setCancelCount(0);
    }

    // ── isBanned / getActiveBan ──────────────────────────────────────────

    @Test
    void isBannedReturnsTrueWhenActiveBanExists() {
        when(banRepository.findActive(eq(7L), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(new Ban()));

        assertThat(service.isBanned(7L)).isTrue();
    }

    @Test
    void isBannedReturnsFalseWhenNoActiveBan() {
        when(banRepository.findActive(eq(7L), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThat(service.isBanned(7L)).isFalse();
    }

    // ── recordCancellation: warning territory ────────────────────────────

    @Test
    void recordCancellation_belowThreshold_doesNotCreateBan() {
        when(menteeRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(menteeRepository.save(any(Mentee.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Ban> result = service.recordCancellation(7L, "test");

        assertThat(result).isEmpty();
        assertThat(mentee.getCancelCount()).isEqualTo(1);
        verify(banRepository, never()).save(any(Ban.class));
        verify(notificationEventPublisher, never())
                .publishUserBanned(anyLong(), any(), anyString(), anyInt());
    }

    // ── recordCancellation: first ban (3rd cancellation, ordinal=1) ──────

    @Test
    void recordCancellation_atThreshold_createsFirstBan_24h() {
        mentee.setCancelCount(2); // about to be 3, threshold met
        when(menteeRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(menteeRepository.save(any(Mentee.class))).thenAnswer(i -> i.getArgument(0));
        when(banRepository.countByUser_Id(7L)).thenReturn(0L);
        when(banRepository.save(any(Ban.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Ban> result = service.recordCancellation(7L, "Frequent cancellations");

        assertThat(result).isPresent();
        ArgumentCaptor<Ban> captor = ArgumentCaptor.forClass(Ban.class);
        verify(banRepository).save(captor.capture());
        Ban saved = captor.getValue();
        assertThat(saved.getBanCount()).isEqualTo(1);
        assertThat(saved.getReason()).isEqualTo("Frequent cancellations");
        // First ban: 24h * factor^0 = 24h
        assertThat(saved.getExpiresAt()).isEqualTo(OffsetDateTime.now(clock).plusHours(24));
        verify(notificationEventPublisher).publishUserBanned(
                eq(7L), eq(saved.getExpiresAt()), eq("Frequent cancellations"), eq(1));
    }

    // ── recordCancellation: 2nd ban → 48h ────────────────────────────────

    @Test
    void recordCancellation_secondBan_doublesDuration() {
        mentee.setCancelCount(3); // 4th cancellation
        when(menteeRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(menteeRepository.save(any(Mentee.class))).thenAnswer(i -> i.getArgument(0));
        when(banRepository.countByUser_Id(7L)).thenReturn(1L); // already banned once
        when(banRepository.save(any(Ban.class))).thenAnswer(i -> i.getArgument(0));

        service.recordCancellation(7L, "again");

        ArgumentCaptor<Ban> captor = ArgumentCaptor.forClass(Ban.class);
        verify(banRepository).save(captor.capture());
        Ban saved = captor.getValue();
        assertThat(saved.getBanCount()).isEqualTo(2);
        // 24h * 2^(2-1) = 48h
        assertThat(saved.getExpiresAt()).isEqualTo(OffsetDateTime.now(clock).plusHours(48));
    }

    // ── recordCancellation: cap at maxBanHours ───────────────────────────

    @Test
    void recordCancellation_largeOrdinal_capsAtMaxBanHours() {
        properties.setMaxBanHours(720); // 30 days
        mentee.setCancelCount(10);
        when(menteeRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(menteeRepository.save(any(Mentee.class))).thenAnswer(i -> i.getArgument(0));
        // ordinal 8: 24 * 2^7 = 3072h → capped at 720h
        when(banRepository.countByUser_Id(7L)).thenReturn(7L);
        when(banRepository.save(any(Ban.class))).thenAnswer(i -> i.getArgument(0));

        service.recordCancellation(7L, "many");

        ArgumentCaptor<Ban> captor = ArgumentCaptor.forClass(Ban.class);
        verify(banRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(OffsetDateTime.now(clock).plusHours(720));
    }

    @Test
    void recordCancellation_unknownMentee_throws404() {
        when(menteeRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordCancellation(7L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(banRepository, never()).save(any(Ban.class));
    }

    // ── liftBan ──────────────────────────────────────────────────────────

    @Test
    void liftBan_setsLiftedFieldsAndPublishesNotification() {
        Ban ban = new Ban();
        ban.setId(99L);
        ban.setUser(mentee);
        when(banRepository.findById(99L)).thenReturn(Optional.of(ban));
        Admin admin = new Admin();
        admin.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of((User) admin));
        when(banRepository.save(ban)).thenReturn(ban);

        service.liftBan(99L, 1L);

        assertThat(ban.getLiftedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(ban.getLiftedByAdminId()).isEqualTo(1L);
        verify(notificationEventPublisher).publishBanLifted(7L);
    }

    @Test
    void liftBan_alreadyLifted_isNoOp() {
        Ban ban = new Ban();
        ban.setId(99L);
        ban.setUser(mentee);
        ban.setLiftedAt(OffsetDateTime.now(clock).minusHours(1));
        ban.setLiftedByAdminId(1L);
        when(banRepository.findById(99L)).thenReturn(Optional.of(ban));

        Ban result = service.liftBan(99L, 1L);

        assertThat(result).isSameAs(ban);
        verify(banRepository, never()).save(any(Ban.class));
        verify(notificationEventPublisher, never()).publishBanLifted(anyLong());
        // Even userRepository should not be touched on the no-op path
        verify(userRepository, times(0)).findById(anyLong());
    }

    @Test
    void liftBan_unknownBan_throws404() {
        when(banRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.liftBan(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
