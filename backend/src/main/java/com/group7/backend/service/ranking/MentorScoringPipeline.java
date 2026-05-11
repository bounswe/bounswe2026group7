package com.group7.backend.service.ranking;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.embedding.MentorPopulationStats;
import com.group7.backend.service.embedding.MentorProfileText;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.util.HaversineDistance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wraps the score → distance-attach → page-curation chain so
 * {@link com.group7.backend.service.MatchingService} stays a thin
 * orchestrator.
 *
 * <p><b>Curated-page layout.</b> The top of every result page follows a
 * fixed slot structure so the user sees a deliberate mix of axes rather
 * than five visually-similar top-relevance candidates:
 *
 * <ol>
 *   <li><b>Slot 1 — "nearest"</b>: the geographically closest mentor
 *       (lowest {@code distanceKm}), respecting the optional
 *       {@code ?maxDistanceKm} client filter and skipping mentors with
 *       no recorded coordinates. Emits factor {@code slot:nearest}.
 *       Omitted entirely when no eligible mentor exists.</li>
 *   <li><b>Slots 2–4 — "top match"</b>: the next three by
 *       {@code matchScore} (excluding the slot-1 winner). No special
 *       factor — these are the "obvious" recommendations.</li>
 *   <li><b>Slot 5 — "diverse pick"</b>: the mentor in the remainder
 *       whose profile embedding is most distant from the already-
 *       selected slots, subject to a minimum {@code matchScore}.
 *       Emits factor {@code diverse-pick}.</li>
 * </ol>
 *
 * <p>Mentors past slot 5 keep relevance order — pagination is unchanged.
 * Empty embeddings are treated as <em>maximally similar</em> to everything
 * so empty-profile mentors never accidentally win the diverse slot.
 */
@Component
public class MentorScoringPipeline {

    /** Min absolute {@code matchScore} (out of 100) to qualify for slot 5. */
    static final int DIVERSE_PICK_MIN_SCORE = 15;

    /**
     * Top-N window for the diverse-pick search. Considering the entire
     * 200-mentor candidate list would translate every cold-cache request
     * into 200 sequential OpenAI calls (the diverse-pick embedding lookups
     * run synchronously inside the matching HTTP transaction). 20 is large
     * enough to find a meaningful off-goal candidate while keeping the
     * cold-cache cost bounded to ~20 OpenAI calls per request.
     */
    static final int DIVERSE_PICK_CANDIDATE_LIMIT = 20;

    private final MentorRanker mentorRanker;
    private final MentorRecommendationProperties recProps;
    private final SemanticSimilarityService similarity;
    private final MentorPopulationStats populationStats;

    public MentorScoringPipeline(MentorRanker mentorRanker,
                                 MentorRecommendationProperties recProps,
                                 SemanticSimilarityService similarity,
                                 MentorPopulationStats populationStats) {
        this.mentorRanker = mentorRanker;
        this.recProps = recProps;
        this.similarity = similarity;
        this.populationStats = populationStats;
    }

    /**
     * Score every candidate, attach distance, optionally filter by
     * {@code maxDistanceKm}, then curate the top-5 slot structure and
     * append the remainder in relevance order.
     *
     * @param mentors        the candidate mentors (already capacity-filtered + windowed)
     * @param mentee         the requesting mentee
     * @param slotsByMentor  pre-fetched mentor availability slots by mentor id
     * @param menteeSlots    pre-fetched mentee availability slots
     * @param maxDistanceKm  optional ceiling; mentors farther than this are dropped
     *                       before curation. When null, no distance filter is applied
     *                       (mentors without coordinates always pass through).
     */
    public List<MentorMatchResponse> rank(List<Mentor> mentors,
                                          Mentee mentee,
                                          Map<Long, List<AvailabilitySlot>> slotsByMentor,
                                          List<MenteeAvailabilitySlot> menteeSlots,
                                          Double maxDistanceKm) {
        if (mentors == null || mentors.isEmpty()) {
            return List.of();
        }

        Map<Long, Mentor> mentorById = new HashMap<>(mentors.size());
        for (Mentor m : mentors) mentorById.put(m.getId(), m);

        // 1. Score every candidate + sort by score (descending).
        List<MentorMatchResponse> scored = new ArrayList<>(mentors.size());
        for (Mentor m : mentors) {
            ScoreResult result = mentorRanker.score(
                    m, mentee,
                    slotsByMentor.getOrDefault(m.getId(), List.of()),
                    menteeSlots);
            MentorMatchResponse response = MentorMatchResponse.from(m, result);
            Double distanceKm = computeDistance(m, mentee);
            response.setDistanceKm(distanceKm);
            // 2. Distance filter: skip mentors with known-too-far coordinates.
            //    Mentors with null distance (one or both sides lack lat/lon)
            //    always pass — withholding them would penalise users who
            //    haven't opted into location yet.
            if (maxDistanceKm != null && distanceKm != null && distanceKm > maxDistanceKm) {
                continue;
            }
            scored.add(response);
        }
        scored.sort(Comparator.comparingInt(MentorMatchResponse::getMatchScore).reversed());

        // 3. Apply the curated-page structure to the head of the list.
        //    `sortedByScore` has already had the optional maxDistanceKm filter
        //    applied above, so curate() doesn't need to know about it.
        return curate(scored, mentorById);
    }

