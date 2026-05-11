package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.MenteeAvailabilitySlot;

import java.util.List;

/**
 * Per-call data threaded through every {@link MentorScoringSignal} so
 * signals don't reach back to repositories at scoring time. Slot lists
 * are pre-fetched in batch by the candidate loader — the same mechanism
 * that eliminated the per-mentor N+1 in the legacy ranker.
 */
public record ScoringContext(
        List<AvailabilitySlot> mentorSlots,
        List<MenteeAvailabilitySlot> menteeSlots
) {

    public ScoringContext {
        mentorSlots = (mentorSlots == null) ? List.of() : List.copyOf(mentorSlots);
        menteeSlots = (menteeSlots == null) ? List.of() : List.copyOf(menteeSlots);
    }
}
