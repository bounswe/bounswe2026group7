package com.group7.backend.service.ranking;

import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;

import java.util.List;

/**
 * Strategy for ranking a mentor against a mentee. Today there are two
 * implementations: {@link RuleBasedMentorRanker} (the legacy weighted-score
 * algorithm, default {@code @Component}) and {@code AdvancedMentorRanker}
 * (embedding-based, registered as {@code @Primary} when
 * {@code app.recommendations.mentor.advanced.enabled=true}). Spring resolves
 * the unique bean at boot; {@link com.group7.backend.service.MatchingService}
 * is unchanged across the swap.
 *
 * <p>The return type converged on {@link ScoreResult} (matching the
 * follow-recommendation side) so factor strings flow through to the response
 * for spec 1.1.2.5 ("explanation for each recommendation"). Implementations
 * that don't surface factors can wrap their int with {@link ScoreResult#of}.
 *
 * <p><b>Contract:</b> implementations must NOT make repository calls. Slot
 * lists are pre-fetched in batch by {@code MatchingService} — this is the
 * mechanism that eliminated the per-mentor N+1 in the legacy code path.
 * Implementations that need additional data should accept it through
 * constructor dependencies (for AI: embedding service, MMR reranker) rather
 * than reaching back to repos at scoring time.
 */
@FunctionalInterface
public interface MentorRanker {

    /**
     * Compute a match score for {@code mentor} relative to {@code mentee}.
     * Higher scores rank above lower. The numeric score is opaque (callers
     * sort but do not interpret the magnitude); the factor list is
     * human-readable codes (e.g. {@code "shared-interest:Java"},
     * {@code "nearby:23km"}) that the UI surfaces as "why is this person
     * recommended?" cards.
     *
     * @param mentor       candidate mentor (must not be null)
     * @param mentee       requesting mentee (must not be null)
     * @param mentorSlots  the mentor's availability slots, pre-fetched; may be empty
     * @param menteeSlots  the mentee's availability slots, pre-fetched; may be empty
     */
    ScoreResult score(Mentor mentor,
                      Mentee mentee,
                      List<AvailabilitySlot> mentorSlots,
                      List<MenteeAvailabilitySlot> menteeSlots);
}
