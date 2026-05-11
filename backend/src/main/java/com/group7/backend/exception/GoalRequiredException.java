package com.group7.backend.exception;

/**
 * Thrown when an action requires a mentorship to have a defined shared goal
 * but the mentorship's {@code sharedGoal} is null or blank.
 *
 * <p>Maps to HTTP 409 with a structured body containing
 * {@code code = "GOAL_REQUIRED"} and the {@code mentorshipId}.
 */
public class GoalRequiredException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Mentorship shared goal must be defined before this action";

    private final Long mentorshipId;

    public GoalRequiredException(Long mentorshipId) {
        super(DEFAULT_MESSAGE);
        this.mentorshipId = mentorshipId;
    }

    public Long getMentorshipId() {
        return mentorshipId;
    }
}
