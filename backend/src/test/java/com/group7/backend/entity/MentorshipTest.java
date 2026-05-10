package com.group7.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentorshipTest {

    private Mentorship withGoal(String goal) {
        Mentorship m = new Mentorship();
        m.setSharedGoal(goal);
        return m;
    }

    @Test
    void hasSharedGoalIsFalseWhenNull() {
        assertThat(withGoal(null).hasSharedGoal()).isFalse();
    }

    @Test
    void hasSharedGoalIsFalseWhenEmpty() {
        assertThat(withGoal("").hasSharedGoal()).isFalse();
    }

    @Test
    void hasSharedGoalIsFalseWhenWhitespaceOnly() {
        assertThat(withGoal("   ").hasSharedGoal()).isFalse();
    }

    @Test
    void hasSharedGoalIsTrueWhenSingleChar() {
        assertThat(withGoal("x").hasSharedGoal()).isTrue();
    }

    @Test
    void hasSharedGoalIsTrueForTypicalSentence() {
        assertThat(withGoal("Ship the MVP by July").hasSharedGoal()).isTrue();
    }
}
