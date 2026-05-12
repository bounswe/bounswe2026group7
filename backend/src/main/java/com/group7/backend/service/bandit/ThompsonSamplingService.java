package com.group7.backend.service.bandit;

import com.group7.backend.entity.ViewerHashtagEngagement;
import com.group7.backend.repository.ViewerHashtagEngagementRepository;
import com.group7.backend.service.HashtagNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thompson-sampling bandit state and draws for the For-You hashtag
 * exploration slot.
 *
 * <p><b>Draw.</b> {@link #samplePosterior(Long, String)} returns a
 * {@code Beta(α, β)} draw — when no posterior row exists, returns a
 * {@code Beta(1, 1)} draw (uniform on {@code [0, 1]}) without inserting.
 * Insertion happens only on actual engagement via
 * {@link #recordEngagement(Long, java.util.Collection)}.
 *
 * <p><b>Sampler.</b> Beta variates are produced via two Gamma draws —
 * {@code Beta(α, β) = X / (X + Y)} where {@code X ~ Gamma(α, 1)} and
 * {@code Y ~ Gamma(β, 1)}. Gamma draws use the Marsaglia-Tsang algorithm
 * (one normal + one uniform per accept), which is fast and exact for
 * {@code α ≥ 1}. The CHECK constraint on the table anchors
 * {@code α, β ≥ 1.0} so we never hit the {@code α < 1} branch that needs
 * a boost trick.
 *
 * <p><b>v1 limitation.</b> {@link #recordImpressionDecay(Long,
 * Collection)} is the β-update path; unwired in v1 (engagement only
 * increments α). Without β updates the posterior concentrates around
 * 1.0 as α grows; once α exceeds ~50 the bandit effectively becomes a
 * deterministic argmax-by-α. The impression-tracking follow-up PR is
 * the gate for wiring β.
 *
 * <p><b>Concurrency.</b> {@link #recordEngagement} delegates to a
 * native UPSERT, atomic per row even under contention. {@code
 * samplePosterior} is read-only and uses a thread-local PRNG so
 * parallel page renders for the same viewer don't contend.
 */
@Service
public class ThompsonSamplingService {

    private static final Logger log = LoggerFactory.getLogger(ThompsonSamplingService.class);

    private final ViewerHashtagEngagementRepository repository;
    private final HashtagNormalizer hashtagNormalizer;

    public ThompsonSamplingService(ViewerHashtagEngagementRepository repository,
                                   HashtagNormalizer hashtagNormalizer) {
        this.repository = repository;
        this.hashtagNormalizer = hashtagNormalizer;
    }

    /**
     * Draw a {@code Beta(α, β)} sample for the {@code (viewer, hashtag)}
     * posterior. Lazy READ — no row → {@code Beta(1, 1)} (uniform).
     */
    public double samplePosterior(Long viewerId, String hashtag) {
        if (viewerId == null || hashtag == null || hashtag.isBlank()) {
            return ThreadLocalRandom.current().nextDouble();
        }
        Optional<ViewerHashtagEngagement> row = repository.findByUserIdAndHashtag(viewerId, hashtag);
        if (row.isEmpty()) {
            return sampleBeta(1.0, 1.0);
        }
        return sampleBeta(row.get().getAlpha(), row.get().getBeta());
    }

    /**
     * After-commit α-update for each normalized hashtag on an engaged
     * post. Normalizes + deduplicates the input set before issuing the
     * batched UPSERT — Postgres requires a deduplicated key list under
     * {@code ON CONFLICT DO UPDATE} or it raises "cannot affect row a
     * second time" on the collision.
     */
    public void recordEngagement(Long viewerId, Collection<String> postHashtags) {
        if (viewerId == null || postHashtags == null || postHashtags.isEmpty()) {
            return;
        }
        String[] normalized = normalizeAndDedup(postHashtags);
        if (normalized.length == 0) {
            return;
        }
        try {
            repository.incrementAlphaBatch(viewerId, normalized);
        } catch (RuntimeException ex) {
            // Honour the listener contract: log WARN with viewer + tag count
            // + cause, do NOT propagate — bandit consistency is best-effort,
            // not a correctness invariant. The user's engagement write has
            // already committed before this fires. We deliberately log
            // tagCount rather than the tag values: hashtags carry user
            // intent (interests, political affiliation, mental-health
            // signals) and a chatty WARN under sustained DB pressure would
            // bleed that content into ops logs at scale. Reproducing a
            // specific failure can use (viewerId, timestamp) to find the
            // engagement row and reconstruct the tag set.
            log.warn("bandit-upsert-failed viewerId={} tagCount={} cause={}",
                    viewerId, normalized.length, ex.getClass().getSimpleName());
        }
    }

    /**
     * β-update path — UNWIRED in v1. The signature is here so the
     * impression-tracking follow-up PR can wire it without re-shaping
     * the service contract.
     */
    public void recordImpressionDecay(Long viewerId, Collection<String> postHashtags) {
        // Intentionally a no-op in v1. Impression-tracking PR wires this.
    }

    /** Normalize + dedup tags into the array shape the UPSERT expects. */
    private String[] normalizeAndDedup(Collection<String> raw) {
        List<String> input = new ArrayList<>(raw);
        return hashtagNormalizer.normalize(input).toArray(new String[0]);
    }

    /**
     * {@code Beta(α, β)} sample via two {@code Gamma(·, 1)} draws.
     * Marsaglia-Tsang for the Gamma side: exact and ~5x faster than
     * Cheng's BC algorithm at our typical α range.
     *
     * <p>Visible-for-test (package-private).
     */
    static double sampleBeta(double alpha, double beta) {
        double x = sampleGamma(alpha);
        double y = sampleGamma(beta);
        double denom = x + y;
        if (denom == 0.0) {
            // Extreme corner: both Gamma draws underflowed to 0. Return the
            // midpoint of the support [0, 1] so the caller still gets a
            // ranking-friendly value (this is statistically vanishingly rare
            // at α, β ≥ 1).
            return 0.5;
        }
        return x / denom;
    }

    /**
     * {@code Gamma(α, 1)} sample for {@code α ≥ 1} via Marsaglia-Tsang
     * (2000). For {@code α < 1} we apply the standard boost trick
     * (Gamma(α, 1) = Gamma(α+1, 1) · U^(1/α)) — defensive, since the
     * CHECK constraint should keep α ≥ 1.0 always.
     */
    private static double sampleGamma(double alpha) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (alpha < 1.0) {
            double u = rng.nextDouble();
            return sampleGamma(alpha + 1.0) * Math.pow(u, 1.0 / alpha);
        }
        double d = alpha - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = rng.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0.0) {
                continue;
            }
            v = v * v * v;
            double u = rng.nextDouble();
            // Squeezed acceptance — short-circuits ~94% of the inner
            // body when v is reasonable, saving the log call.
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v;
            }
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }
}
