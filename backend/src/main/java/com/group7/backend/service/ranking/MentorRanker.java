package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;

import java.util.List;

/**
 * Strategy for ranking a mentor against a mentee. Today the only impl is
 * {@link RuleBasedMentorRanker} (the legacy weighted-score algorithm); a
 * future AI-driven ranker will plug in here without any service-layer
 * changes.
 *
 * <p><b>Contract:</b> implementations must NOT make repository calls. Slot
 * lists are pre-fetched in batch by {@code MatchingService} — this is the
 * mechanism that eliminated the per-mentor N+1 in the legacy code path.
 * Implementations that need additional data should accept it through
 * constructor dependencies (for AI: model client, embedding cache) rather
 * than reaching back to repos at scoring time.
 */
@FunctionalInterface
public interface MentorRanker {

    /**
     * Compute a match score for {@code mentor} relative to {@code mentee}.
     * Higher scores rank above lower. Return value is opaque — callers sort
     * but do not interpret the magnitude.
     *
     * @param mentor       candidate mentor (must not be null)
     * @param mentee       requesting mentee (must not be null)
     * @param mentorSlots  the mentor's availability slots, pre-fetched; may be empty
     * @param menteeSlots  the mentee's availability slots, pre-fetched; may be empty
     */
    int score(Mentor mentor,
              Mentee mentee,
              List<AvailabilitySlot> mentorSlots,
              List<MenteeAvailabilitySlot> menteeSlots);
}
