package com.group7.backend.service;

import com.group7.backend.config.MeetingProperties;
import com.group7.backend.dto.request.MeetingCreateRequest;
import com.group7.backend.dto.request.MeetingRescheduleCreateRequest;
import com.group7.backend.dto.response.MeetingCreateResponse;
import com.group7.backend.dto.response.MeetingSummaryResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.GoalRequiredException;
import com.group7.backend.exception.MeetingConflictException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.repository.*;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-07T10:00:00Z");

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingActionItemRepository actionItemRepository;
    @Mock private MeetingRescheduleRequestRepository rescheduleRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    @Spy
    private Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private MeetingService meetingService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;
    private MeetingProperties properties;

    @BeforeEach
    void setUp() {
        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Ada");
        mentor.setTimezone("UTC");

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setFirstName("Mia");

        mentorship = new Mentorship();
        mentorship.setId(11L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC));
        mentorship.setEndDate(OffsetDateTime.of(2026, 5, 25, 10, 0, 0, 0, ZoneOffset.UTC));
        mentorship.setDuration(3);
        mentorship.setSharedGoal("Build a portfolio");

        properties = new MeetingProperties();

        meetingService = new MeetingService(
                meetingRepository,
                actionItemRepository,
                rescheduleRepository,
                mentorshipRepository,
                availabilitySlotRepository,
                userRepository,
                notificationEventPublisher,
                clock,
                properties
        );
    }

    @Test
    void createMeetings_createsSingleMeetingAndNotifies() {
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = start.plusHours(1);
        MeetingCreateRequest request = baseCreateRequest(start, end);

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(meetingRepository.existsOverlappingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(rescheduleRepository.existsOverlappingPendingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of(availabilitySlot(mentor, start)));
        when(meetingRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Meeting> saved = inv.getArgument(0);
            long id = 100L;
            for (Meeting meeting : saved) {
                meeting.setId(id++);
            }
            return saved;
        });

        MeetingCreateResponse response = meetingService.createMeetings(11L, 1L, request);

        assertThat(response.getMeetings()).hasSize(1);
        assertThat(response.getWarnings()).isEmpty();
        verify(notificationEventPublisher).publishMeetingPendingConfirmation(2L, "Ada");

        ArgumentCaptor<List<Meeting>> captor = ArgumentCaptor.forClass(List.class);
        verify(meetingRepository).saveAll(captor.capture());
        Meeting created = captor.getValue().get(0);

        assertThat(created.getStatus()).isEqualTo(MeetingStatus.PENDING_CONFIRMATION);
        assertThat(created.getMeetingType()).isEqualTo(MeetingType.ONLINE);
        assertThat(created.getMeetingLink()).isEqualTo("https://meet.example.com/abc");

        OffsetDateTime expectedDeadline = OffsetDateTime.of(2026, 5, 8, 9, 0, 0, 0, ZoneOffset.UTC);
        assertThat(created.getConfirmationDeadline()).isEqualTo(expectedDeadline);
    }

    @Test
    void createMeetings_recurringGeneratesUntilMentorshipEnd() {
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = start.plusHours(1);
        MeetingCreateRequest request = baseCreateRequest(start, end);
        request.setRecurring(true);
        request.setRecurrenceRule("FREQ=WEEKLY;INTERVAL=1");

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(meetingRepository.existsOverlappingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(rescheduleRepository.existsOverlappingPendingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of(availabilitySlot(mentor, start)));
        when(meetingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        MeetingCreateResponse response = meetingService.createMeetings(11L, 1L, request);

        assertThat(response.getMeetings()).hasSize(3);
        ArgumentCaptor<List<Meeting>> captor = ArgumentCaptor.forClass(List.class);
        verify(meetingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void createMeetings_outsideAvailabilityAddsWarning() {
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = start.plusHours(1);
        MeetingCreateRequest request = baseCreateRequest(start, end);

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(meetingRepository.existsOverlappingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(rescheduleRepository.existsOverlappingPendingForParticipants(eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(availabilitySlotRepository.findByMentorId(1L)).thenReturn(List.of());
        when(meetingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        MeetingCreateResponse response = meetingService.createMeetings(11L, 1L, request);

        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getWarnings().get(0)).contains("outside the mentor's availability window");
    }

    @Test
    void createMeetings_rejectsEndAfterMentorshipEnd() {
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 25, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 5, 25, 11, 0, 0, 0, ZoneOffset.UTC);
        MeetingCreateRequest request = baseCreateRequest(start, end);

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> meetingService.createMeetings(11L, 1L, request))
                .isInstanceOf(MeetingConflictException.class)
                .hasMessageContaining("within the mentorship duration");
    }

    @Test
    void confirmMeeting_setsStatusAndPublishes() {
        Meeting meeting = meetingWithStatus(MeetingStatus.PENDING_CONFIRMATION);
        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

        MeetingSummaryResponse response = meetingService.confirmMeeting(55L, 2L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(meeting.getConfirmedAt()).isNotNull();
        verify(notificationEventPublisher).publishMeetingConfirmed(1L, "Mia");
    }

    @Test
    void requestReschedule_createsPendingRequestAndNotifies() {
        Meeting meeting = meetingWithStatus(MeetingStatus.CONFIRMED);
        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));
        when(rescheduleRepository.findByMeetingIdAndStatus(55L, MeetingRescheduleStatus.PENDING))
                .thenReturn(Optional.empty());
        when(meetingRepository.existsOverlappingForParticipantsExcludingMeeting(eq(55L), eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(rescheduleRepository.existsOverlappingPendingForParticipantsExcludingMeeting(eq(55L), eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(rescheduleRepository.save(any(MeetingRescheduleRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        MeetingRescheduleCreateRequest request = new MeetingRescheduleCreateRequest();
        request.setProposedStart(OffsetDateTime.of(2026, 5, 9, 10, 0, 0, 0, ZoneOffset.UTC));
        request.setProposedEnd(OffsetDateTime.of(2026, 5, 9, 11, 0, 0, 0, ZoneOffset.UTC));
        request.setReason("Schedule conflict");

        meetingService.requestReschedule(55L, 1L, request);

        verify(notificationEventPublisher).publishMeetingRescheduleRequested(2L, "Ada");
    }

    @Test
    void requestReschedule_rejectsOutsideMentorship() {
        Meeting meeting = meetingWithStatus(MeetingStatus.CONFIRMED);
        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));

        MeetingRescheduleCreateRequest request = new MeetingRescheduleCreateRequest();
        request.setProposedStart(mentorship.getEndDate().plusDays(1));
        request.setProposedEnd(mentorship.getEndDate().plusDays(1).plusHours(1));

        assertThatThrownBy(() -> meetingService.requestReschedule(55L, 1L, request))
                .isInstanceOf(MeetingConflictException.class)
                .hasMessageContaining("within the mentorship duration");
    }

    @Test
    void approveReschedule_updatesMeetingAndPublishes() {
        Meeting meeting = meetingWithStatus(MeetingStatus.PENDING_CONFIRMATION);
        MeetingRescheduleRequest req = new MeetingRescheduleRequest();
        req.setId(88L);
        req.setMeeting(meeting);
        req.setRequestedBy(mentee);
        req.setProposedStart(OffsetDateTime.of(2026, 5, 9, 10, 0, 0, 0, ZoneOffset.UTC));
        req.setProposedEnd(OffsetDateTime.of(2026, 5, 9, 11, 0, 0, 0, ZoneOffset.UTC));

        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));
        when(rescheduleRepository.findById(88L)).thenReturn(Optional.of(req));
        when(meetingRepository.existsOverlappingForParticipantsExcludingMeeting(eq(55L), eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(rescheduleRepository.existsOverlappingPendingForParticipantsExcludingMeeting(eq(55L), eq(1L), eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rescheduleRepository.save(any(MeetingRescheduleRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        meetingService.approveReschedule(55L, 88L, 1L);

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getStartTime()).isEqualTo(req.getProposedStart());
        assertThat(meeting.getEndTime()).isEqualTo(req.getProposedEnd());
        assertThat(req.getStatus()).isEqualTo(MeetingRescheduleStatus.APPROVED);
        assertThat(req.getDecidedAt()).isNotNull();
        verify(notificationEventPublisher).publishMeetingRescheduleApproved(2L, "Ada");
    }

    @Test
    void cancelMeeting_rejectsNonMentor() {
        Meeting meeting = meetingWithStatus(MeetingStatus.CONFIRMED);
        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.cancelMeeting(55L, 2L))
                .isInstanceOf(ProfileNotVisibleException.class);
    }

    @Test
    void cancelMeeting_rejectsCompletedMeetings() {
        Meeting meeting = meetingWithStatus(MeetingStatus.COMPLETED);
        when(meetingRepository.findById(55L)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> meetingService.cancelMeeting(55L, 1L))
                .isInstanceOf(MeetingConflictException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    private MeetingCreateRequest baseCreateRequest(OffsetDateTime start, OffsetDateTime end) {
        MeetingCreateRequest request = new MeetingCreateRequest();
        request.setTitle("Weekly sync");
        request.setDescription("Check-in");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setMeetingType(MeetingType.ONLINE);
        request.setMeetingLink("https://meet.example.com/abc");
        request.setRecurring(false);
        return request;
    }

    private AvailabilitySlot availabilitySlot(Mentor owner, OffsetDateTime start) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setMentor(owner);
        slot.setDayOfWeek(start.getDayOfWeek());
        slot.setStartTime(start.toLocalTime().minusHours(1));
        slot.setEndTime(start.toLocalTime().plusHours(2));
        slot.setRecurring(true);
        return slot;
    }

    private Meeting meetingWithStatus(MeetingStatus status) {
        Meeting meeting = new Meeting();
        meeting.setId(55L);
        meeting.setMentorship(mentorship);
        meeting.setTitle("Mentorship sync");
        meeting.setStartTime(OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC));
        meeting.setEndTime(OffsetDateTime.of(2026, 5, 8, 11, 0, 0, 0, ZoneOffset.UTC));
        meeting.setMeetingType(MeetingType.ONLINE);
        meeting.setMeetingLink("https://meet.example.com/abc");
        meeting.setStatus(status);
        return meeting;
    }

    // ── Shared-goal gate ───────────────────────────────────────────────────

    @Test
    void createMeetings_NullGoal_GoalRequired() {
        mentorship.setSharedGoal(null);
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = start.plusHours(1);
        MeetingCreateRequest request = baseCreateRequest(start, end);

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> meetingService.createMeetings(11L, 1L, request))
                .isInstanceOf(GoalRequiredException.class)
                .extracting(ex -> ((GoalRequiredException) ex).getMentorshipId())
                .isEqualTo(11L);
    }

    @Test
    void createMeetings_BlankGoal_GoalRequired() {
        mentorship.setSharedGoal("   ");
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        MeetingCreateRequest request = baseCreateRequest(start, start.plusHours(1));

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> meetingService.createMeetings(11L, 1L, request))
                .isInstanceOf(GoalRequiredException.class);
    }

    /** Inactive status surfaces before goal-required (deeper invariant wins). */
    @Test
    void createMeetings_InactiveAndNullGoal_StatusErrorWins() {
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        mentorship.setSharedGoal(null);
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        MeetingCreateRequest request = baseCreateRequest(start, start.plusHours(1));

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> meetingService.createMeetings(11L, 1L, request))
                .isInstanceOf(MeetingConflictException.class)
                .hasMessageContaining("not active");
    }

    /** Mentor-auth check surfaces before goal-required. */
    @Test
    void createMeetings_NonMentorAndNullGoal_ForbiddenWins() {
        mentorship.setSharedGoal(null);
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 8, 10, 0, 0, 0, ZoneOffset.UTC);
        MeetingCreateRequest request = baseCreateRequest(start, start.plusHours(1));

        when(mentorshipRepository.findById(11L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> meetingService.createMeetings(11L, 2L, request))
                .isInstanceOf(ProfileNotVisibleException.class);
    }
}

