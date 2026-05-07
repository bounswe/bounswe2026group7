package com.group7.backend.service;

import com.group7.backend.config.MeetingProperties;
import com.group7.backend.dto.request.*;
import com.group7.backend.dto.response.*;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MeetingConflictException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);

    private static final List<MeetingStatus> CONFLICT_STATUSES =
            List.of(MeetingStatus.PENDING_CONFIRMATION, MeetingStatus.CONFIRMED);

    private final MeetingRepository meetingRepository;
    private final MeetingActionItemRepository actionItemRepository;
    private final MeetingRescheduleRequestRepository rescheduleRepository;
    private final MentorshipRepository mentorshipRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    private final int confirmationWindowHours;
    private final int confirmationMinHoursBeforeStart;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingActionItemRepository actionItemRepository,
                          MeetingRescheduleRequestRepository rescheduleRepository,
                          MentorshipRepository mentorshipRepository,
                          AvailabilitySlotRepository availabilitySlotRepository,
                          UserRepository userRepository,
                          NotificationEventPublisher notificationEventPublisher,
                          Clock clock,
                          MeetingProperties properties) {
        this.meetingRepository = meetingRepository;
        this.actionItemRepository = actionItemRepository;
        this.rescheduleRepository = rescheduleRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
        this.confirmationWindowHours = properties.confirmationWindowHours();
        this.confirmationMinHoursBeforeStart = properties.confirmationMinHoursBeforeStart();
    }

    @Transactional
    public MeetingCreateResponse createMeetings(Long mentorshipId, Long userId, MeetingCreateRequest request) {
        Mentorship mentorship = getActiveMentorshipForUser(mentorshipId, userId);
        requireMentor(mentorship, userId);

        validateTimeRange(request.getStartTime(), request.getEndTime());
        validateMeetingLink(request.getMeetingType(), request.getMeetingLink());

        String recurrenceRule = normaliseRecurrenceRule(request.isRecurring(), request.getRecurrenceRule());
        int intervalWeeks = request.isRecurring() ? parseWeeklyInterval(recurrenceRule) : 0;

        List<Meeting> meetings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        OffsetDateTime start = request.getStartTime();
        OffsetDateTime end = request.getEndTime();
        OffsetDateTime mentorshipEnd = mentorship.getEndDate();

        if (start.isAfter(mentorshipEnd)) {
            throw new MeetingConflictException("Meeting start time must be within the mentorship duration");
        }

        do {
            ensureNoConflicts(mentorship, start, end, null);
            warnIfOutsideAvailability(mentorship.getMentor().getId(), start, end, warnings);

            Meeting meeting = new Meeting();
            meeting.setMentorship(mentorship);
            meeting.setTitle(request.getTitle());
            meeting.setDescription(request.getDescription());
            meeting.setStartTime(start);
            meeting.setEndTime(end);
            meeting.setStatus(MeetingStatus.PENDING_CONFIRMATION);
            meeting.setMeetingType(request.getMeetingType());
            meeting.setMeetingLink(request.getMeetingLink());
            meeting.setRecurring(request.isRecurring());
            meeting.setRecurrenceRule(recurrenceRule);
            meeting.setCreatedBy(loadUser(userId));
            meeting.setConfirmationDeadline(calculateConfirmationDeadline(start));
            meetings.add(meeting);

            if (!request.isRecurring()) {
                break;
            }
            start = start.plusWeeks(intervalWeeks);
            end = end.plusWeeks(intervalWeeks);
        } while (start.isBefore(mentorshipEnd));

        List<Meeting> saved = meetingRepository.saveAll(meetings);

        for (Meeting savedMeeting : saved) {
            notificationEventPublisher.publishMeetingPendingConfirmation(
                mentorship.getMentee().getId(), mentorship.getMentor().getFirstName());
        }

        MeetingCreateResponse response = new MeetingCreateResponse();
        response.setMeetings(saved.stream().map(MeetingSummaryResponse::from).toList());
        response.setWarnings(warnings);
        return response;
    }

    @Transactional(readOnly = true)
    public List<MeetingSummaryResponse> listMeetings(Long mentorshipId, Long userId) {
        Mentorship mentorship = getActiveMentorshipForUser(mentorshipId, userId);
        return meetingRepository.findByMentorshipIdOrderByStartTimeAsc(mentorship.getId()).stream()
                .map(MeetingSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getMeeting(Long meetingId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        List<MeetingActionItemResponse> items = actionItemRepository
                .findByMeetingIdOrderByOrderIndexAscIdAsc(meetingId)
                .stream()
                .map(MeetingActionItemResponse::from)
                .toList();
        MeetingRescheduleRequestResponse pending = rescheduleRepository
                .findByMeetingIdAndStatus(meetingId, MeetingRescheduleStatus.PENDING)
                .map(MeetingRescheduleRequestResponse::from)
                .orElse(null);
        return MeetingDetailResponse.from(meeting, items, pending);
    }

    @Transactional
    public MeetingSummaryResponse confirmMeeting(Long meetingId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        requireMentee(meeting.getMentorship(), userId);
        if (meeting.getStatus() != MeetingStatus.PENDING_CONFIRMATION) {
            throw new MeetingConflictException("Meeting is not awaiting confirmation");
        }
        meeting.setStatus(MeetingStatus.CONFIRMED);
        meeting.setConfirmedAt(OffsetDateTime.now(clock));
        Meeting saved = meetingRepository.save(meeting);
        notificationEventPublisher.publishMeetingConfirmed(
                meeting.getMentorship().getMentor().getId(),
                meeting.getMentorship().getMentee().getFirstName());
        return MeetingSummaryResponse.from(saved);
    }

    @Transactional
    public MeetingSummaryResponse declineMeeting(Long meetingId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        requireMentee(meeting.getMentorship(), userId);
        if (meeting.getStatus() != MeetingStatus.PENDING_CONFIRMATION) {
            throw new MeetingConflictException("Meeting is not awaiting confirmation");
        }
        meeting.setStatus(MeetingStatus.DECLINED);
        Meeting saved = meetingRepository.save(meeting);
        notificationEventPublisher.publishMeetingDeclined(
                meeting.getMentorship().getMentor().getId(),
                meeting.getMentorship().getMentee().getFirstName());
        return MeetingSummaryResponse.from(saved);
    }

    @Transactional
    public MeetingRescheduleRequestResponse requestReschedule(Long meetingId, Long userId, MeetingRescheduleCreateRequest request) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        if (!canReschedule(meeting.getStatus())) {
            throw new MeetingConflictException("Meeting cannot be rescheduled in its current state");
        }
        validateTimeRange(request.getProposedStart(), request.getProposedEnd());

        if (rescheduleRepository.findByMeetingIdAndStatus(meetingId, MeetingRescheduleStatus.PENDING).isPresent()) {
            throw new MeetingConflictException("A pending reschedule request already exists for this meeting");
        }

        ensureNoConflicts(meeting.getMentorship(), request.getProposedStart(), request.getProposedEnd(), meeting.getId());

        MeetingRescheduleRequest entity = new MeetingRescheduleRequest();
        entity.setMeeting(meeting);
        entity.setRequestedBy(loadUser(userId));
        entity.setProposedStart(request.getProposedStart());
        entity.setProposedEnd(request.getProposedEnd());
        entity.setReason(request.getReason());

        MeetingRescheduleRequest saved = rescheduleRepository.save(entity);

        Long counterpartId = counterpartUserId(meeting.getMentorship(), userId);
        String requesterName = displayName(userId, meeting.getMentorship());
        notificationEventPublisher.publishMeetingRescheduleRequested(counterpartId, requesterName);

        return MeetingRescheduleRequestResponse.from(saved);
    }

    @Transactional
    public MeetingRescheduleRequestResponse approveReschedule(Long meetingId, Long requestId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        MeetingRescheduleRequest req = rescheduleRepository.findById(requestId)
                .filter(r -> r.getMeeting().getId().equals(meetingId))
                .orElseThrow(() -> new ResourceNotFoundException("Reschedule request not found"));

        requireCounterpart(req, userId);
        if (req.getStatus() != MeetingRescheduleStatus.PENDING) {
            throw new MeetingConflictException("Reschedule request is not pending");
        }
        if (!canReschedule(meeting.getStatus())) {
            throw new MeetingConflictException("Meeting cannot be rescheduled in its current state");
        }

        ensureNoConflicts(meeting.getMentorship(), req.getProposedStart(), req.getProposedEnd(), meeting.getId());

        meeting.setStartTime(req.getProposedStart());
        meeting.setEndTime(req.getProposedEnd());
        if (meeting.getStatus() == MeetingStatus.PENDING_CONFIRMATION) {
            meeting.setStatus(MeetingStatus.CONFIRMED);
            meeting.setConfirmedAt(OffsetDateTime.now(clock));
        }
        meetingRepository.save(meeting);

        req.setStatus(MeetingRescheduleStatus.APPROVED);
        req.setDecidedAt(OffsetDateTime.now(clock));
        MeetingRescheduleRequest saved = rescheduleRepository.save(req);

        notificationEventPublisher.publishMeetingRescheduleApproved(
                req.getRequestedBy().getId(), displayName(userId, meeting.getMentorship()));
        return MeetingRescheduleRequestResponse.from(saved);
    }

    @Transactional
    public MeetingRescheduleRequestResponse rejectReschedule(Long meetingId, Long requestId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        MeetingRescheduleRequest req = rescheduleRepository.findById(requestId)
                .filter(r -> r.getMeeting().getId().equals(meetingId))
                .orElseThrow(() -> new ResourceNotFoundException("Reschedule request not found"));

        requireCounterpart(req, userId);
        if (req.getStatus() != MeetingRescheduleStatus.PENDING) {
            throw new MeetingConflictException("Reschedule request is not pending");
        }

        req.setStatus(MeetingRescheduleStatus.REJECTED);
        req.setDecidedAt(OffsetDateTime.now(clock));
        MeetingRescheduleRequest saved = rescheduleRepository.save(req);

        notificationEventPublisher.publishMeetingRescheduleRejected(
                req.getRequestedBy().getId(), displayName(userId, meeting.getMentorship()));
        return MeetingRescheduleRequestResponse.from(saved);
    }

    @Transactional
    public MeetingSummaryResponse cancelMeeting(Long meetingId, Long userId) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        requireMentor(meeting.getMentorship(), userId);
        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new MeetingConflictException("Meeting cannot be cancelled in its current state");
        }
        meeting.setStatus(MeetingStatus.CANCELLED);
        Meeting saved = meetingRepository.save(meeting);
        notificationEventPublisher.publishMeetingCancelled(
                meeting.getMentorship().getMentee().getId(),
                meeting.getMentorship().getMentor().getFirstName());
        return MeetingSummaryResponse.from(saved);
    }

    @Transactional
    public MeetingDetailResponse updateNotes(Long meetingId, Long userId, MeetingNotesRequest request) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        meeting.setNotes(request.getNotes());
        Meeting saved = meetingRepository.save(meeting);
        List<MeetingActionItemResponse> items = actionItemRepository
                .findByMeetingIdOrderByOrderIndexAscIdAsc(meetingId)
                .stream()
                .map(MeetingActionItemResponse::from)
                .toList();
        return MeetingDetailResponse.from(saved, items, pendingReschedule(meetingId));
    }

    @Transactional
    public MeetingActionItemResponse addActionItem(Long meetingId, Long userId, MeetingActionItemCreateRequest request) {
        Meeting meeting = getMeetingForUser(meetingId, userId);
        MeetingActionItem item = new MeetingActionItem();
        item.setMeeting(meeting);
        item.setText(request.getText());
        item.setCreatedBy(loadUser(userId));
        item.setOrderIndex(resolveOrderIndex(meetingId, request.getOrderIndex()));
        return MeetingActionItemResponse.from(actionItemRepository.save(item));
    }

    @Transactional
    public MeetingActionItemResponse updateActionItem(Long itemId, Long userId, MeetingActionItemUpdateRequest request) {
        MeetingActionItem item = actionItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));
        Meeting meeting = getMeetingForUser(item.getMeeting().getId(), userId);

        if (request.getText() != null) {
            if (!Objects.equals(item.getCreatedBy().getId(), userId)) {
                throw new ProfileNotVisibleException("Only the creator can edit the action item text");
            }
            item.setText(request.getText());
        }

        if (request.getCompleted() != null) {
            boolean completed = request.getCompleted();
            item.setCompleted(completed);
            item.setCompletedAt(completed ? OffsetDateTime.now(clock) : null);
            item.setCompletedBy(completed ? loadUser(userId) : null);
        }

        return MeetingActionItemResponse.from(actionItemRepository.save(item));
    }

    @Transactional
    public void deleteActionItem(Long itemId, Long userId) {
        MeetingActionItem item = actionItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));
        getMeetingForUser(item.getMeeting().getId(), userId);
        if (!Objects.equals(item.getCreatedBy().getId(), userId)) {
            throw new ProfileNotVisibleException("Only the creator can delete the action item");
        }
        actionItemRepository.delete(item);
    }

    private Mentorship getActiveMentorshipForUser(Long mentorshipId, Long userId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MeetingConflictException("Mentorship is not active");
        }
        if (!isParticipant(mentorship, userId)) {
            throw new ProfileNotVisibleException("You can only access your own mentorship meetings");
        }
        return mentorship;
    }

    private Meeting getMeetingForUser(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        Mentorship mentorship = meeting.getMentorship();
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MeetingConflictException("Mentorship is not active");
        }
        if (!isParticipant(mentorship, userId)) {
            throw new ProfileNotVisibleException("You can only access your own mentorship meetings");
        }
        return meeting;
    }

    private boolean isParticipant(Mentorship mentorship, Long userId) {
        return mentorship.getMentor().getId().equals(userId)
                || mentorship.getMentee().getId().equals(userId);
    }

    private void requireMentor(Mentorship mentorship, Long userId) {
        if (!mentorship.getMentor().getId().equals(userId)) {
            throw new ProfileNotVisibleException("Only the mentor can perform this action");
        }
    }

    private void requireMentee(Mentorship mentorship, Long userId) {
        if (!mentorship.getMentee().getId().equals(userId)) {
            throw new ProfileNotVisibleException("Only the mentee can perform this action");
        }
    }

    private void requireCounterpart(MeetingRescheduleRequest request, Long userId) {
        if (request.getRequestedBy().getId().equals(userId)) {
            throw new ProfileNotVisibleException("Only the counterpart can decide this request");
        }
    }

    private boolean canReschedule(MeetingStatus status) {
        return status == MeetingStatus.CONFIRMED || status == MeetingStatus.PENDING_CONFIRMATION;
    }

    private void validateTimeRange(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end time are required");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (start.isBefore(OffsetDateTime.now(clock).minusMinutes(1))) {
            throw new IllegalArgumentException("Meeting start time must be in the future");
        }
    }

    private void validateMeetingLink(MeetingType type, String link) {
        if (type == MeetingType.ONLINE) {
            if (link == null || link.isBlank()) {
                throw new IllegalArgumentException("Meeting link is required for online meetings");
            }
            if (!isValidUrl(link)) {
                throw new IllegalArgumentException("Meeting link must be a valid URL");
            }
        }
    }

    private boolean isValidUrl(String link) {
        try {
            URI uri = URI.create(link);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String normaliseRecurrenceRule(boolean recurring, String rule) {
        if (!recurring) {
            return null;
        }
        if (rule == null || rule.isBlank()) {
            return "FREQ=WEEKLY;INTERVAL=1";
        }
        return rule.trim().toUpperCase(Locale.ROOT);
    }

    private int parseWeeklyInterval(String rule) {
        String normalized = rule.toUpperCase(Locale.ROOT);
        if (!normalized.contains("FREQ=WEEKLY")) {
            throw new IllegalArgumentException("Only weekly recurrence is supported");
        }
        int interval = 1;
        String[] parts = normalized.split(";");
        for (String part : parts) {
            if (part.startsWith("INTERVAL=")) {
                interval = Integer.parseInt(part.substring("INTERVAL=".length()));
            }
        }
        if (interval < 1) {
            throw new IllegalArgumentException("Recurrence interval must be >= 1");
        }
        return interval;
    }

    private OffsetDateTime calculateConfirmationDeadline(OffsetDateTime startTime) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime deadlineByWindow = now.plusHours(confirmationWindowHours);
        OffsetDateTime deadlineByStart = startTime.minusHours(confirmationMinHoursBeforeStart);
        OffsetDateTime deadline = deadlineByWindow.isBefore(deadlineByStart)
                ? deadlineByWindow
                : deadlineByStart;
        if (deadline.isBefore(now)) {
            return now;
        }
        return deadline;
    }

    private void ensureNoConflicts(Mentorship mentorship,
                                   OffsetDateTime start,
                                   OffsetDateTime end,
                                   Long excludeMeetingId) {
        boolean overlapMeetings = excludeMeetingId == null
                ? meetingRepository.existsOverlappingForParticipants(
                    mentorship.getMentor().getId(), mentorship.getMentee().getId(),
                    start, end, CONFLICT_STATUSES)
                : meetingRepository.existsOverlappingForParticipantsExcludingMeeting(
                    excludeMeetingId,
                    mentorship.getMentor().getId(), mentorship.getMentee().getId(),
                    start, end, CONFLICT_STATUSES);
        if (overlapMeetings) {
            throw new MeetingConflictException("Meeting time conflicts with another scheduled meeting");
        }

        boolean overlapReschedules = excludeMeetingId == null
                ? rescheduleRepository.existsOverlappingPendingForParticipants(
                    mentorship.getMentor().getId(), mentorship.getMentee().getId(),
                    start, end, MeetingRescheduleStatus.PENDING)
                : rescheduleRepository.existsOverlappingPendingForParticipantsExcludingMeeting(
                    excludeMeetingId,
                    mentorship.getMentor().getId(), mentorship.getMentee().getId(),
                    start, end, MeetingRescheduleStatus.PENDING);
        if (overlapReschedules) {
            throw new MeetingConflictException("Meeting time conflicts with a pending reschedule request");
        }
    }

    private void warnIfOutsideAvailability(Long mentorId,
                                           OffsetDateTime start,
                                           OffsetDateTime end,
                                           List<String> warnings) {
        if (!isWithinAvailability(mentorId, start, end)) {
            warnings.add("Meeting time is outside the mentor's availability window: " + start + " - " + end);
        }
    }

    private boolean isWithinAvailability(Long mentorId, OffsetDateTime start, OffsetDateTime end) {
        DayOfWeek day = start.getDayOfWeek();
        LocalTime startLocal = start.toLocalTime();
        LocalTime endLocal = end.toLocalTime();

        return availabilitySlotRepository.findByMentorId(mentorId).stream()
                .filter(slot -> slot.getDayOfWeek() == day)
                .anyMatch(slot -> startLocal.compareTo(slot.getStartTime()) >= 0
                        && endLocal.compareTo(slot.getEndTime()) <= 0);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private int resolveOrderIndex(Long meetingId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        return actionItemRepository.findByMeetingIdOrderByOrderIndexAscIdAsc(meetingId).stream()
                .map(MeetingActionItem::getOrderIndex)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private MeetingRescheduleRequestResponse pendingReschedule(Long meetingId) {
        return rescheduleRepository.findByMeetingIdAndStatus(meetingId, MeetingRescheduleStatus.PENDING)
                .map(MeetingRescheduleRequestResponse::from)
                .orElse(null);
    }

    private Long counterpartUserId(Mentorship mentorship, Long userId) {
        if (mentorship.getMentor().getId().equals(userId)) {
            return mentorship.getMentee().getId();
        }
        return mentorship.getMentor().getId();
    }

    private String displayName(Long userId, Mentorship mentorship) {
        if (mentorship.getMentor().getId().equals(userId)) {
            return mentorship.getMentor().getFirstName();
        }
        return mentorship.getMentee().getFirstName();
    }
}
