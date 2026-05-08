package com.group7.backend.controller;

import com.group7.backend.dto.request.TaskCreateRequest;
import com.group7.backend.dto.request.TaskReviewRequest;
import com.group7.backend.dto.request.TaskSubmissionRequest;
import com.group7.backend.dto.response.TaskDetailResponse;
import com.group7.backend.dto.response.TaskSummaryResponse;
import com.group7.backend.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Tasks", description = "Mentorship task management")
@PreAuthorize("isAuthenticated()")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/mentorships/{id}/tasks")
    @Operation(summary = "Assign a task", description = "Mentor assigns a new task to their mentee.")
    public ResponseEntity<TaskDetailResponse> createTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskCreateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        TaskDetailResponse response = taskService.createTask(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mentorships/{id}/tasks")
    @Operation(summary = "List tasks", description = "List all tasks for a given mentorship.")
    public ResponseEntity<List<TaskSummaryResponse>> listTasks(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(taskService.listTasks(id, userId));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get task details", description = "Get full task details including submission history.")
    public ResponseEntity<TaskDetailResponse> getTask(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(taskService.getTask(id, userId));
    }

    @PostMapping("/tasks/{id}/submission")
    @Operation(summary = "Submit a task", description = "Mentee submits work for a task.")
    public ResponseEntity<TaskDetailResponse> submitTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskSubmissionRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        TaskDetailResponse response = taskService.submitTask(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/tasks/{id}/feedback")
    @Operation(summary = "Review a task", description = "Mentor provides feedback and updates the status of the latest submission.")
    public ResponseEntity<TaskDetailResponse> reviewTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskReviewRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(taskService.reviewTask(id, userId, request));
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "Delete task", description = "Mentor deletes an unsubmitted task.")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        taskService.deleteTask(id, userId);
        return ResponseEntity.noContent().build();
    }
}
