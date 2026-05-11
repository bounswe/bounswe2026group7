package com.group7.backend.service.embedding;

import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.MentorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Population-level statistics about the mentor pool — currently the
 * centroid of all mentor profile embeddings. Used by
 * {@link com.group7.backend.service.ranking.MentorScoringPipeline} to
 * pick the slot-5 "unique" mentor: the candidate whose embedding is
 * <em>farthest from the population centroid</em>, i.e. the most
 * globally rare profile, rather than just "most different from the
 * other slot picks".
 *
 * <p>This is what makes slot 5 a genuine discovery: in a pool where
 * 180 of 200 mentors are software-engineer types, the population
 * centroid sits in software-engineer-space, and the game-audio composer
 * who happens to mentor on the side surfaces as slot 5 — not because
 * they're different from <em>your</em> top picks, but because they're
 * different from <em>everyone</em>.
 *
 * <p><b>Refresh.</b> Centroid is computed eagerly on application ready
 * and daily at 03:00 UTC. Mentor population changes slowly enough that
 * once-a-day staleness is fine. The cost is ~200 OpenAI embedding calls
 * per refresh, almost entirely cache-hits after warm-up.
 *
 * <p><b>Cold start.</b> {@link #centroid()} returns {@link Optional#empty()}
 * until the first refresh completes. The pipeline falls back to its
 * pairwise diversity rule in that window.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.mentor.advanced.enabled", havingValue = "true")
public class MentorPopulationStats {

    private static final Logger log = LoggerFactory.getLogger(MentorPopulationStats.class);

    private final MentorRepository mentorRepository;
    private final SemanticSimilarityService similarity;
    private final AtomicReference<float[]> centroid = new AtomicReference<>();

    public MentorPopulationStats(MentorRepository mentorRepository,
                                 SemanticSimilarityService similarity) {
        this.mentorRepository = mentorRepository;
        this.similarity = similarity;
    }

    /** Eagerly populate on app start so the first matching request sees a centroid. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    /** Daily refresh at 03:00 UTC — picks up new mentors and profile edits. */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void scheduledRefresh() {
        refresh();
    }

    /**
     * Recompute the centroid from the current mentor population. Pulls
     * each mentor's embedding from {@link SemanticSimilarityService}'s
     * cache (warm after a single warm-up pass), then averages the
     * non-empty vectors. Atomically swaps the stored centroid; never
     * throws — keeps the previous centroid on failure rather than
     * leaving the slot-5 picker without an anchor.
     */
    @Transactional(readOnly = true)
    public void refresh() {
        try {
            List<Mentor> all = mentorRepository.findAll();
            if (all.isEmpty()) {
                log.debug("MentorPopulationStats.refresh skipped: empty mentor pool");
                return;
            }

            float[] sum = null;
            int contributing = 0;
            for (Mentor m : all) {
                float[] vec = similarity.embed(MentorProfileText.forMentor(m));
                if (vec.length == 0) continue;
                if (sum == null) sum = new float[vec.length];
                if (sum.length != vec.length) continue;          // mixed-model cache entries — skip
                for (int i = 0; i < vec.length; i++) sum[i] += vec[i];
                contributing++;
            }
            if (contributing == 0 || sum == null) {
                log.debug("MentorPopulationStats.refresh: no usable embeddings, keeping previous centroid");
                return;
            }
            float[] mean = new float[sum.length];
            for (int i = 0; i < sum.length; i++) mean[i] = sum[i] / contributing;
            centroid.set(mean);
            log.info("MentorPopulationStats centroid refreshed from {} mentor embeddings", contributing);
        } catch (RuntimeException ex) {
            // Defensive: a single bad refresh shouldn't crash the scheduler.
            // Keep the previous centroid; the next refresh will retry.
            log.warn("MentorPopulationStats.refresh failed: {}", ex.getClass().getSimpleName());
        }
    }

    /** The current centroid, or empty when not yet populated. Thread-safe. */
    public Optional<float[]> centroid() {
        float[] c = centroid.get();
        return Optional.ofNullable(c);
    }

    /** Visible for tests — force the centroid to a specific value. */
    void setCentroidForTest(float[] vec) {
        centroid.set(vec);
    }
}
