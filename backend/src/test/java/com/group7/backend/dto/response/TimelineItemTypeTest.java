package com.group7.backend.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the priority values of {@link TimelineItemType}. The timeline service uses
 * priority as the second-level sort key; if a future commit reorders the enum
 * declaration this test fails fast instead of silently shifting the rendered order.
 */
class TimelineItemTypeTest {

    @Test
    void milestoneHasPriorityZero() {
        assertThat(TimelineItemType.MILESTONE.priority()).isEqualTo(0);
    }

    @Test
    void meetingHasPriorityOne() {
        assertThat(TimelineItemType.MEETING.priority()).isEqualTo(1);
    }

    @Test
    void taskHasPriorityTwo() {
        assertThat(TimelineItemType.TASK.priority()).isEqualTo(2);
    }
}
