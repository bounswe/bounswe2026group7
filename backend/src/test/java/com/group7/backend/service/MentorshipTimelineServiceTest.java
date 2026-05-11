package com.group7.backend.service;

import com.group7.backend.dto.response.TimelineItem;
import com.group7.backend.dto.response.TimelineItemType;
import com.group7.backend.dto.response.TimelineResponse;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.MeetingType;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.MilestoneStatus;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.exception.InvalidTimelineWindowException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MeetingActionItemRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MilestoneActionItemRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.projection.ActionItemCountTuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipTimelineServiceTest {

    @Mock private MentorshipService mentorshipService;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingActionItemRepository meetingActionItemRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private MilestoneActionItemRepository milestoneActionItemRepository;

    private Clock fixedClock;
    private MentorshipTimelineService service;

    private Mentorship mentorship;

    private static final long MID = 100L;
    private static final long MENTOR_ID = 1L;
    private static final long MENTEE_ID = 2L;
    private static final OffsetDateTime PROGRAM_START =
            OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime PROGRAM_END =
            OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC);
        service = new MentorshipTimelineService(
                mentorshipService, meetingRepository, meetingActionItemRepository,
                taskRepository, milestoneRepository, milestoneActionItemRepository, fixedClock);

        Mentor mentor = new Mentor();
        mentor.setId(MENTOR_ID);
        mentor.setFirstName("Mentor");
        Mentee mentee = new Mentee();
        mentee.setId(MENTEE_ID);
        mentee.setFirstName("Mentee");

        mentorship = new Mentorship();
        mentorship.setId(MID);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
        mentorship.setStartDate(PROGRAM_START);
        mentorship.setEndDate(PROGRAM_END);
    }

    private void stubFound(long callerId) {
        when(mentorshipService.findForParticipant(callerId, MID)).thenReturn(mentorship);
    }

    private void stubEmptyDomains() {
        lenient().when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        lenient().when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        lenient().when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
    }

    private static Meeting newMeeting(long id, OffsetDateTime startTime, MeetingStatus status, String notes) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setTitle("Meeting " + id);
        m.setStartTime(startTime);
        m.setEndTime(startTime.plusHours(1));
        m.setStatus(status);
        m.setMeetingType(MeetingType.ONLINE);
        m.setMeetingLink("https://meet.example.com/" + id);
        m.setNotes(notes);
        return m;
    }

    private static Task newTask(long id, OffsetDateTime dueDate, TaskStatus status) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Task " + id);
        t.setDueDate(dueDate);
        t.setStatus(status);
        return t;
    }

    private static Milestone newMilestone(long id, OffsetDateTime targetDate, MilestoneStatus status) {
        Milestone ms = new Milestone();
        ms.setId(id);
        ms.setTitle("Milestone " + id);
        ms.setTargetDate(targetDate);
        ms.setStatus(status);
        return ms;
    }

    // ── Authorisation ────────────────────────────────────────────────────────

    @Test
    void getTimeline_returnsForMentor() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getTimeline_returnsForMentee() {
        stubFound(MENTEE_ID);
        stubEmptyDomains();

        TimelineResponse r = service.getTimeline(MENTEE_ID, MID, null, null);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getTimeline_propagatesResourceNotFoundFromFinder() {
        when(mentorshipService.findForParticipant(999L, MID))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        assertThatThrownBy(() -> service.getTimeline(999L, MID, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Empty window ─────────────────────────────────────────────────────────

    @Test
    void getTimeline_emptyMentorshipReturnsEmptyItemsButPopulatedMetadata() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.mentorshipId()).isEqualTo(MID);
        assertThat(r.startDate()).isEqualTo(PROGRAM_START);
        assertThat(r.endDate()).isEqualTo(PROGRAM_END);
        assertThat(r.currentDate()).isEqualTo(FIXED_NOW);
        assertThat(r.items()).isEmpty();

        // Empty parent lists must short-circuit the bulk count queries (Hibernate 6 invalid SQL guard).
        verify(meetingActionItemRepository, never()).countByMeetingIds(anyCollection());
        verify(milestoneActionItemRepository, never()).countByMilestoneIds(anyCollection());
    }

    // ── Default window ──────────────────────────────────────────────────────

    @Test
    void getTimeline_defaultWindowUsesMentorshipDates() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();

        service.getTimeline(MENTOR_ID, MID, null, null);

        verify(meetingRepository).findInWindow(MID, PROGRAM_START, PROGRAM_END);
        verify(taskRepository).findInWindow(MID, PROGRAM_START, PROGRAM_END);
        verify(milestoneRepository).findInWindow(MID, PROGRAM_START, PROGRAM_END);
    }

    @Test
    void getTimeline_explicitFromOverridesDefault() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();
        OffsetDateTime customFrom = PROGRAM_START.plusDays(7);

        service.getTimeline(MENTOR_ID, MID, customFrom, null);

        verify(meetingRepository).findInWindow(MID, customFrom, PROGRAM_END);
    }

    @Test
    void getTimeline_explicitToOverridesDefault() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();
        OffsetDateTime customTo = PROGRAM_END.minusDays(7);

        service.getTimeline(MENTOR_ID, MID, null, customTo);

        verify(taskRepository).findInWindow(MID, PROGRAM_START, customTo);
    }

    // ── Window validation ──────────────────────────────────────────────────

    @Test
    void getTimeline_throwsWhenFromAfterTo() {
        stubFound(MENTOR_ID);
        OffsetDateTime laterFrom = PROGRAM_START.plusDays(10);
        OffsetDateTime earlierTo = PROGRAM_START.plusDays(5);

        assertThatThrownBy(() -> service.getTimeline(MENTOR_ID, MID, laterFrom, earlierTo))
                .isInstanceOf(InvalidTimelineWindowException.class)
                .hasMessageContaining("'from' must be before");
    }

    @Test
    void getTimeline_throwsWhenMentorshipHasNullProgramDates() {
        // Defensive guard for legacy rows that bypass DDL or are constructed in-memory.
        mentorship.setStartDate(null);
        mentorship.setEndDate(null);
        stubFound(MENTOR_ID);

        assertThatThrownBy(() -> service.getTimeline(MENTOR_ID, MID, null, null))
                .isInstanceOf(InvalidTimelineWindowException.class)
                .hasMessageContaining("no program window");
    }

    /**
     * Both arms of the short-circuit null check in validateWindow must throw. The other
     * test exercises the {@code from == null} arm; this one pins the {@code to == null}
     * arm to keep branch coverage at 100%.
     */
    @Test
    void validateWindow_throwsOnNullToWithNonNullFrom() {
        assertThatThrownBy(() ->
                MentorshipTimelineService.validateWindow(PROGRAM_START, null))
                .isInstanceOf(InvalidTimelineWindowException.class)
                .hasMessageContaining("no program window");
    }

    @Test
    void getTimeline_zeroWidthWindowIsValid() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();
        OffsetDateTime instant = PROGRAM_START.plusDays(15);

        assertThatCode(() -> service.getTimeline(MENTOR_ID, MID, instant, instant))
                .doesNotThrowAnyException();
    }

    @Test
    void getTimeline_exactly24MonthWindowIsValid() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();
        OffsetDateTime from = PROGRAM_START;
        OffsetDateTime to = PROGRAM_START.plusMonths(24);

        assertThatCode(() -> service.getTimeline(MENTOR_ID, MID, from, to))
                .doesNotThrowAnyException();
    }

    @Test
    void getTimeline_throwsWhenWindowIsOneDayPast24Months() {
        // Pins the cap. The earlier ChronoUnit-based check truncated; this one uses
        // plusMonths so even +1 day past the boundary trips the cap.
        stubFound(MENTOR_ID);
        OffsetDateTime from = PROGRAM_START;
        OffsetDateTime to = PROGRAM_START.plusMonths(24).plusDays(1);

        assertThatThrownBy(() -> service.getTimeline(MENTOR_ID, MID, from, to))
                .isInstanceOf(InvalidTimelineWindowException.class)
                .hasMessageContaining("24 months");
    }

    // ── Item shape ──────────────────────────────────────────────────────────

    @Test
    void meetingItem_carriesCounters_hasNotesTrueWhenNotesPresent() {
        stubFound(MENTOR_ID);
        OffsetDateTime t = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any()))
                .thenReturn(List.of(newMeeting(55L, t, MeetingStatus.CONFIRMED, "agenda")));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(meetingActionItemRepository.countByMeetingIds(any()))
                .thenReturn(List.of(new ActionItemCountTuple(55L, 3L, 1L)));

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);
        TimelineItem item = r.items().get(0);

        assertThat(item.type()).isEqualTo(TimelineItemType.MEETING);
        assertThat(item.id()).isEqualTo(55L);
        assertThat(item.occursAt()).isEqualTo(t);
        assertThat(item.status()).isEqualTo("CONFIRMED");
        assertThat(item.detailUrl()).isEqualTo("/api/meetings/55");
        assertThat(item.actionItemTotal()).isEqualTo(3L);
        assertThat(item.actionItemCompleted()).isEqualTo(1L);
        assertThat(item.hasNotes()).isTrue();
    }

    @Test
    void meetingItem_hasNotesFalseWhenNotesNullOrBlank() {
        stubFound(MENTOR_ID);
        OffsetDateTime t = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any()))
                .thenReturn(List.of(
                        newMeeting(1L, t, MeetingStatus.CONFIRMED, null),
                        newMeeting(2L, t.plusHours(1), MeetingStatus.CONFIRMED, "   ")));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(meetingActionItemRepository.countByMeetingIds(any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.items()).extracting(TimelineItem::hasNotes).containsExactly(false, false);
    }

    @Test
    void meetingItem_zeroCountersWhenNoActionItems() {
        stubFound(MENTOR_ID);
        OffsetDateTime t = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any()))
                .thenReturn(List.of(newMeeting(7L, t, MeetingStatus.CONFIRMED, null)));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(meetingActionItemRepository.countByMeetingIds(any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);
        TimelineItem item = r.items().get(0);

        assertThat(item.actionItemTotal()).isEqualTo(0L);
        assertThat(item.actionItemCompleted()).isEqualTo(0L);
    }

    @Test
    void taskItem_typeConditionalFieldsAreNull() {
        stubFound(MENTOR_ID);
        OffsetDateTime t = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(taskRepository.findInWindow(any(), any(), any()))
                .thenReturn(List.of(newTask(11L, t, TaskStatus.PENDING)));
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);
        TimelineItem item = r.items().get(0);

        assertThat(item.type()).isEqualTo(TimelineItemType.TASK);
        assertThat(item.detailUrl()).isEqualTo("/api/tasks/11");
        assertThat(item.actionItemTotal()).isNull();
        assertThat(item.actionItemCompleted()).isNull();
        assertThat(item.hasNotes()).isNull();
    }

    @Test
    void milestoneItem_carriesCounters_hasNotesAlwaysNull() {
        stubFound(MENTOR_ID);
        OffsetDateTime t = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(milestoneRepository.findInWindow(any(), any(), any()))
                .thenReturn(List.of(newMilestone(20L, t, MilestoneStatus.IN_PROGRESS)));
        when(milestoneActionItemRepository.countByMilestoneIds(any()))
                .thenReturn(List.of(new ActionItemCountTuple(20L, 5L, 2L)));

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);
        TimelineItem item = r.items().get(0);

        assertThat(item.type()).isEqualTo(TimelineItemType.MILESTONE);
        assertThat(item.detailUrl()).isEqualTo("/api/milestones/20");
        assertThat(item.actionItemTotal()).isEqualTo(5L);
        assertThat(item.actionItemCompleted()).isEqualTo(2L);
        assertThat(item.hasNotes()).isNull();
    }

    // ── Sort & tiebreaker ───────────────────────────────────────────────────

    @Test
    void items_sortedByOccursAtAscending() {
        stubFound(MENTOR_ID);
        OffsetDateTime base = PROGRAM_START.plusDays(1);
        when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newMeeting(1L, base.plusHours(2), MeetingStatus.CONFIRMED, null)));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newTask(2L, base, TaskStatus.PENDING)));
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newMilestone(3L, base.plusHours(4), MilestoneStatus.PENDING)));
        when(meetingActionItemRepository.countByMeetingIds(any())).thenReturn(List.of());
        when(milestoneActionItemRepository.countByMilestoneIds(any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.items()).extracting(TimelineItem::id).containsExactly(2L, 1L, 3L);
    }

    @Test
    void items_tiebreakerMilestoneBeforeMeetingBeforeTask() {
        stubFound(MENTOR_ID);
        OffsetDateTime sameInstant = PROGRAM_START.plusDays(5);
        when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newMeeting(101L, sameInstant, MeetingStatus.CONFIRMED, null)));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newTask(102L, sameInstant, TaskStatus.PENDING)));
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newMilestone(103L, sameInstant, MilestoneStatus.PENDING)));
        when(meetingActionItemRepository.countByMeetingIds(any())).thenReturn(List.of());
        when(milestoneActionItemRepository.countByMilestoneIds(any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.items()).extracting(TimelineItem::type).containsExactly(
                TimelineItemType.MILESTONE, TimelineItemType.MEETING, TimelineItemType.TASK);
    }

    @Test
    void items_tiebreakerByIdWithinSameType() {
        stubFound(MENTOR_ID);
        OffsetDateTime sameInstant = PROGRAM_START.plusDays(5);
        when(meetingRepository.findInWindow(any(), any(), any())).thenReturn(List.of(
                newMeeting(20L, sameInstant, MeetingStatus.CONFIRMED, null),
                newMeeting(10L, sameInstant, MeetingStatus.CONFIRMED, null)));
        when(taskRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(milestoneRepository.findInWindow(any(), any(), any())).thenReturn(List.of());
        when(meetingActionItemRepository.countByMeetingIds(any())).thenReturn(List.of());

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.items()).extracting(TimelineItem::id).containsExactly(10L, 20L);
    }

    // ── currentDate ─────────────────────────────────────────────────────────

    @Test
    void currentDate_isSourcedFromInjectedClock() {
        stubFound(MENTOR_ID);
        stubEmptyDomains();

        TimelineResponse r = service.getTimeline(MENTOR_ID, MID, null, null);

        // Use a different clock instant to prove we're reading from `clock`, not `OffsetDateTime.now()`.
        OffsetDateTime otherInstant = OffsetDateTime.of(2999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        Clock otherClock = Clock.fixed(Instant.parse("2999-01-01T00:00:00Z"), ZoneOffset.UTC);
        MentorshipTimelineService otherService = new MentorshipTimelineService(
                mentorshipService, meetingRepository, meetingActionItemRepository,
                taskRepository, milestoneRepository, milestoneActionItemRepository, otherClock);
        TimelineResponse other = otherService.getTimeline(MENTOR_ID, MID, null, null);

        assertThat(r.currentDate()).isEqualTo(FIXED_NOW);
        assertThat(other.currentDate()).isEqualTo(otherInstant);
    }

    // ── TimelineItem null-guard contract ────────────────────────────────────

    @Test
    void timelineItem_compactConstructorRejectsNullOnAlwaysPresentFields() {
        OffsetDateTime t = PROGRAM_START.plusDays(1);

        assertThatThrownBy(() -> new TimelineItem(null, 1L, "x", t, "OK", "/u", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> new TimelineItem(TimelineItemType.TASK, 1L, null, t, "OK", "/u", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> new TimelineItem(TimelineItemType.TASK, 1L, "x", null, "OK", "/u", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("occursAt");
        assertThatThrownBy(() -> new TimelineItem(TimelineItemType.TASK, 1L, "x", t, null, "/u", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> new TimelineItem(TimelineItemType.TASK, 1L, "x", t, "OK", null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("detailUrl");
    }
}
