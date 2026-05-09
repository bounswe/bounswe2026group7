package com.group7.backend.service;

import com.group7.backend.dto.request.TaskCreateRequest;
import com.group7.backend.dto.request.TaskReviewRequest;
import com.group7.backend.dto.request.TaskSubmissionRequest;
import com.group7.backend.entity.*;
import com.group7.backend.exception.GoalRequiredException;
import com.group7.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private TaskService taskService;

    private Mentorship activeMentorship;
    private Mentorship endedMentorship;
    private Task pendingTask;
    private Task submittedTask;

    @BeforeEach
    void setUp() {
        Mentor mentor = new Mentor(); mentor.setId(1L); mentor.setFirstName("Mentor");

        Mentee mentee = new Mentee(); mentee.setId(2L); mentee.setFirstName("Mentee");

        activeMentorship = new Mentorship();
        activeMentorship.setId(10L);
        activeMentorship.setMentor(mentor);
        activeMentorship.setMentee(mentee);
        activeMentorship.setStatus(MentorshipStatus.ACTIVE);
        activeMentorship.setSharedGoal("Build a portfolio");

        endedMentorship = new Mentorship();
        endedMentorship.setId(20L);
        endedMentorship.setMentor(mentor);
        endedMentorship.setMentee(mentee);
        endedMentorship.setStatus(MentorshipStatus.COMPLETED);
        endedMentorship.setSharedGoal("Build a portfolio");

        pendingTask = new Task();
        pendingTask.setId(100L);
        pendingTask.setMentorship(activeMentorship);
        pendingTask.setTitle("Pending Task");
        pendingTask.setStatus(TaskStatus.PENDING);
        pendingTask.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        submittedTask = new Task();
        submittedTask.setId(200L);
        submittedTask.setMentorship(activeMentorship);
        submittedTask.setTitle("Submitted Task");
        submittedTask.setStatus(TaskStatus.SUBMITTED);
        submittedTask.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void createTask_Success() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        TaskCreateRequest req = new TaskCreateRequest();
        req.setTitle("New Task");
        
        var response = taskService.createTask(10L, 1L, req);
        
        assertThat(response.getTitle()).isEqualTo("New Task");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.PENDING);
        verify(notificationEventPublisher).publishTaskAssigned(2L, "New Task");
    }

    @Test
    void createTask_MenteeForbidden() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        TaskCreateRequest req = new TaskCreateRequest();

        assertThatThrownBy(() -> taskService.createTask(10L, 2L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the mentor can assign");
    }

    @Test
    void createTask_EndedMentorship_Conflict() {
        when(mentorshipRepository.findById(20L)).thenReturn(Optional.of(endedMentorship));
        TaskCreateRequest req = new TaskCreateRequest();

        assertThatThrownBy(() -> taskService.createTask(20L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Mentorship is not active");
    }

    @Test
    void createTask_PastDueDate_BadRequest() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        TaskCreateRequest req = new TaskCreateRequest();
        req.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        assertThatThrownBy(() -> taskService.createTask(10L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Due date must be in the future");
    }

    @Test
    void createTask_NullGoal_GoalRequired() {
        activeMentorship.setSharedGoal(null);
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        TaskCreateRequest req = new TaskCreateRequest();
        req.setTitle("Anything");

        assertThatThrownBy(() -> taskService.createTask(10L, 1L, req))
                .isInstanceOf(GoalRequiredException.class);
    }

    @Test
    void createTask_BlankGoal_GoalRequired() {
        activeMentorship.setSharedGoal("   ");
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        TaskCreateRequest req = new TaskCreateRequest();
        req.setTitle("Anything");

        assertThatThrownBy(() -> taskService.createTask(10L, 1L, req))
                .isInstanceOf(GoalRequiredException.class)
                .extracting(ex -> ((GoalRequiredException) ex).getMentorshipId())
                .isEqualTo(10L);
    }

    /** Status-inactive must surface before goal-required so the deeper invariant wins. */
    @Test
    void createTask_InactiveAndNullGoal_StatusErrorWins() {
        endedMentorship.setSharedGoal(null);
        when(mentorshipRepository.findById(20L)).thenReturn(Optional.of(endedMentorship));
        TaskCreateRequest req = new TaskCreateRequest();

        assertThatThrownBy(() -> taskService.createTask(20L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Mentorship is not active");
    }

    /** Auth (FORBIDDEN) must surface before goal-required. */
    @Test
    void createTask_NonMentorAndNullGoal_ForbiddenWins() {
        activeMentorship.setSharedGoal(null);
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        TaskCreateRequest req = new TaskCreateRequest();

        assertThatThrownBy(() -> taskService.createTask(10L, 2L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the mentor can assign");
    }

    @Test
    void submitTask_Success() {
        when(taskRepository.findByIdWithMentorship(100L)).thenReturn(Optional.of(pendingTask));
        when(taskSubmissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TaskSubmissionRequest req = new TaskSubmissionRequest();
        req.setSubmissionText("Here is my work");

        var response = taskService.submitTask(100L, 2L, req);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        verify(notificationEventPublisher).publishTaskSubmitted(1L, "Mentee", "Pending Task");
    }

    @Test
    void submitTask_MentorForbidden() {
        when(taskRepository.findByIdWithMentorship(100L)).thenReturn(Optional.of(pendingTask));
        TaskSubmissionRequest req = new TaskSubmissionRequest();

        assertThatThrownBy(() -> taskService.submitTask(100L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the mentee can submit");
    }

    @Test
    void reviewTask_Success_Completed() {
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));
        TaskSubmission submission = new TaskSubmission();
        when(taskSubmissionRepository.findFirstByTaskIdOrderBySubmittedAtDescIdDesc(200L)).thenReturn(Optional.of(submission));

        TaskReviewRequest req = new TaskReviewRequest();
        req.setFeedback("Great job!");
        req.setStatus(TaskStatus.COMPLETED);

        var response = taskService.reviewTask(200L, 1L, req);

        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(submission.getFeedback()).isEqualTo("Great job!");
        assertThat(submission.getReviewedBy().getId()).isEqualTo(1L);
        verify(notificationEventPublisher).publishTaskReviewed(2L, "Submitted Task", false);
    }

    @Test
    void reviewTask_InvalidStatus_BadRequest() {
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));
        TaskReviewRequest req = new TaskReviewRequest();
        req.setStatus(TaskStatus.PENDING); // Invalid for review

        assertThatThrownBy(() -> taskService.reviewTask(200L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Review status must be COMPLETED or REVISION_REQUESTED");
    }

    @Test
    void reviewTask_AlreadyCompleted_Conflict() {
        submittedTask.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));
        TaskReviewRequest req = new TaskReviewRequest();
        req.setStatus(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> taskService.reviewTask(200L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Task is already completed");
    }

    @Test
    void reviewTask_AlreadyReviewed_Conflict() {
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));
        TaskSubmission submission = new TaskSubmission();
        submission.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC)); // Already reviewed
        when(taskSubmissionRepository.findFirstByTaskIdOrderBySubmittedAtDescIdDesc(200L)).thenReturn(Optional.of(submission));

        TaskReviewRequest req = new TaskReviewRequest();
        req.setStatus(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> taskService.reviewTask(200L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("This submission has already been reviewed");
    }

    @Test
    void getTask_FlagsOverdue() {
        pendingTask.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)); // In the past
        when(taskRepository.findByIdWithMentorship(100L)).thenReturn(Optional.of(pendingTask));

        var response = taskService.getTask(100L, 1L);

        assertThat(response.isOverdue()).isTrue();
    }

    @Test
    void getTask_NotOverdueIfSubmitted() {
        submittedTask.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)); // Past
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));

        var response = taskService.getTask(200L, 1L);

        assertThat(response.isOverdue()).isFalse(); // Submitted tasks are never overdue
    }

    @Test
    void deleteTask_Success() {
        when(taskRepository.findByIdWithMentorship(100L)).thenReturn(Optional.of(pendingTask));

        taskService.deleteTask(100L, 1L);

        verify(taskRepository).delete(pendingTask);
    }

    @Test
    void deleteTask_MenteeForbidden() {
        when(taskRepository.findByIdWithMentorship(100L)).thenReturn(Optional.of(pendingTask));

        assertThatThrownBy(() -> taskService.deleteTask(100L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the mentor can delete");
    }

    @Test
    void deleteTask_AlreadySubmitted_Conflict() {
        when(taskRepository.findByIdWithMentorship(200L)).thenReturn(Optional.of(submittedTask));

        assertThatThrownBy(() -> taskService.deleteTask(200L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot delete a task that has already been submitted");
    }

    @Test
    void listTasks_NonParticipantForbidden() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));

        assertThatThrownBy(() -> taskService.listTasks(10L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You are not a participant");
    }

    @Test
    void fetchAttachments_OtherUser_Forbidden() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        
        Attachment att = new Attachment();
        User otherUser = new Mentee();
        otherUser.setId(999L);
        att.setUploader(otherUser);
        
        UUID uuid = UUID.randomUUID();
        when(attachmentRepository.findAllById(List.of(uuid))).thenReturn(List.of(att));
        
        TaskCreateRequest req = new TaskCreateRequest();
        req.setAssignmentAttachmentIds(List.of(uuid));

        assertThatThrownBy(() -> taskService.createTask(10L, 1L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You cannot attach files uploaded by someone else");
    }
}
