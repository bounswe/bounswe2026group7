package com.group7.backend.service;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipAuditLog;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.MentorshipAuditLogRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipAutoCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T12:00:00Z");

    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MentorshipAuditLogRepository mentorshipAuditLogRepository;
    @Mock private MentorshipCleanupService mentorshipCleanupService;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private MentorshipAutoCompletionService service;

    @BeforeEach
    void setUp() {
        // Lenient: only some tests run a per-row callback (others return on
        // the empty-list short-circuit before TransactionTemplate is touched).
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MentorshipAutoCompletionService(mentorshipRepository,
                mentorshipAuditLogRepository, mentorshipCleanupService,
                notificationEventPublisher, clock, transactionManager);
    }

    private Mentorship activeExpired(Long id, Long mentorId, Long menteeId) {
        Mentor mentor = new Mentor();
        mentor.setId(mentorId);
        mentor.setFirstName("Mentor" + mentorId);
        mentor.setCurrentMenteeCount(1);

        Mentee mentee = new Mentee();
        mentee.setId(menteeId);
        mentee.setFirstName("Mentee" + menteeId);
        mentee.setActiveMentorId(mentorId);

        Mentorship m = new Mentorship();
        m.setId(id);
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setStatus(MentorshipStatus.ACTIVE);
        m.setStartDate(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMonths(3));
        m.setEndDate(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusHours(1));
        m.setDuration(3);
        return m;
    }

    @Test
    void autoCompleteExpired_returnsZeroWhenNothingExpired() {
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        assertThat(service.autoCompleteExpired()).isZero();
        verify(mentorshipAuditLogRepository, never()).save(any());
    }

    @Test
    void autoCompleteExpired_processesAllExpiredRows() {
        Mentorship m1 = activeExpired(100L, 1L, 2L);
        Mentorship m2 = activeExpired(101L, 3L, 4L);
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(m1, m2));
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(m1));
        when(mentorshipRepository.findById(101L)).thenReturn(Optional.of(m2));

        int processed = service.autoCompleteExpired();

        assertThat(processed).isEqualTo(2);
        assertThat(m1.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(m2.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        verify(mentorshipCleanupService).cleanupChildren(100L);
        verify(mentorshipCleanupService).cleanupChildren(101L);
        // Both participants of each mentorship receive a notification:
        // m1 -> recipients 1L (mentor) and 2L (mentee); m2 -> 3L and 4L.
        verify(notificationEventPublisher).publishMentorshipAutoCompleted(eq(1L), anyString());
        verify(notificationEventPublisher).publishMentorshipAutoCompleted(eq(2L), anyString());
        verify(notificationEventPublisher).publishMentorshipAutoCompleted(eq(3L), anyString());
        verify(notificationEventPublisher).publishMentorshipAutoCompleted(eq(4L), anyString());
    }

    @Test
    void autoCompleteExpired_writesAuditRowAndNotifiesBoth() {
        Mentorship m = activeExpired(100L, 1L, 2L);
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(m));
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(m));

        service.autoCompleteExpired();

        ArgumentCaptor<MentorshipAuditLog> captor = ArgumentCaptor.forClass(MentorshipAuditLog.class);
        verify(mentorshipAuditLogRepository).save(captor.capture());
        MentorshipAuditLog logged = captor.getValue();
        assertThat(logged.getFromStatus()).isEqualTo(MentorshipStatus.ACTIVE);
        assertThat(logged.getToStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(logged.getActorUserId()).isNull();
        assertThat(logged.getReason()).contains("Auto-completed");

        verify(notificationEventPublisher).publishMentorshipAutoCompleted(1L, "Mentee2");
        verify(notificationEventPublisher).publishMentorshipAutoCompleted(2L, "Mentor1");
    }

    @Test
    void autoCompleteExpired_clearsActiveMentorIdAndDecrementsCount() {
        Mentorship m = activeExpired(100L, 1L, 2L);
        Mentor mentor = m.getMentor();
        Mentee mentee = m.getMentee();
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(m));
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(m));

        service.autoCompleteExpired();

        assertThat(mentee.getActiveMentorId()).isNull();
        assertThat(mentor.getCurrentMenteeCount()).isZero();
    }

    @Test
    void autoCompleteExpired_skipsRowFlippedByConcurrentActor() {
        // Sweep query loaded m as ACTIVE, but by the time completeOne runs the
        // row was cancelled by someone else. Service must no-op silently.
        Mentorship m = activeExpired(100L, 1L, 2L);
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(m));
        Mentorship raceFlipped = activeExpired(100L, 1L, 2L);
        raceFlipped.setStatus(MentorshipStatus.CANCELLED);
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(raceFlipped));

        int processed = service.autoCompleteExpired();

        assertThat(processed).isEqualTo(1);
        verify(mentorshipCleanupService, never()).cleanupChildren(anyLong());
        verify(mentorshipAuditLogRepository, never()).save(any());
    }

    @Test
    void autoCompleteExpired_oneFailureDoesNotBlockOthers() {
        Mentorship m1 = activeExpired(100L, 1L, 2L);
        Mentorship m2 = activeExpired(101L, 3L, 4L);
        when(mentorshipRepository.findActiveExpiredAt(eq(MentorshipStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(m1, m2));
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(m1));
        when(mentorshipRepository.findById(101L)).thenReturn(Optional.of(m2));
        doThrow(new RuntimeException("cleanup boom"))
                .when(mentorshipCleanupService).cleanupChildren(100L);

        int processed = service.autoCompleteExpired();

        assertThat(processed).isEqualTo(1);
        assertThat(m2.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        verify(mentorshipCleanupService).cleanupChildren(101L);
    }
}
