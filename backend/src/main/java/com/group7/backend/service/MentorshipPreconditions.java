package com.group7.backend.service;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.exception.GoalRequiredException;

/**
 * Mentorship-level invariants enforced before downstream domain writes
 * (tasks, meetings, milestones). Centralised here so the rule lives in
 * exactly one place and the exception cannot drift across callers.
 */
public final class MentorshipPreconditions {

    private MentorshipPreconditions() {
    }

    /**
     * Ensures the mentorship has a defined shared goal.
     *
     * @throws GoalRequiredException carrying the mentorship id, mapped by
     *         {@code GlobalExceptionHandler} to HTTP 409 with
     *         {@code code = "GOAL_REQUIRED"}.
     */
    public static void requireSharedGoal(Mentorship mentorship) {
        if (!mentorship.hasSharedGoal()) {
            throw new GoalRequiredException(mentorship.getId());
        }
    }
}
