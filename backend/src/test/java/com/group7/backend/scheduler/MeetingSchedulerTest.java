package com.group7.backend.scheduler;

import com.group7.backend.config.MeetingProperties;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingReminderState;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.MeetingReminderStateRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingSchedulerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-07T10:00:00Z");

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingReminderStateRepository reminderStateRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    @Spy
    private Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private MeetingScheduler scheduler;
    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;

    @BeforeEach
    void setUp() {
        MeetingProperties properties = new MeetingProperties(24, 1, "24", 5, "0 */5 * * * *", "UTC");
        scheduler = new MeetingScheduler(
                meetingRepository,
                reminderStateRepository,
                notificationEventPublisher,
                clock,
                properties
        );

        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Ada");

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setFirstName("Mia");

        mentorship = new Mentorship();
        mentorship.setId(11L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
    }

    @Test
    void run_sendsRemindersAndRecordsState() {
        Meeting meeting = confirmedMeeting();
        String reminderText = "Meeting starts at " + meeting.getStartTime();

        when(meetingRepository.findByStatusAndStartTimeBetween(eq(MeetingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of(meeting));
        when(reminderStateRepository.existsByMeeting_IdAndReminderOffsetMinutes(55L, 1440))
                .thenReturn(false);
        when(meetingRepository.findByStatusAndConfirmationDeadlineBefore(eq(MeetingStatus.PENDING_CONFIRMATION), any()))
                .thenReturn(List.of());
        when(meetingRepository.findByStatusAndEndTimeBefore(eq(MeetingStatus.CONFIRMED), any()))
                .thenReturn(List.of());

        scheduler.run();

        verify(notificationEventPublisher).publishMeetingReminder(1L, reminderText);
        verify(notificationEventPublisher).publishMeetingReminder(2L, reminderText);

        ArgumentCaptor<MeetingReminderState> stateCaptor = ArgumentCaptor.forClass(MeetingReminderState.class);
        verify(reminderStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getMeeting()).isEqualTo(meeting);
        assertThat(stateCaptor.getValue().getReminderOffsetMinutes()).isEqualTo(1440);
    }

    @Test
    void run_skipsReminderWhenStateExists() {
        Meeting meeting = confirmedMeeting();

        when(meetingRepository.findByStatusAndStartTimeBetween(eq(MeetingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of(meeting));
        when(reminderStateRepository.existsByMeeting_IdAndReminderOffsetMinutes(55L, 1440))
                .thenReturn(true);
        when(meetingRepository.findByStatusAndConfirmationDeadlineBefore(eq(MeetingStatus.PENDING_CONFIRMATION), any()))
                .thenReturn(List.of());
        when(meetingRepository.findByStatusAndEndTimeBefore(eq(MeetingStatus.CONFIRMED), any()))
                .thenReturn(List.of());

        scheduler.run();

        verify(notificationEventPublisher, never()).publishMeetingReminder(any(), any());
        verify(reminderStateRepository, never()).save(any(MeetingReminderState.class));
    }

    @Test
    void run_expiresPendingMeetings() {
        Meeting pending = pendingMeeting();

        when(meetingRepository.findByStatusAndStartTimeBetween(eq(MeetingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(meetingRepository.findByStatusAndConfirmationDeadlineBefore(eq(MeetingStatus.PENDING_CONFIRMATION), any()))
                .thenReturn(List.of(pending));
        when(meetingRepository.findByStatusAndEndTimeBefore(eq(MeetingStatus.CONFIRMED), any()))
                .thenReturn(List.of());

        scheduler.run();

        assertThat(pending.getStatus()).isEqualTo(MeetingStatus.EXPIRED);
        verify(meetingRepository).save(pending);
        verify(notificationEventPublisher).publishMeetingAutoDeclined(1L, "Mia");
    }

    @Test
    void run_completesPastMeetings() {
        Meeting meeting = confirmedMeeting();

        when(meetingRepository.findByStatusAndStartTimeBetween(eq(MeetingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(meetingRepository.findByStatusAndConfirmationDeadlineBefore(eq(MeetingStatus.PENDING_CONFIRMATION), any()))
                .thenReturn(List.of());
        when(meetingRepository.findByStatusAndEndTimeBefore(eq(MeetingStatus.CONFIRMED), any()))
                .thenReturn(List.of(meeting));

        scheduler.run();

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        verify(meetingRepository).save(meeting);
    }

    private Meeting confirmedMeeting() {
        Meeting meeting = new Meeting();
        meeting.setId(55L);
        meeting.setMentorship(mentorship);
        meeting.setStatus(MeetingStatus.CONFIRMED);
        meeting.setStartTime(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusHours(24));
        meeting.setEndTime(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusHours(25));
        return meeting;
    }

    private Meeting pendingMeeting() {
        Meeting meeting = new Meeting();
        meeting.setId(77L);
        meeting.setMentorship(mentorship);
        meeting.setStatus(MeetingStatus.PENDING_CONFIRMATION);
        meeting.setStartTime(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusHours(2));
        meeting.setEndTime(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusHours(3));
        meeting.setConfirmationDeadline(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).minusHours(1));
        return meeting;
    }
}
