package com.group7.backend.dto.response;

/**
 * Discriminator for {@link TimelineItem}. The {@link #priority()} accessor returns
 * the value used as the second-level sort key in the timeline (after {@code occursAt});
 * lower priority means earlier in the rendered list when timestamps tie.
 *
 * <p>Priority is an explicit field, not {@link Enum#ordinal()}, so reordering the enum
 * declaration cannot silently change sort behaviour.
 */
public enum TimelineItemType {
    MILESTONE(0),
    MEETING(1),
    TASK(2);

    private final int priority;

    TimelineItemType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
