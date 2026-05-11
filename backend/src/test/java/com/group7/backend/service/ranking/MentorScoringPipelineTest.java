package com.group7.backend.service.ranking;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pipeline-level contract: score → distance attach → curated 5-slot
 * page → tail in relevance order. Scoring math itself is covered by
 * {@code RuleBasedMentorRankerTest} and per-signal tests.
 */
class MentorScoringPipelineTest {

    private static MentorRecommendationProperties props() {
        return new MentorRecommendationProperties(
                new MentorRecommendationProperties.Advanced(true),
                new MentorRecommendationProperties.Weights(0.5, 0.1, 0.1, 0.1, 0.05, 0.05, 0.1),
                new MentorRecommendationProperties.Signals(true, true, true, true, true, true),
                new MentorRecommendationProperties.Proximity(100, 0.0),
                null);
    }

    private static Mentor mentor(long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        return m;
    }

    private static MentorRanker stubRanker(int score) {
        return (mentor, mentee, ms, es) -> new ScoreResult(score, List.of());
    }

    private static SemanticSimilarityService stubSimilarityNoModel() {
        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenReturn(new float[0]);
        when(sim.cosineSimilarity(any(), any())).thenReturn(0.0);
        return sim;
    }

    /**
     * Population stats that report no centroid. Forces the pipeline onto
     * the pairwise-diversity fallback path, which is the behaviour the
     * majority of these tests were written against.
     */
    private static java.util.Optional<com.group7.backend.service.embedding.MentorPopulationStats> noCentroidStats() {
        var stats = mock(com.group7.backend.service.embedding.MentorPopulationStats.class);
        when(stats.centroid()).thenReturn(java.util.Optional.empty());
        return java.util.Optional.of(stats);
    }

    /** Population stats with an explicit centroid for outlier-strategy tests. */
    private static java.util.Optional<com.group7.backend.service.embedding.MentorPopulationStats> statsWithCentroid(float[] centroid) {
        var stats = mock(com.group7.backend.service.embedding.MentorPopulationStats.class);
        when(stats.centroid()).thenReturn(java.util.Optional.of(centroid));
        return java.util.Optional.of(stats);
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    void emptyInput_returnsEmptyList() {
        var pipeline = new MentorScoringPipeline(
                stubRanker(42), props(), stubSimilarityNoModel(), noCentroidStats());
        assertThat(pipeline.rank(List.of(), new Mentee(), new HashMap<>(), List.of(), null)).isEmpty();
        assertThat(pipeline.rank(null, new Mentee(), new HashMap<>(), List.of(), null)).isEmpty();
    }

    @Test
    void singleMentor_noCoordinates_returnedWithoutSlotOrDiverseFactors() {
        var mentor = mentor(1L);
        var pipeline = new MentorScoringPipeline(
                stubRanker(70), props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(mentor), new Mentee(), new HashMap<>(), List.of(), null);
        assertThat(out).hasSize(1);
        // No coordinates on either side → no slot:nearest. Only one mentor
        // total → no diverse-pick (needs at least one anchor in slots 1-4).
        assertThat(out.get(0).getFactors()).doesNotContain("slot:nearest", "diverse-pick");
    }

    @Test
    void singleMentor_withCoordinates_getsSlotNearestFactor() {
        // The "always fill slot 1" contract: when location data is present
        // even a one-mentor result earns the slot:nearest badge.
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var mentor = mentor(1L); mentor.setLatitude(41.001); mentor.setLongitude(29.001);

        var pipeline = new MentorScoringPipeline(
                stubRanker(70), props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(mentor), mentee, new HashMap<>(), List.of(), null);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getFactors()).contains("slot:nearest");
    }

    // ── Distance attachment + filter ────────────────────────────────────

    @Test
    void distanceAttached_whenBothSidesHaveCoordinates() {
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var mentor = mentor(1L); mentor.setLatitude(41.0); mentor.setLongitude(29.0);

        var pipeline = new MentorScoringPipeline(
                stubRanker(10), props(), stubSimilarityNoModel(), noCentroidStats());

        var out = pipeline.rank(List.of(mentor), mentee, new HashMap<>(), List.of(), null);
        assertThat(out.get(0).getDistanceKm()).isNotNull().isLessThan(0.01);
    }

    @Test
    void distanceNull_whenEitherSideLacksCoordinates() {
        var mentee = new Mentee();
        var mentor = mentor(1L); mentor.setLatitude(41.0); mentor.setLongitude(29.0);

        var pipeline = new MentorScoringPipeline(
                stubRanker(10), props(), stubSimilarityNoModel(), noCentroidStats());

        var out = pipeline.rank(List.of(mentor), mentee, new HashMap<>(), List.of(), null);
        assertThat(out.get(0).getDistanceKm()).isNull();
    }

