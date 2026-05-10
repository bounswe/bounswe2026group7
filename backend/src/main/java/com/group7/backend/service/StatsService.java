package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeStatsResponse;
import com.group7.backend.dto.response.MentorStatsResponse;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * Read-only aggregator for mentor and mentee dashboards (#253).
 *
 * <p>Each public method issues a small fixed set of single-row COUNT/SUM queries; no
 * collections are loaded. Authorization is implicit: the caller's user id (from the
 * security context) is the only filter, so a mentor cannot see another mentor's stats
 * and a mentee cannot see another mentee's.
 */
@Service
public class StatsService {

    /**
     * Statuses that represent a meeting still on the mentee's calendar (#253). Confirmed
     * sessions count, and so do ones still awaiting the other party's confirmation —
     * both are upcoming work the dashboard should surface.
     */
    private static final EnumSet<MeetingStatus> UPCOMING_MEETING_STATUSES =
            EnumSet.of(MeetingStatus.PENDING_CONFIRMATION, MeetingStatus.CONFIRMED);

    /**
     * Pending task statuses for the mentee (#253). REVISION_REQUESTED is included
     * because the mentee still owes work — same convention as
     * {@code TaskRepository.findPendingTasksDueWithin}.
     */
    private static final EnumSet<TaskStatus> PENDING_TASK_STATUSES =
            EnumSet.of(TaskStatus.PENDING, TaskStatus.REVISION_REQUESTED);

    private final MentorshipRepository mentorshipRepository;
    private final MentorshipRequestRepository mentorshipRequestRepository;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;

    public StatsService(MentorshipRepository mentorshipRepository,
                        MentorshipRequestRepository mentorshipRequestRepository,
                        TaskRepository taskRepository,
                        MeetingRepository meetingRepository) {
        this.mentorshipRepository = mentorshipRepository;
        this.mentorshipRequestRepository = mentorshipRequestRepository;
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public MentorStatsResponse getMentorStats(Long mentorId) {
        long totalMentees = mentorshipRepository.countDistinctMenteesByMentorId(mentorId);
        long active = mentorshipRepository.countByMentorIdAndStatus(
                mentorId, MentorshipStatus.ACTIVE);
        long completed = mentorshipRepository.countByMentorIdAndStatus(
                mentorId, MentorshipStatus.COMPLETED);
        long tasksAssigned = taskRepository.countByMentorId(mentorId);
        long tasksCompleted = taskRepository.countByMentorIdAndStatus(
                mentorId, TaskStatus.COMPLETED);
        double meetingHours = meetingRepository.sumCompletedMeetingHoursForMentor(mentorId);
        long pendingRequests = mentorshipRequestRepository.countByMentor_IdAndStatus(
                mentorId, MentorshipRequestStatus.PENDING);

        // Rating aggregation lands with #237 (MentorRating). Until that PR merges into
        // dev, return null/0 so the contract is stable for the dashboard.
        Double averageRating = null;
        long ratingCount = 0L;

        return new MentorStatsResponse(
                totalMentees,
                active,
                completed,
                tasksAssigned,
                tasksCompleted,
                meetingHours,
                averageRating,
                ratingCount,
                pendingRequests
        );
    }

    @Transactional(readOnly = true)
    public MenteeStatsResponse getMenteeStats(Long menteeId) {
        long active = mentorshipRepository.countByMenteeIdAndStatus(
                menteeId, MentorshipStatus.ACTIVE);
        long completedTasks = taskRepository.countByMenteeIdAndStatus(
                menteeId, TaskStatus.COMPLETED);
        long pendingTasks = taskRepository.countByMenteeIdAndStatusIn(
                menteeId, PENDING_TASK_STATUSES);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long upcomingMeetings = meetingRepository.countUpcomingByMenteeId(
                menteeId, now, UPCOMING_MEETING_STATUSES);
        long mentorsWorkedWith = mentorshipRepository.countDistinctMentorsByMenteeId(menteeId);
        long requestsSent = mentorshipRequestRepository.countByMentee_Id(menteeId);

        return new MenteeStatsResponse(
                active,
                completedTasks,
                pendingTasks,
                upcomingMeetings,
                mentorsWorkedWith,
                requestsSent
        );
    }
}
