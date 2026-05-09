package com.group7.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalRequiredHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returns409WithStructuredBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mentorships/42/tasks");
        GoalRequiredException ex = new GoalRequiredException(42L);

        ResponseEntity<Map<String, Object>> response = handler.handleGoalRequired(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "error", "Conflict",
                "code", "GOAL_REQUIRED",
                "message", GoalRequiredException.DEFAULT_MESSAGE,
                "mentorshipId", 42L
        ));
    }

    @Test
    void mentorshipIdIsLongNotString() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mentorships/7/meetings");
        GoalRequiredException ex = new GoalRequiredException(7L);

        ResponseEntity<Map<String, Object>> response = handler.handleGoalRequired(ex, request);

        assertThat(response.getBody().get("mentorshipId")).isInstanceOf(Long.class).isEqualTo(7L);
    }

    @Test
    void codeIsStableMachineReadable() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mentorships/1/milestones");
        GoalRequiredException ex = new GoalRequiredException(1L);

        ResponseEntity<Map<String, Object>> response = handler.handleGoalRequired(ex, request);

        assertThat(response.getBody().get("code")).isEqualTo("GOAL_REQUIRED");
    }
}
