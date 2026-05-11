package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.Task;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.SentMilestoneReminderRepository;
import com.group7.backend.repository.SentTaskReminderRepository;
import com.group7.backend.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removes data hanging off a mentorship when it is cancelled or otherwise
 * terminated (#133). Meeting / Task / Milestone / Conversation children all
 * have ON DELETE CASCADE on their parent FKs, so deleting them by id is
 * enough — their grandchildren (action items, submissions, attachments,
 * messages, participants) follow.
 *
 * <p>The two {@code sent_*_reminders} tables are reminder dedup rows tracked
 * by a plain {@code task_id} / {@code milestone_id} column without an FK
 * constraint, so they need explicit cleanup before the parent rows go away.
 *
 * <p>Operates within the caller's transaction (Propagation.REQUIRED) so the
 * status flip + cleanup either both commit or both roll back.
 */
@Service
public class MentorshipCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipCleanupService.class);

    private final TaskRepository taskRepository;
    private final SentTaskReminderRepository sentTaskReminderRepository;
    private final MilestoneRepository milestoneRepository;
    private final SentMilestoneReminderRepository sentMilestoneReminderRepository;
    private final MeetingRepository meetingRepository;
    private final ConversationRepository conversationRepository;

    public MentorshipCleanupService(TaskRepository taskRepository,
                                    SentTaskReminderRepository sentTaskReminderRepository,
                                    MilestoneRepository milestoneRepository,
                                    SentMilestoneReminderRepository sentMilestoneReminderRepository,
                                    MeetingRepository meetingRepository,
                                    ConversationRepository conversationRepository) {
        this.taskRepository = taskRepository;
        this.sentTaskReminderRepository = sentTaskReminderRepository;
        this.milestoneRepository = milestoneRepository;
        this.sentMilestoneReminderRepository = sentMilestoneReminderRepository;
        this.meetingRepository = meetingRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void cleanupChildren(Long mentorshipId) {
        // Tasks (cascade: TaskSubmission + attachments). Reminders need a
        // manual sweep first because they have no FK to tasks.
        List<Task> tasks = taskRepository.findByMentorshipIdOrderByCreatedAtDesc(mentorshipId);
        if (!tasks.isEmpty()) {
            List<Long> taskIds = tasks.stream().map(Task::getId).toList();
            sentTaskReminderRepository.deleteByTaskIdIn(taskIds);
            taskRepository.deleteAllInBatch(tasks);
        }

        // Milestones (cascade: MilestoneActionItem). Same reminder caveat.
        List<Milestone> milestones = milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(mentorshipId);
        if (!milestones.isEmpty()) {
            List<Long> milestoneIds = milestones.stream().map(Milestone::getId).toList();
            sentMilestoneReminderRepository.deleteByMilestoneIdIn(milestoneIds);
            milestoneRepository.deleteAllInBatch(milestones);
        }

        // Meetings (cascade: action items, reschedule requests, reminder state).
        List<Meeting> meetings = meetingRepository.findByMentorshipIdOrderByStartTimeAsc(mentorshipId);
        if (!meetings.isEmpty()) {
            meetingRepository.deleteAllInBatch(meetings);
        }

        // Conversation (cascade: ConversationParticipant, Message). Mentorship-scoped
        // conversations are 0..1 per mentorship; mentor-pair (user↔user) chats live
        // outside the mentorship and are not touched.
        Conversation conversation = conversationRepository.findByMentorshipId(mentorshipId).orElse(null);
        if (conversation != null) {
            conversationRepository.delete(conversation);
        }

        log.info("Mentorship cleanup complete: mentorshipId={}, tasks={}, milestones={}, "
                        + "meetings={}, conversation={}",
                mentorshipId, tasks.size(), milestones.size(), meetings.size(),
                conversation != null);
    }
}