    @Test
    void maxDistanceKmFilter_dropsFarMentors_butKeepsUnknownDistance() {
        var mentee = new Mentee();
        mentee.setLatitude(41.0); mentee.setLongitude(29.0);

        var near = mentor(1L); near.setLatitude(41.0001); near.setLongitude(29.0);
        var far  = mentor(2L); far.setLatitude(40.7128);  far.setLongitude(-74.0060);  // ~8000 km
        var unknown = mentor(3L);

        var pipeline = new MentorScoringPipeline(
                stubRanker(10), props(), stubSimilarityNoModel(), noCentroidStats());

        var out = pipeline.rank(List.of(near, far, unknown), mentee, new HashMap<>(), List.of(), 1000.0);
        assertThat(out).extracting(MentorMatchResponse::getId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    // ── Slot 1 (nearest) ────────────────────────────────────────────────

    @Test
    void slot1_isClosestMentor_evenIfLowerScored() {
        // The closest mentor (lowest distanceKm) gets slot 1, regardless of
        // raw score. Higher-scored mentors that are farther away still rank
        // in slots 2-4.
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var far   = mentor(1L); far.setLatitude(40.0);    far.setLongitude(29.0);  // ~111 km
        var close = mentor(2L); close.setLatitude(41.001); close.setLongitude(29.001); // ~0.15 km
        var distant = mentor(3L); distant.setLatitude(40.5); distant.setLongitude(29.0); // ~55 km

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90;  // far but high-score
                    case 2 -> 30;  // close but low-score → still wins slot 1
                    case 3 -> 60;
                    default -> 0;
                }, new ArrayList<>());

        var pipeline = new MentorScoringPipeline(
                ranker, props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(far, close, distant), mentee, new HashMap<>(), List.of(), null);

        assertThat(out.get(0).getId()).isEqualTo(2L);  // closest wins slot 1
        assertThat(out.get(0).getFactors()).contains("slot:nearest");
    }