    /**
     * Compose the curated page-zero slots (nearest → top-match × 3 → diverse)
     * and append remaining mentors in relevance order so pagination behaves
     * naturally. When the candidate pool is smaller than the slot template,
     * fill what we can and stop — no padding, no empty slots in the response.
     */
    private List<MentorMatchResponse> curate(List<MentorMatchResponse> sortedByScore,
                                             Map<Long, Mentor> mentorById) {
        if (sortedByScore.isEmpty()) return sortedByScore;

        List<MentorMatchResponse> curated = new ArrayList<>(sortedByScore.size());
        Set<Long> used = new HashSet<>();

        // Slot 1 — nearest mentor when location data exists, otherwise just
        // the top-overall match. We always fill slot 1 with the strongest
        // candidate available so the page never opens with an "empty slot"
        // feel — the only difference is whether it earns the slot:nearest
        // factor (location data present) or not (none of the candidates
        // have coordinates).
        Optional<MentorMatchResponse> nearest = pickNearest(sortedByScore);
        if (nearest.isPresent()) {
            var r = nearest.get();
            attachFactor(r, "slot:nearest");
            curated.add(r);
            used.add(r.getId());
        }

        // Slots 2-4 — top relevance, excluding anyone already placed. When
        // slot 1 was filled by location, cap the relevance run at 3; when it
        // wasn't, expand the cap to 4 so we still surface 4 score-based picks
        // before the diverse wildcard (preserves the 5-slot rhythm of
        // 1 + 3 + 1 → 4 + 1 when location data is missing).
        int relevanceCap = curated.isEmpty() ? 4 : 3;
        int placedRelevance = 0;
        for (MentorMatchResponse r : sortedByScore) {
            if (placedRelevance >= relevanceCap) break;
            if (used.contains(r.getId())) continue;
            curated.add(r);
            used.add(r.getId());
            placedRelevance++;
        }

        // Slot 5 — most-different mentor by embedding distance from slots 1-4.
        // Score floor (DIVERSE_PICK_MIN_SCORE) guards against tagging zero-
        // score / empty-profile mentors; they'd produce zero-vector embeddings
        // which look "maximally diverse" but carry no real recommendation.
        Optional<MentorMatchResponse> diverse = pickDiverse(sortedByScore, used, curated, mentorById);
        diverse.ifPresent(r -> {
            attachFactor(r, "diverse-pick");
            curated.add(r);
            used.add(r.getId());
        });

        // Tail — remaining mentors in plain relevance order for page 2+.
        for (MentorMatchResponse r : sortedByScore) {
            if (used.contains(r.getId())) continue;
            curated.add(r);
        }
        return curated;
    }

    /**
     * The closest mentor by {@code distanceKm}. Input is already
     * {@code maxDistanceKm}-filtered upstream in {@link #rank}, so we
     * only need the null-coordinate guard here.
     */
    private static Optional<MentorMatchResponse> pickNearest(List<MentorMatchResponse> sortedByScore) {
        return sortedByScore.stream()
                .filter(r -> r.getDistanceKm() != null)
                .min(Comparator.comparingDouble(MentorMatchResponse::getDistanceKm));
    }

    /**
     * Slot-5 picker. Prefers an <em>outlier</em>: the candidate whose
     * profile embedding is farthest from the global mentor-pool
     * centroid (the most genuinely unique profile in the system, not
     * just unlike the top picks on this page). Falls back to the
     * pairwise "most different from slots 1-4" rule when the
     * population centroid isn't available yet (cold start or all
     * embeddings unavailable).
     *
     * <p>Common rules across both strategies:
     * <ul>
     *   <li>Candidates limited to the first {@link #DIVERSE_PICK_CANDIDATE_LIMIT}
     *       un-placed mentors so cold-cache embedding cost stays bounded.</li>
     *   <li>Minimum {@link #DIVERSE_PICK_MIN_SCORE} so we don't badge a
     *       zero-score wildcard.</li>
     *   <li>Empty-vector candidates can't win — under the outlier strategy
     *       we skip them outright; under pairwise they're clamped to
     *       similarity = 1.0 (least diverse).</li>
     * </ul>
     */
    private Optional<MentorMatchResponse> pickDiverse(List<MentorMatchResponse> sortedByScore,
                                                      Set<Long> used,
                                                      List<MentorMatchResponse> alreadyPicked,
                                                      Map<Long, Mentor> mentorById) {
        Optional<float[]> centroid = populationStats.centroid();
        if (centroid.isPresent()) {
            return pickDiverseOutlier(sortedByScore, used, mentorById, centroid.get());
        }
        // Fallback: pairwise distance from slot 1-4 picks. Used at cold
        // start before the centroid populates, or when no embeddings are
        // available anywhere in the system.
        return pickDiversePairwise(sortedByScore, used, alreadyPicked, mentorById);
    }

