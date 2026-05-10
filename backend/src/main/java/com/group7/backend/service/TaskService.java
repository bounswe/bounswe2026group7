package com.group7.backend.service;

import com.group7.backend.dto.request.TaskCreateRequest;
import com.group7.backend.dto.request.TaskReviewRequest;
import com.group7.backend.dto.request.TaskSubmissionRequest;
import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.TaskDetailResponse;
import com.group7.backend.dto.response.TaskSubmissionResponse;
import com.group7.backend.dto.response.TaskSummaryResponse;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.entity.TaskSubmission;
import com.group7.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final MentorshipRepository mentorshipRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public TaskService(TaskRepository taskRepository,
                       TaskSubmissionRepository taskSubmissionRepository,
                       MentorshipRepository mentorshipRepository,
                       AttachmentRepository attachmentRepository,
                       NotificationEventPublisher notificationEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.attachmentRepository = attachmentRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public TaskDetailResponse createTask(Long mentorshipId, Long mentorId, TaskCreateRequest request) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mentorship not found"));

        if (!mentorship.getMentor().getId().equals(mentorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the mentor can assign tasks");
        }
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentorship is not active");
        }
        MentorshipPreconditions.requireSharedGoal(mentorship);

        if (request.getDueDate() != null && request.getDueDate().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date must be in the future");
        }

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        task.setStatus(TaskStatus.PENDING);

        if (request.getAssignmentAttachmentIds() != null && !request.getAssignmentAttachmentIds().isEmpty()) {
            List<Attachment> attachments = fetchAndValidateAttachments(request.getAssignmentAttachmentIds(), mentorId);
            task.getAssignmentAttachments().addAll(attachments);
        }

        task = taskRepository.save(task);

        notificationEventPublisher.publishTaskAssigned(mentorship.getMentee().getId(), task.getTitle());

        return mapToDetailResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listTasks(Long mentorshipId, Long userId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mentorship not found"));

        validateParticipant(mentorship, userId);

        return taskRepository.findByMentorshipIdOrderByCreatedAtDesc(mentorshipId).stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(Long taskId, Long userId) {
        Task task = taskRepository.findByIdWithMentorship(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        validateParticipant(task.getMentorship(), userId);

        return mapToDetailResponse(task);
    }

    public TaskDetailResponse submitTask(Long taskId, Long menteeId, TaskSubmissionRequest request) {
        Task task = taskRepository.findByIdWithMentorship(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!task.getMentorship().getMentee().getId().equals(menteeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the mentee can submit work");
        }
        if (task.getMentorship().getStatus() != MentorshipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentorship is not active");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot submit to a completed task");
        }

        TaskSubmission submission = new TaskSubmission();
        submission.setTask(task);
        submission.setSubmissionText(request.getSubmissionText());

        if (request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
            List<Attachment> attachments = fetchAndValidateAttachments(request.getAttachmentIds(), menteeId);
            submission.getAttachments().addAll(attachments);
        }

        taskSubmissionRepository.save(submission);

        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        notificationEventPublisher.publishTaskSubmitted(
                task.getMentorship().getMentor().getId(),
                task.getMentorship().getMentee().getFirstName(),
                task.getTitle()
        );

        return mapToDetailResponse(task);
    }

    public TaskDetailResponse reviewTask(Long taskId, Long mentorId, TaskReviewRequest request) {
        Task task = taskRepository.findByIdWithMentorship(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!task.getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the mentor can review submissions");
        }
        if (task.getMentorship().getStatus() != MentorshipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentorship is not active");
        }
        if (request.getStatus() != TaskStatus.COMPLETED && request.getStatus() != TaskStatus.REVISION_REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review status must be COMPLETED or REVISION_REQUESTED");
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is already completed and cannot be reviewed again");
        }
        if (task.getStatus() == TaskStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task has not been submitted yet");
        }

        TaskSubmission latestSubmission = taskSubmissionRepository.findFirstByTaskIdOrderBySubmittedAtDescIdDesc(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No submission found for this task"));
                
        if (latestSubmission.getReviewedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This submission has already been reviewed");
        }

        latestSubmission.setFeedback(request.getFeedback());
        latestSubmission.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        latestSubmission.setReviewedBy(task.getMentorship().getMentor());
        taskSubmissionRepository.save(latestSubmission);

        task.setStatus(request.getStatus());
        taskRepository.save(task);

        notificationEventPublisher.publishTaskReviewed(
                task.getMentorship().getMentee().getId(),
                task.getTitle(),
                request.getStatus() == TaskStatus.REVISION_REQUESTED
        );

        return mapToDetailResponse(task);
    }

    public void deleteTask(Long taskId, Long mentorId) {
        Task task = taskRepository.findByIdWithMentorship(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!task.getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the mentor can delete tasks");
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a task that has already been submitted");
        }
        if (task.getMentorship().getStatus() != MentorshipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mentorship is not active");
        }

        taskRepository.delete(task);
    }

    // --- Private Helpers ---

    private void validateParticipant(Mentorship mentorship, Long userId) {
        boolean isMentor = mentorship.getMentor().getId().equals(userId);
        boolean isMentee = mentorship.getMentee().getId().equals(userId);
        if (!isMentor && !isMentee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this mentorship");
        }
    }

    private List<Attachment> fetchAndValidateAttachments(List<UUID> attachmentIds, Long userId) {
        List<Attachment> attachments = attachmentRepository.findAllById(attachmentIds);
        if (attachments.size() != attachmentIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more attachments were not found");
        }
        for (Attachment att : attachments) {
            if (!att.getUploader().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot attach files uploaded by someone else");
            }
        }
        return attachments;
    }

    private TaskSummaryResponse mapToSummaryResponse(Task task) {
        TaskSummaryResponse dto = new TaskSummaryResponse();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDueDate(task.getDueDate());
        dto.setStatus(task.getStatus());
        dto.setCreatedAt(task.getCreatedAt());
        
        boolean overdue = (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.REVISION_REQUESTED) 
                && task.getDueDate() != null 
                && task.getDueDate().isBefore(OffsetDateTime.now(ZoneOffset.UTC));
        dto.setOverdue(overdue);
        return dto;
    }

    private TaskDetailResponse mapToDetailResponse(Task task) {
        TaskDetailResponse dto = new TaskDetailResponse();
        dto.setId(task.getId());
        dto.setMentorshipId(task.getMentorship().getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setDueDate(task.getDueDate());
        dto.setStatus(task.getStatus());
        dto.setCreatedAt(task.getCreatedAt());

        boolean overdue = (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.REVISION_REQUESTED)
                && task.getDueDate() != null
                && task.getDueDate().isBefore(OffsetDateTime.now(ZoneOffset.UTC));
        dto.setOverdue(overdue);

        dto.setAssignmentAttachments(task.getAssignmentAttachments().stream()
                .map(this::mapToAttachmentSummary)
                .collect(Collectors.toList()));

        List<TaskSubmissionResponse> subDtos = taskSubmissionRepository.findByTaskIdOrderBySubmittedAtDescIdDesc(task.getId())
                .stream()
                .map(sub -> {
                    TaskSubmissionResponse sDto = new TaskSubmissionResponse();
                    sDto.setId(sub.getId());
                    sDto.setSubmissionText(sub.getSubmissionText());
                    sDto.setFeedback(sub.getFeedback());
                    sDto.setSubmittedAt(sub.getSubmittedAt());
                    sDto.setReviewedAt(sub.getReviewedAt());
                    sDto.setAttachments(sub.getAttachments().stream()
                            .map(this::mapToAttachmentSummary)
                            .collect(Collectors.toList()));
                    return sDto;
                })
                .collect(Collectors.toList());
        dto.setSubmissions(subDtos);

        return dto;
    }

    private AttachmentSummary mapToAttachmentSummary(Attachment att) {
        // Constructing a relative download URL matching the attachment endpoint
        String downloadUrl = "/api/uploads/attachments/" + att.getId();
        return AttachmentSummary.of(att, downloadUrl);
    }
}
