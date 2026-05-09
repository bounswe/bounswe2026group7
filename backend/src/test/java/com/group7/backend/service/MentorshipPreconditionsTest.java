package com.group7.backend.service;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.exception.GoalRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorshipPreconditionsTest {

    private Mentorship mentorshipWithGoal(String goal) {
        Mentorship m = new Mentorship();
        m.setId(42L);
        m.setSharedGoal(goal);
        return m;
    }

    @Test
    void requireSharedGoalThrowsWhenGoalIsNull() {
        Mentorship mentorship = mentorshipWithGoal(null);

        assertThatThrownBy(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .isInstanceOf(GoalRequiredException.class)
                .hasMessageContaining("must be defined")
                .extracting(ex -> ((GoalRequiredException) ex).getMentorshipId())
                .isEqualTo(42L);
    }

    @Test
    void requireSharedGoalThrowsWhenGoalIsEmpty() {
        Mentorship mentorship = mentorshipWithGoal("");

        assertThatThrownBy(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .isInstanceOf(GoalRequiredException.class);
    }

    @Test
    void requireSharedGoalThrowsWhenGoalIsWhitespace() {
        Mentorship mentorship = mentorshipWithGoal("   ");

        assertThatThrownBy(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .isInstanceOf(GoalRequiredException.class);
    }

    @Test
    void requireSharedGoalThrowsWhenGoalIsTabsAndNewlines() {
        Mentorship mentorship = mentorshipWithGoal("\t\n");

        assertThatThrownBy(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .isInstanceOf(GoalRequiredException.class);
    }

    @Test
    void requireSharedGoalDoesNothingWhenGoalIsSet() {
        Mentorship mentorship = mentorshipWithGoal("Ship the MVP by July");

        assertThatCode(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSharedGoalDoesNothingWhenGoalContainsOnlyOneNonBlankChar() {
        Mentorship mentorship = mentorshipWithGoal("x");

        assertThatCode(() -> MentorshipPreconditions.requireSharedGoal(mentorship))
                .doesNotThrowAnyException();
    }

    @Test
    void goalRequiredExceptionCarriesMessageAndId() {
        GoalRequiredException ex = new GoalRequiredException(7L);

        assertThat(ex.getMessage()).isEqualTo(GoalRequiredException.DEFAULT_MESSAGE);
        assertThat(ex.getMentorshipId()).isEqualTo(7L);
    }
}