    /**
     * Outlier strategy: distance from the global mentor centroid. The
     * candidate with the smallest cosine similarity to the centroid is
     * the most genuinely rare profile in the pool — a discovery the
     * user wouldn't reach through a normal sort.
     */
    private Optional<MentorMatchResponse> pickDiverseOutlier(List<MentorMatchResponse> sortedByScore,
                                                              Set<Long> used,
                                                              Map<Long, Mentor> mentorById,
                                                              float[] centroid) {
        MentorMatchResponse best = null;
        double bestDistance = -1.0;
        int considered = 0;
        for (MentorMatchResponse r : sortedByScore) {
            if (used.contains(r.getId())) continue;
            if (r.getMatchScore() < DIVERSE_PICK_MIN_SCORE) continue;
            if (considered >= DIVERSE_PICK_CANDIDATE_LIMIT) break;
            considered++;

            float[] vec = embeddingOf(r, mentorById);
            if (vec.length == 0) continue;     // empty profiles aren't "unique", they're "unknown"
            if (vec.length != centroid.length) continue;  // model mismatch between cache entries
            double sim = similarity.cosineSimilarity(vec, centroid);
            double distance = 1.0 - sim;
            if (distance > bestDistance) {
                bestDistance = distance;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Pairwise-distance fallback. Picks the candidate most different
     * from the slot 1-4 embeddings. Used only when the population
     * centroid isn't populated yet.
     */
    private Optional<MentorMatchResponse> pickDiversePairwise(List<MentorMatchResponse> sortedByScore,
                                                               Set<Long> used,
                                                               List<MentorMatchResponse> alreadyPicked,
                                                               Map<Long, Mentor> mentorById) {
        if (alreadyPicked.isEmpty()) {
            return Optional.empty();
        }
        List<float[]> pickedEmbeddings = new ArrayList<>(alreadyPicked.size());
        for (MentorMatchResponse picked : alreadyPicked) {
            pickedEmbeddings.add(embeddingOf(picked, mentorById));
        }

        MentorMatchResponse best = null;
        double bestDistance = -1.0;
        int considered = 0;
        for (MentorMatchResponse r : sortedByScore) {
            if (used.contains(r.getId())) continue;
            if (r.getMatchScore() < DIVERSE_PICK_MIN_SCORE) continue;
            if (considered >= DIVERSE_PICK_CANDIDATE_LIMIT) break;
            considered++;

            float[] vec = embeddingOf(r, mentorById);
            double maxSim = 0.0;
            for (float[] pickedVec : pickedEmbeddings) {
                double sim = diversityAwareSimilarity(vec, pickedVec);
                if (sim > maxSim) maxSim = sim;
            }
            double distance = 1.0 - maxSim;
            if (distance > bestDistance) {
                bestDistance = distance;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    private float[] embeddingOf(MentorMatchResponse r, Map<Long, Mentor> mentorById) {
        Mentor m = mentorById.get(r.getId());
        if (m == null) return new float[0];
        return similarity.embed(MentorProfileText.forMentor(m));
    }

    /**
     * Cosine wrapper that treats empty vectors as maximally similar (1.0).
     * Without this an empty embedding is trivially diverse from everything
     * and games the slot-5 contest.
     */
    private double diversityAwareSimilarity(float[] a, float[] b) {
        if (a == null || a.length == 0 || b == null || b.length == 0) {
            return 1.0;
        }
        return similarity.cosineSimilarity(a, b);
    }

    private static void attachFactor(MentorMatchResponse r, String factor) {
        List<String> with = new ArrayList<>(r.getFactors());
        if (!with.contains(factor)) with.add(factor);
        r.setFactors(List.copyOf(with));
    }

    private static Double computeDistance(Mentor mentor, Mentee mentee) {
        Double mLat = mentor.getLatitude(), mLon = mentor.getLongitude();
        Double eLat = mentee.getLatitude(), eLon = mentee.getLongitude();
        if (mLat == null || mLon == null || eLat == null || eLon == null) {
            return null;
        }
        try {
            return HaversineDistance.kilometres(mLat, mLon, eLat, eLon);
        } catch (IllegalArgumentException invalid) {
            // Defensive: out-of-range data slipped past the DB CHECK.
            return null;
        }
    }
}
