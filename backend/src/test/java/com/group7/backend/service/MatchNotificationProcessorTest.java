package com.group7.backend.service;

import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.LastMatchNotification;
import com.group7.backend.entity.Mentee;
import com.group7.backend.repository.LastMatchNotificationRepository;
import com.group7.backend.repository.MenteeRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link MatchNotificationProcessor}'s per-user transactional
 * logic. Mocks the matching service + repositories + publisher; the real {@code
 * RuleBasedMentorRanker} is irrelevant here because we never exercise scoring —
 * only the publish-vs-skip decision based on state-row equality. Pattern matches
 * the existing {@link com.group7.backend.scheduler.AttachmentOrphanCleanupScheduler}
 * test convention: {@code @ExtendWith(MockitoExtension.class)}, fixed Clock,
 * manual constructor, AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class MatchNotificationProcessorTest {

    @Mock private MatchingService matchingService;
    @Mock private MenteeRepository menteeRepository;
    @Mock private LastMatchNotificationRepository stateRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-05-07T09:00:00Z"), ZoneOffset.UTC);

    private MatchNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MatchNotificationProcessor(
                matchingService,
                menteeRepository,
                stateRepository,
                notificationEventPublisher,
                fixedClock);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Mentee eligibleMentee(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("Mentee" + id);
        m.setActiveMentorId(null);   // eligible
        return m;
    }

    private MentorMatchResponse mentorMatch(Long id, String firstName) {
        MentorMatchResponse r = new MentorMatchResponse();
        r.setId(id);
        r.setFirstName(firstName);
        return r;
    }

    private LastMatchNotification stateRow(Long userId, Long notifiedId) {
        LastMatchNotification row = new LastMatchNotification();
        row.setUserId(userId);
        row.setNotifiedMatchUserId(notifiedId);
        row.setSentAt(OffsetDateTime.parse("2026-05-06T09:00:00Z"));
        return row;
    }

    // ── Mentee side ──────────────────────────────────────────────────────────

    @Test
    void processMentee_publishesAndUpserts_whenStateRowAbsent() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(99L, "Ali")));
        when(stateRepository.findById(1L)).thenReturn(Optional.empty());

        boolean published = processor.processMentee(1L);

        assertThat(published).isTrue();
        verify(notificationEventPublisher).publishMatchFound(1L, "Ali");
        ArgumentCaptor<LastMatchNotification> saved = ArgumentCaptor.forClass(LastMatchNotification.class);
        verify(stateRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getNotifiedMatchUserId()).isEqualTo(99L);
        assertThat(saved.getValue().getSentAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
    }

    @Test
    void processMentee_publishesAndUpserts_whenTopMatchDiffers() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(200L, "Bob")));
        when(stateRepository.findById(1L))
                .thenReturn(Optional.of(stateRow(1L, /*previously notified*/ 99L)));

        boolean published = processor.processMentee(1L);

        assertThat(published).isTrue();
        verify(notificationEventPublisher).publishMatchFound(1L, "Bob");
        ArgumentCaptor<LastMatchNotification> saved = ArgumentCaptor.forClass(LastMatchNotification.class);
        verify(stateRepository).save(saved.capture());
        assertThat(saved.getValue().getNotifiedMatchUserId()).isEqualTo(200L);
    }

    @Test
    void processMentee_skips_whenTopMatchUnchanged() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(99L, "Ali")));
        when(stateRepository.findById(1L)).thenReturn(Optional.of(stateRow(1L, 99L)));

        boolean published = processor.processMentee(1L);

        assertThat(published).isFalse();
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void processMentee_skips_whenMenteeRaceDeleted() {
        when(menteeRepository.findById(1L)).thenReturn(Optional.empty());

        boolean published = processor.processMentee(1L);

        assertThat(published).isFalse();
        verify(matchingService, never()).rankMentorsFor(any(), any());
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void processMentee_skips_whenMenteeRaceAcquiredActiveMentor() {
        Mentee me = eligibleMentee(1L);
        me.setActiveMentorId(42L);   // raced: now has an active mentor
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));

        boolean published = processor.processMentee(1L);

        assertThat(published).isFalse();
        verify(matchingService, never()).rankMentorsFor(any(), any());
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void processMentee_skips_whenRankReturnsEmpty() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null)).thenReturn(List.of());

        boolean published = processor.processMentee(1L);

        assertThat(published).isFalse();
        verify(stateRepository, never()).findById(anyLong());
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void processMentee_skips_whenTopMatchHasNullId() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        // Defensive guard against DTO-mapping bugs — ranker emits a response
        // with id=null. We log + skip rather than NPE downstream.
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(null, "Ali")));

        boolean published = processor.processMentee(1L);

        assertThat(published).isFalse();
        verify(notificationEventPublisher, never()).publishMatchFound(anyLong(), anyString());
        verify(stateRepository, never()).save(any());
    }

    // ── Upsert correctness (sentAt timestamp + state-row carry) ──────────────

    @Test
    void processMentee_setsSentAtFromInjectedClock() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(99L, "Ali")));
        when(stateRepository.findById(1L)).thenReturn(Optional.empty());

        processor.processMentee(1L);

        ArgumentCaptor<LastMatchNotification> saved = ArgumentCaptor.forClass(LastMatchNotification.class);
        verify(stateRepository).save(saved.capture());
        // Asserts the Clock dependency is wired through, not OffsetDateTime.now()
        // sneaking in via field initializers or static helpers.
        assertThat(saved.getValue().getSentAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-07T09:00:00Z"));
    }

    @Test
    void processMentee_preservesEntityIdentityWhenUpdatingExistingStateRow() {
        Mentee me = eligibleMentee(1L);
        when(menteeRepository.findById(1L)).thenReturn(Optional.of(me));
        when(matchingService.rankMentorsFor(me, null))
                .thenReturn(List.of(mentorMatch(200L, "Bob")));
        LastMatchNotification existing = stateRow(1L, 99L);
        when(stateRepository.findById(1L)).thenReturn(Optional.of(existing));

        processor.processMentee(1L);

        // The same managed instance is saved (mutated in place) — not a fresh
        // entity that would trigger an insert + PK collision.
        ArgumentCaptor<LastMatchNotification> saved = ArgumentCaptor.forClass(LastMatchNotification.class);
        verify(stateRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getNotifiedMatchUserId()).isEqualTo(200L);
    }
}
