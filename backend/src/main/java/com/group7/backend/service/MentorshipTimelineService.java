package com.group7.backend.service;

import com.group7.backend.dto.response.TimelineItem;
import com.group7.backend.dto.response.TimelineItemType;
import com.group7.backend.dto.response.TimelineResponse;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.Task;
import com.group7.backend.exception.InvalidTimelineWindowException;
import com.group7.backend.repository.MeetingActionItemRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MilestoneActionItemRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.projection.ActionItemCountTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only aggregator for the mentorship timeline (#332, spec 1.1.5.9-1.1.5.13).
 *
 * <p>Merges meetings + tasks + milestones into one chronologically-sorted feed scoped to a
 * window (default = mentorship.startDate..endDate, capped at {@value #MAX_WINDOW_MONTHS}
 * months). Action-item counts are bulk-loaded via grouped queries; sort is performed in
 * Java because Hibernate has no clean cross-entity union-sort idiom.
 *
 * <p>Stateless and thread-safe.
 */
@Service
public class MentorshipTimelineService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipTimelineService.class);

    private static final long MAX_WINDOW_MONTHS = 24;

    private static final String MEETING_DETAIL_URL_PREFIX = "/api/meetings/";
    private static final String TASK_DETAIL_URL_PREFIX = "/api/tasks/";
    private static final String MILESTONE_DETAIL_URL_PREFIX = "/api/milestones/";

    private final MentorshipService mentorshipService;
    private final MeetingRepository meetingRepository;
    private final MeetingActionItemRepository meetingActionItemRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneActionItemRepository milestoneActionItemRepository;
    private final Clock clock;

    public MentorshipTimelineService(MentorshipService mentorshipService,
                                     MeetingRepository meetingRepository,
                                     MeetingActionItemRepository meetingActionItemRepository,
                                     TaskRepository taskRepository,
                                     MilestoneRepository milestoneRepository,
                                     MilestoneActionItemRepository milestoneActionItemRepository,
                                     Clock clock) {
        this.mentorshipService = mentorshipService;
        this.meetingRepository = meetingRepository;
        this.meetingActionItemRepository = meetingActionItemRepository;
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.milestoneActionItemRepository = milestoneActionItemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TimelineResponse getTimeline(Long userId, Long mentorshipId,
                                        OffsetDateTime from, OffsetDateTime to) {
        // 1. Auth — non-participants get 404 here, before any window math.
        Mentorship mentorship = mentorshipService.findForParticipant(userId, mentorshipId);

        // 2. Window — defaults from the mentorship; validation throws 400.
        OffsetDateTime windowFrom = from != null ? from : mentorship.getStartDate();
        OffsetDateTime windowTo = to != null ? to : mentorship.getEndDate();
        validateWindow(windowFrom, windowTo);

        // 3. Fetch each domain in the window.
        List<Meeting> meetings = meetingRepository.findInWindow(mentorshipId, windowFrom, windowTo);
        List<Task> tasks = taskRepository.findInWindow(mentorshipId, windowFrom, windowTo);
        List<Milestone> milestones = milestoneRepository.findInWindow(mentorshipId, windowFrom, windowTo);

        // 4. Bulk action-item counters keyed by parent id (avoids N+1).
        //    Empty-list short-circuit — Hibernate 6 emits invalid SQL on `WHERE x IN ()`.
        Map<Long, ActionItemCounts> meetingAi = meetings.isEmpty()
                ? Map.of()
                : indexCounts(meetingActionItemRepository.countByMeetingIds(idsOfMeetings(meetings)));
        Map<Long, ActionItemCounts> milestoneAi = milestones.isEmpty()
                ? Map.of()
                : indexCounts(milestoneActionItemRepository.countByMilestoneIds(idsOfMilestones(milestones)));

        // 5. Map to uniform TimelineItem; null means "not applicable to this type".
        List<TimelineItem> items = new ArrayList<>(meetings.size() + tasks.size() + milestones.size());
        meetings.forEach(m -> items.add(
                toMeetingItem(m, meetingAi.getOrDefault(m.getId(), ActionItemCounts.ZERO))));
        tasks.forEach(t -> items.add(toTaskItem(t)));
        milestones.forEach(ms -> items.add(
                toMilestoneItem(ms, milestoneAi.getOrDefault(ms.getId(), ActionItemCounts.ZERO))));

        // 6. Stable sort: occursAt asc, then MILESTONE > MEETING > TASK, then id asc.
        items.sort(
                Comparator.comparing(TimelineItem::occursAt)
                        .thenComparingInt(it -> it.type().priority())
                        .thenComparingLong(TimelineItem::id));

        log.debug("timeline userId={} mentorshipId={} window=[{}, {}] meetings={} tasks={} milestones={}",
                userId, mentorshipId, windowFrom, windowTo,
                meetings.size(), tasks.size(), milestones.size());

        return new TimelineResponse(
                mentorshipId,
                mentorship.getStartDate(),
                mentorship.getEndDate(),
                OffsetDateTime.now(clock),
                items);
    }

    /** Internal value type for bulk-loaded action-item counts. */
    private record ActionItemCounts(long total, long completed) {
        static final ActionItemCounts ZERO = new ActionItemCounts(0L, 0L);
    }

    static void validateWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new InvalidTimelineWindowException(
                    "Mentorship has no program window; supply 'from' and 'to'");
        }
        if (from.isAfter(to)) {
            throw new InvalidTimelineWindowException("'from' must be before or equal to 'to'");
        }
        // Use plusMonths comparison instead of ChronoUnit.MONTHS.between(...), which truncates
        // to whole months and silently lets ~1 month of slack past the 24-month cap.
        if (to.isAfter(from.plusMonths(MAX_WINDOW_MONTHS))) {
            throw new InvalidTimelineWindowException(
                    "Timeline window cannot exceed " + MAX_WINDOW_MONTHS + " months");
        }
    }

    private static Collection<Long> idsOfMeetings(List<Meeting> meetings) {
        return meetings.stream().map(Meeting::getId).toList();
    }

    private static Collection<Long> idsOfMilestones(List<Milestone> milestones) {
        return milestones.stream().map(Milestone::getId).toList();
    }

    private static Map<Long, ActionItemCounts> indexCounts(List<ActionItemCountTuple> tuples) {
        return tuples.stream().collect(Collectors.toUnmodifiableMap(
                ActionItemCountTuple::parentId,
                t -> new ActionItemCounts(t.total(), t.completed())));
    }

    private static TimelineItem toMeetingItem(Meeting m, ActionItemCounts counts) {
        boolean hasNotes = m.getNotes() != null && !m.getNotes().isBlank();
        return new TimelineItem(
                TimelineItemType.MEETING,
                m.getId(),
                m.getTitle(),
                m.getStartTime(),
                m.getStatus().name(),
                MEETING_DETAIL_URL_PREFIX + m.getId(),
                counts.total(),
                counts.completed(),
                hasNotes);
    }

    private static TimelineItem toTaskItem(Task t) {
        return new TimelineItem(
                TimelineItemType.TASK,
                t.getId(),
                t.getTitle(),
                t.getDueDate(),
                t.getStatus().name(),
                TASK_DETAIL_URL_PREFIX + t.getId(),
                null,
                null,
                null);
    }

    private static TimelineItem toMilestoneItem(Milestone ms, ActionItemCounts counts) {
        return new TimelineItem(
                TimelineItemType.MILESTONE,
                ms.getId(),
                ms.getTitle(),
                ms.getTargetDate(),
                ms.getStatus().name(),
                MILESTONE_DETAIL_URL_PREFIX + ms.getId(),
                counts.total(),
                counts.completed(),
                null);
    }
}
