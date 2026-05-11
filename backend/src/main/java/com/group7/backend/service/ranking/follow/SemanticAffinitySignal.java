package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Signal: cosine similarity between the viewer's profile embedding and
 * the candidate's profile embedding. Captures synonym-equivalence
 * (e.g. "ML" vs "machine learning") and freeform text in bio /
 * mentoring-goals / career-interest fields that the other six signals
 * can't read.
 *
 * <p>Reads {@link FollowRecommendationContext#viewerInterestEmbedding()}
 * (pre-computed once per request by {@code FollowRecommendationService})
 * and asks {@link SemanticSimilarityService} for the candidate's vector
 * on demand — the service caches per (model + text) so repeat candidates
 * within the cache TTL hit the cache.
 *
 * <p><b>Fail-open.</b> If either vector is empty (viewer profile blank,
 * OpenAI unreachable, key missing) the signal emits
 * {@code semantic-unavailable} and contributes zero. The other six
 * signals carry the recommendation.
 *
 * <p>Gated by {@code app.recommendations.follow.signals.semantic-affinity-enabled=true}
 * so the bean doesn't load when the signal is dark. The aggregator's
 * {@code List<FollowScoringSignal>} injection just won't see it and the
 * recommendation runs on the other six.
 */
@Component
@ConditionalOnProperty(name = "app.recommendations.follow.signals.semantic-affinity-enabled",
        havingValue = "true")
public class SemanticAffinitySignal implements FollowScoringSignal {

    private final SemanticSimilarityService semantic;
    private final FollowRecommendationProperties props;

    public SemanticAffinitySignal(SemanticSimilarityService semantic,
                                  FollowRecommendationProperties props) {
        this.semantic = semantic;
        this.props = props;
    }

    @Override public String code() { return "semantic-affinity"; }
    @Override public boolean isEnabled() { return props.signals().semanticAffinityEnabled(); }
    @Override public double getWeight() { return props.weights().semanticAffinity(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        float[] viewerVec = ctx.viewerInterestEmbedding();
        if (viewerVec == null || viewerVec.length == 0) {
            return SignalContribution.of(0.0, "semantic-unavailable");
        }
        String candText = candidateProfileText(candidate);
        if (candText.isBlank()) {
            return SignalContribution.NONE;
        }
        float[] candVec = semantic.embed(candText);
        if (candVec.length == 0) {
            return SignalContribution.of(0.0, "semantic-unavailable");
        }
        double sim = SemanticSimilarityService.cosineSimilarity(viewerVec, candVec);
        if (sim <= 0.0) {
            return SignalContribution.NONE;
        }
        return SignalContribution.of(sim, String.format("semantic-affinity:%.2f", sim));
    }

    /**
     * Concatenates the candidate's visible profile-text fields into a
     * single embedding input. Order-stable per call so cache keys are
     * deterministic for the same candidate.
     *
     * <p>Public so {@code FollowRecommendationService.buildAdvancedContext}
     * can reuse it to embed the viewer with the exact same field ordering
     * the candidate path uses — that symmetry is what makes
     * {@code cosineSimilarity(viewer, candidate)} meaningful.
     */
    public static String candidateProfileText(User u) {
        if (u instanceof Mentor m) {
            return joinNonBlank(
                    m.getBio(),
                    m.getExpertise(),
                    m.getField(),
                    joinList(m.getInterests()),
                    m.getMentoringGoals());
        }
        if (u instanceof Mentee me) {
            return joinNonBlank(
                    me.getGoals(),
                    me.getCareerInterest(),
                    me.getMajor(),
                    joinList(me.getInterests()),
                    me.getBackgroundInfo());
        }
        return "";
    }

    private static String joinNonBlank(String... parts) {
        List<String> kept = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p != null && !p.isBlank()) kept.add(p);
        }
        return String.join(" ", kept);
    }

    private static String joinList(List<String> labels) {
        if (labels == null || labels.isEmpty()) return "";
        return String.join(" ", labels);
    }
}
