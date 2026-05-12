package com.group7.backend.service.ranking.signals;

import com.group7.backend.config.MentorRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.embedding.MentorProfileText;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.MentorScoringSignal;
import com.group7.backend.service.ranking.ScoringContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Cosine similarity between the mentor's profile embedding and the
 * mentee's query embedding (OpenAI {@code text-embedding-3-small} by
 * default). Both embeddings are cached by
 * {@link SemanticSimilarityService}, so the only fresh API call per
 * matching request is the mentee side; the 200 mentor embeddings
 * warm-up once and stay cached.
 *
 * <p>Emits factor {@code semantic-match:0.XX} only when cosine ≥ 0.5
 * (anything below that is noise on the user-facing card). On embedding
 * failure (missing API key, OpenAI down) returns score 0 and attaches
 * the {@code semantic-unavailable} factor so the matcher card reflects
 * the degraded state without throwing.
 */
@Component
public class SemanticMatchSignal implements MentorScoringSignal {

    private static final double SHOW_THRESHOLD = 0.5;

    private final SemanticSimilarityService similarity;
    private final MentorRecommendationProperties props;

    public SemanticMatchSignal(SemanticSimilarityService similarity,
                               MentorRecommendationProperties props) {
        this.similarity = similarity;
        this.props = props;
    }

    @Override public String code() { return "semantic-match"; }

    @Override
    public boolean isEnabled() {
        return props.signals() != null && props.signals().semanticMatchEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights() == null ? 0.0 : props.weights().semanticMatch();
    }

    @Override
    public SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext ctx) {
        float[] mentorVec = similarity.embed(MentorProfileText.forMentor(mentor));
        float[] menteeVec = similarity.embed(MentorProfileText.forMentee(mentee));
        if (mentorVec.length == 0 || menteeVec.length == 0) {
            return new SignalContribution(0.0, List.of("semantic-unavailable"));
        }
        double cos = similarity.cosineSimilarity(menteeVec, mentorVec);
        double normalized = Math.max(0.0, Math.min(1.0, cos));
        List<String> factors = (cos >= SHOW_THRESHOLD)
                ? List.of(String.format(Locale.ROOT, "semantic-match:%.2f", cos))
                : List.of();
        return new SignalContribution(normalized, factors);
    }
}