    @Test
    void slot1_respectsMaxDistanceKmFilter() {
        // With maxDistanceKm=10, the 55km candidate is dropped entirely from
        // the candidate set, the 0.15km candidate wins slot 1, and the 111km
        // one was already dropped too.
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var close = mentor(1L); close.setLatitude(41.001); close.setLongitude(29.001);
        var mid   = mentor(2L); mid.setLatitude(40.5);     mid.setLongitude(29.0);
        var far   = mentor(3L); far.setLatitude(40.0);     far.setLongitude(29.0);

        var pipeline = new MentorScoringPipeline(
                stubRanker(50), props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(close, mid, far), mentee, new HashMap<>(), List.of(), 10.0);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getId()).isEqualTo(1L);
        assertThat(out.get(0).getFactors()).contains("slot:nearest");
    }

    @Test
    void slot1_omitted_whenNoMentorHasCoordinates() {
        // No coordinates → no slot:nearest factor, and the page still opens
        // with the top-overall match (not an empty slot 1).
        var mentee = new Mentee();
        var m1 = mentor(1L); var m2 = mentor(2L); var m3 = mentor(3L);

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                mentor.getId().intValue() * 10, new ArrayList<>());

        var pipeline = new MentorScoringPipeline(
                ranker, props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(m1, m2, m3), mentee, new HashMap<>(), List.of(), null);

        for (var r : out) assertThat(r.getFactors()).doesNotContain("slot:nearest");
        // Top overall (id=3, score 30) lands in slot 1; relevance order follows.
        assertThat(out).extracting(MentorMatchResponse::getId).containsExactly(3L, 2L, 1L);
    }

    @Test
    void noLocationData_stillFillsFourTopSlotsPlusOneDiverse() {
        // Without a location winner the relevance cap expands from 3 to 4 so
        // the page rhythm becomes 0+4+1 instead of 0+3+1. The diverse slot
        // still fires when a qualifying off-goal candidate exists.
        var mentee = new Mentee();
        var m1 = mentor(1L); m1.setMentoringGoals("react");
        var m2 = mentor(2L); m2.setMentoringGoals("react");
        var m3 = mentor(3L); m3.setMentoringGoals("react");
        var m4 = mentor(4L); m4.setMentoringGoals("react");
        var m5 = mentor(5L); m5.setMentoringGoals("different");  // off-goal candidate

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 60; case 5 -> 30;
                    default -> 0;
                }, new ArrayList<>());

        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return t.contains("different") ? new float[]{0, 1} : new float[]{1, 0};
        });
        when(sim.cosineSimilarity(any(), any())).thenAnswer(inv -> {
            float[] a = inv.getArgument(0); float[] b = inv.getArgument(1);
            if (a.length == 0 || b.length == 0) return 0.0;
            return (a[0] == b[0] && a[1] == b[1]) ? 1.0 : 0.0;
        });

        var pipeline = new MentorScoringPipeline(
                ranker, props(), sim, noCentroidStats());
        var out = pipeline.rank(List.of(m1, m2, m3, m4, m5), mentee, new HashMap<>(), List.of(), null);

        // Slots 1-4 = top 4 by relevance, no nearest badge anywhere.
        assertThat(out.get(0).getId()).isEqualTo(1L);
        assertThat(out.get(1).getId()).isEqualTo(2L);
        assertThat(out.get(2).getId()).isEqualTo(3L);
        assertThat(out.get(3).getId()).isEqualTo(4L);
        for (int i = 0; i < 4; i++) {
            assertThat(out.get(i).getFactors()).doesNotContain("slot:nearest");
        }
        // Slot 5 = diverse (m5, score 30 ≥ 15, orthogonal embedding).
        assertThat(out.get(4).getId()).isEqualTo(5L);
        assertThat(out.get(4).getFactors()).contains("diverse-pick");
    }

    // ── Slots 2-4 (top-relevance) ───────────────────────────────────────

    @Test
    void slots2to4_areTop3ByScore_excludingSlot1Winner() {
        var mentee = new Mentee(); mentee.setLatitude(41.0); mentee.setLongitude(29.0);
        var near  = mentor(1L); near.setLatitude(41.001); near.setLongitude(29.001); // nearest, score 30
        var top1  = mentor(2L);  // far/unknown but score 90
        var top2  = mentor(3L);  // score 80
        var top3  = mentor(4L);  // score 70
        var rest  = mentor(5L);  // score 60

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 30; case 2 -> 90; case 3 -> 80; case 4 -> 70; case 5 -> 60;
                    default -> 0;
                }, new ArrayList<>());

        var pipeline = new MentorScoringPipeline(
                ranker, props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(near, top1, top2, top3, rest), mentee, new HashMap<>(), List.of(), null);

        assertThat(out.get(0).getId()).isEqualTo(1L);    // slot 1: nearest
        assertThat(out.get(1).getId()).isEqualTo(2L);    // slot 2: top score
        assertThat(out.get(2).getId()).isEqualTo(3L);    // slot 3
        assertThat(out.get(3).getId()).isEqualTo(4L);    // slot 4
    }

    // ── Slot 5 (diverse pick) ───────────────────────────────────────────

    @Test
    void slot5_picksMostDifferentEmbedding_amongAboveMinScore() {
        // No location data, so slots 1-4 are top-4 by relevance and slot 5 is
        // the diverse pick. m1-m3 + m5 share an embedding (react cluster); m4
        // is orthogonal. With cap=4 + no location, m5 (score 50) falls into
        // slot 4 by relevance; slot 5 then picks m4 (score 40, orthogonal)
        // over the empties — embedding distance maximises against slots 1-4.
        var mentee = new Mentee();
        var m1 = mentor(1L); m1.setMentoringGoals("react");
        var m2 = mentor(2L); m2.setMentoringGoals("react");
        var m3 = mentor(3L); m3.setMentoringGoals("react");
        var m4 = mentor(4L); m4.setMentoringGoals("different");  // orthogonal, score 40
        var m5 = mentor(5L); m5.setMentoringGoals("react");      // duplicate, score 50

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 40; case 5 -> 50;
                    default -> 0;
                }, new ArrayList<>());

        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return t.contains("different") ? new float[]{0, 1} : new float[]{1, 0};
        });
        when(sim.cosineSimilarity(any(), any())).thenAnswer(inv -> {
            float[] a = inv.getArgument(0); float[] b = inv.getArgument(1);
            if (a.length == 0 || b.length == 0) return 0.0;
            return (a[0] == b[0] && a[1] == b[1]) ? 1.0 : 0.0;
        });

        var pipeline = new MentorScoringPipeline(
                ranker, props(), sim, noCentroidStats());
        var out = pipeline.rank(List.of(m1, m2, m3, m4, m5), new Mentee(), new HashMap<>(), List.of(), null);

        // Slots 1-4 (cap=4 because no location winner): top 4 by relevance.
        assertThat(out.get(0).getId()).isEqualTo(1L);
        assertThat(out.get(1).getId()).isEqualTo(2L);
        assertThat(out.get(2).getId()).isEqualTo(3L);
        assertThat(out.get(3).getId()).isEqualTo(5L);    // score 50 beats score 40 for slot 4
        // Slot 5 picks the orthogonal mentor (m4) — it maximises embedding
        // distance from the 4 react-clustered mentors in slots 1-4.
        assertThat(out.get(4).getId()).isEqualTo(4L);
        assertThat(out.get(4).getFactors()).contains("diverse-pick");
        // None of slots 1-4 carry the diverse-pick badge.
        for (int i = 0; i < 4; i++) {
            assertThat(out.get(i).getFactors()).doesNotContain("diverse-pick");
        }
    }

    @Test
    void slot5_omitted_whenAllRemainingBelowMinScore() {
        // Three high-score mentors fill slots 2-4, two zero-score mentors are
        // left over — neither qualifies for slot 5 because of the min-score
        // gate (DIVERSE_PICK_MIN_SCORE = 15).
        var mentee = new Mentee();
        var m1 = mentor(1L); var m2 = mentor(2L); var m3 = mentor(3L);
        var lowA = mentor(4L); var lowB = mentor(5L);
        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 5; case 5 -> 0;
                    default -> 0;
                }, new ArrayList<>());

        var pipeline = new MentorScoringPipeline(
                ranker, props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(List.of(m1, m2, m3, lowA, lowB), mentee, new HashMap<>(), List.of(), null);
        assertThat(out).allSatisfy(r -> assertThat(r.getFactors()).doesNotContain("diverse-pick"));
    }

    @Test
    void slot5_skipsEmptyProfileMentors_evenIfTheyAreMaximallyDifferent() {
        // Four react-y mentors fill slots 1-4. An empty-profile mentor with
        // score HIGHER than the off-goal mentor (m6 score 25 > offGoal score
        // 20) would naively win slot 5 by max-distance + score floor, but
        // diversityAwareSimilarity treats the empty vector as max-similar so
        // it scores zero distance — the off-goal mentor wins instead.
        var mentee = new Mentee();
        var m1 = mentor(1L); m1.setMentoringGoals("react");
        var m2 = mentor(2L); m2.setMentoringGoals("react");
        var m3 = mentor(3L); m3.setMentoringGoals("react");
        var m4 = mentor(4L); m4.setMentoringGoals("react");
        var offGoal = mentor(5L); offGoal.setMentoringGoals("different");  // score 20
        var emptyHigh = mentor(6L);                                         // empty + score 25

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 60;
                    case 5 -> 20; case 6 -> 25;
                    default -> 0;
                }, new ArrayList<>());

        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            if (t.isBlank()) return new float[0];
            return t.contains("different") ? new float[]{0, 1} : new float[]{1, 0};
        });
        when(sim.cosineSimilarity(any(), any())).thenAnswer(inv -> {
            float[] a = inv.getArgument(0); float[] b = inv.getArgument(1);
            if (a.length == 0 || b.length == 0) return 0.0;
            return (a[0] == b[0] && a[1] == b[1]) ? 1.0 : 0.0;
        });

        var pipeline = new MentorScoringPipeline(
                ranker, props(), sim, noCentroidStats());
        var out = pipeline.rank(List.of(m1, m2, m3, m4, offGoal, emptyHigh),
                new Mentee(), new HashMap<>(), List.of(), null);

        // Slot 5 must be the off-goal mentor — emptyHigh's higher score and
        // "maximal" zero-vector diversity are both neutralised by the floor.
        var diversePick = out.stream().filter(r -> r.getFactors().contains("diverse-pick")).findFirst();
        assertThat(diversePick).isPresent();
        assertThat(diversePick.get().getId()).isEqualTo(5L);
    }

    // ── Tail ordering ───────────────────────────────────────────────────

    @Test
    void mentorsBeyondSlot5_keepRelevanceOrder() {
        // 7 mentors. After the curated 5 slots, the remaining 2 should be in
        // descending score order.
        var mentee = new Mentee();
        var mentors = new ArrayList<Mentor>();
        for (int i = 1; i <= 7; i++) mentors.add(mentor((long) i));

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                mentor.getId().intValue() * 10, new ArrayList<>());

        var pipeline = new MentorScoringPipeline(
                ranker, props(), stubSimilarityNoModel(), noCentroidStats());
        var out = pipeline.rank(mentors, mentee, new HashMap<>(), List.of(), null);

        assertThat(out).hasSize(7);
        // Last two should be sorted by score descending — but the curated 5
        // already consumed the top 3 + 1 diverse (skipped: no embeddings).
        // Whatever remains, the tail should be score-descending.
        for (int i = out.size() - 2; i < out.size() - 1; i++) {
            assertThat(out.get(i).getMatchScore())
                    .isGreaterThanOrEqualTo(out.get(i + 1).getMatchScore());
        }
    }

    // ── Outlier-strategy slot 5 (population centroid available) ──────────

    @Test
    void slot5_outlierStrategy_picksMentorFarthestFromPopulationCentroid() {
        // Population centroid is at [1, 0] — the "mass" of the mentor pool
        // sits in React/frontend space. Slot 5 should pick the candidate
        // whose embedding is FARTHEST from that centroid, not just
        // "different from the slot 1-4 picks". Even if the slot 1-4 picks
        // happened to be the unusual ones, the global outlier still wins.
        var mentee = new Mentee();
        var m1 = mentor(1L); m1.setMentoringGoals("react");
        var m2 = mentor(2L); m2.setMentoringGoals("react");
        var m3 = mentor(3L); m3.setMentoringGoals("react");
        var m4 = mentor(4L); m4.setMentoringGoals("react");
        // m5 is the rare profile in the global pool — score 25 (≥ 15 floor).
        var m5 = mentor(5L); m5.setMentoringGoals("game-audio");

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 60; case 5 -> 25;
                    default -> 0;
                }, new ArrayList<>());

        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            return t.contains("game-audio") ? new float[]{0, 1} : new float[]{1, 0};
        });
        // Real cosine — empties → 0, identical → 1, orthogonal → 0.
        when(sim.cosineSimilarity(any(), any())).thenAnswer(inv -> {
            float[] a = inv.getArgument(0); float[] b = inv.getArgument(1);
            if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) return 0.0;
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        });

        // Centroid sits at [1, 0] (the React mass) — so the orthogonal
        // mentor m5 has the maximum distance from it (1 - 0 = 1.0).
        var stats = statsWithCentroid(new float[]{1f, 0f});

        var pipeline = new MentorScoringPipeline(ranker, props(), sim, stats);
        var out = pipeline.rank(List.of(m1, m2, m3, m4, m5),
                new Mentee(), new HashMap<>(), List.of(), null);

        // Slots 1-4 absorbed in score order (no location data). m5 wins slot 5
        // because its embedding is the farthest from the centroid.
        var diverse = out.stream().filter(r -> r.getFactors().contains("diverse-pick")).findFirst();
        assertThat(diverse).isPresent();
        assertThat(diverse.get().getId()).isEqualTo(5L);
    }

    @Test
    void slot5_outlierStrategy_skipsEmptyEmbeddingsEvenIfScoreAboveFloor() {
        // An empty-profile mentor with score above the min floor would be
        // technically "different" from the centroid (distance 1.0 by the
        // empty-vector clamp) but the outlier strategy explicitly skips
        // empty vectors — empty means "we don't know", not "unique".
        var mentee = new Mentee();
        var m1 = mentor(1L); m1.setMentoringGoals("react");
        var m2 = mentor(2L); m2.setMentoringGoals("react");
        var m3 = mentor(3L); m3.setMentoringGoals("react");
        var m4 = mentor(4L); m4.setMentoringGoals("react");
        var empty = mentor(5L);
        var realOutlier = mentor(6L); realOutlier.setMentoringGoals("game-audio");

        MentorRanker ranker = (mentor, me, ms, es) -> new ScoreResult(
                switch (mentor.getId().intValue()) {
                    case 1 -> 90; case 2 -> 80; case 3 -> 70; case 4 -> 60;
                    case 5 -> 50;       // empty profile but above min-score floor
                    case 6 -> 20;       // real outlier, lower score
                    default -> 0;
                }, new ArrayList<>());

        var sim = mock(SemanticSimilarityService.class);
        when(sim.embed(anyString())).thenAnswer(inv -> {
            String t = inv.getArgument(0);
            if (t.isBlank()) return new float[0];
            return t.contains("game-audio") ? new float[]{0, 1} : new float[]{1, 0};
        });
        when(sim.cosineSimilarity(any(), any())).thenAnswer(inv -> {
            float[] a = inv.getArgument(0); float[] b = inv.getArgument(1);
            if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) return 0.0;
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        });

        var pipeline = new MentorScoringPipeline(
                ranker, props(), sim, statsWithCentroid(new float[]{1f, 0f}));
        var out = pipeline.rank(List.of(m1, m2, m3, m4, empty, realOutlier),
                new Mentee(), new HashMap<>(), List.of(), null);

        var diverse = out.stream().filter(r -> r.getFactors().contains("diverse-pick")).findFirst();
        assertThat(diverse).isPresent();
        assertThat(diverse.get().getId()).isEqualTo(6L);   // realOutlier, not the empty
    }
}
