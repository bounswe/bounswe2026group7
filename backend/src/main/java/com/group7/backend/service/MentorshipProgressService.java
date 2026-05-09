package com.group7.backend.service;

import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MilestoneStatus;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.TaskSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Read-only aggregator for mentorship progress (#334, spec 1.1.5.5).
 *
 * <p>Combines task and milestone counts into one payload so dashboards can render a
 * percentage and a "last activity" label without orchestrating the math client-side.
 */
@Service
public class MentorshipProgressService {

    private final MentorshipService mentorshipService;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final MilestoneRepository milestoneRepository;

    public MentorshipProgressService(MentorshipService mentorshipService,
                                     TaskRepository taskRepository,
                                     TaskSubmissionRepository taskSubmissionRepository,
                                     MilestoneRepository milestoneRepository) {
        this.mentorshipService = mentorshipService;
        this.taskRepository = taskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @Transactional(readOnly = true)
    public MentorshipProgressResponse getProgress(Long userId, Long mentorshipId) {
        Mentorship mentorship = mentorshipService.findForParticipant(userId, mentorshipId);
        long mid = mentorship.getId();

        long taskTotal = taskRepository.countByMentorshipId(mid);
        long taskCompleted = taskRepository.countByMentorshipIdAndStatus(mid, TaskStatus.COMPLETED);
        long taskSubmitted = taskRepository.countByMentorshipIdAndStatus(mid, TaskStatus.SUBMITTED);

        long milestoneTotal = milestoneRepository.countByMentorshipId(mid);
        long milestoneCompleted =
                milestoneRepository.countByMentorshipIdAndStatus(mid, MilestoneStatus.COMPLETED);

        float progressPercentage = computePercentage(
                taskTotal, taskCompleted, milestoneTotal, milestoneCompleted);

        OffsetDateTime lastActivityAt = computeLastActivity(mid);

        return new MentorshipProgressResponse(
                mid,
                taskTotal,
                taskCompleted,
                taskSubmitted,
                milestoneTotal,
                milestoneCompleted,
                progressPercentage,
                lastActivityAt
        );
    }

    /**
     * Equal-weight when both surfaces have entries; single-surface ratio when only one does;
     * {@code 0.0} when both are empty. Result is in [0.0, 1.0]; never NaN.
     */
    private static float computePercentage(long taskTotal, long taskCompleted,
                                           long milestoneTotal, long milestoneCompleted) {
        boolean hasTasks = taskTotal > 0;
        boolean hasMilestones = milestoneTotal > 0;

        if (!hasTasks && !hasMilestones) {
            return 0.0f;
        }
        if (!hasTasks) {
            return (float) milestoneCompleted / milestoneTotal;
        }
        if (!hasMilestones) {
            return (float) taskCompleted / taskTotal;
        }
        float taskRatio = (float) taskCompleted / taskTotal;
        float milestoneRatio = (float) milestoneCompleted / milestoneTotal;
        return 0.5f * taskRatio + 0.5f * milestoneRatio;
    }

    private OffsetDateTime computeLastActivity(long mentorshipId) {
        OffsetDateTime latest = null;
        latest = laterOf(latest, taskSubmissionRepository.findMaxSubmittedAtForMentorship(mentorshipId));
        latest = laterOf(latest, taskSubmissionRepository.findMaxReviewedAtForMentorship(mentorshipId));
        latest = laterOf(latest, milestoneRepository.findMaxCompletedAtForMentorship(mentorshipId));
        return latest;
    }

    private static OffsetDateTime laterOf(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
