package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.service.embedding.SemanticSimilarityService;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cosine similarity between the post embedding and the viewer's
 * interest-text embedding. Falls back to hashtag-Jaccard when the
 * embedder is unavailable so a single signal still produces a useful
 * score; emits {@code feed:semantic-unavailable} in that case so the UI
 * can show the fallback transparently.
 *
 * <p>When the signal is disabled via the kill switch
 * ({@code app.recommendations.feed.signals.semantic-match-enabled=false})
 * it returns {@link SignalContribution#NONE} with no factor — distinct
 * from runtime failure, which still emits {@code semantic-unavailable}.
 */
@Component("feedSemanticMatchSignal")
public class SemanticMatchSignal implements FeedScoringSignal {

    private final ForYouRecommendationProperties props;
    private final SemanticSimilarityService similarity;

    public SemanticMatchSignal(ForYouRecommendationProperties props,
                               SemanticSimilarityService similarity) {
        this.props = props;
        this.similarity = similarity;
    }

    @Override
    public String code() {
        return "semantic-match";
    }

    @Override
    public boolean isEnabled() {
        return props.signals().semanticMatchEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights().semanticMatch();
    }

    @Override
    public SignalContribution compute(FeedPost post, FeedScoringContext context) {
        Set<String> viewerInterests = context.viewerInterestHashtags();
        if (viewerInterests.isEmpty()) {
            return SignalContribution.NONE;
        }

        float[] viewerEmbedding = context.viewerInterestEmbedding();
        float[] postEmbedding = context.postEmbeddings().getOrDefault(post.getId(), new float[0]);

        // Embedder unavailable on either side → fall back to hashtag-Jaccard
        // and flag the response so the UI can show the degraded mode. The
        // candidate-pool fallback keeps the ranker meaningful even when
        // OpenAI is down or the API key isn't wired.
        if (viewerEmbedding.length == 0 || postEmbedding.length == 0) {
            double jaccard = hashtagJaccard(post, viewerInterests);
            return new SignalContribution(jaccard, List.of("feed:semantic-unavailable"));
        }

        double cosine = Math.max(0.0, similarity.cosineSimilarity(viewerEmbedding, postEmbedding));
        if (cosine >= 0.5) {
            // Round to 2dp for chip display.
            String formatted = String.format("%.2f", cosine);
            return new SignalContribution(cosine, List.of("feed:semantic-match:" + formatted));
        }
        return new SignalContribution(cosine, List.of());
    }

    /**
     * Set-overlap fallback: |post tags ∩ viewer interests| / |post tags|.
     * Mirrors {@code InterestOverlapFeedRanker}'s historical computation.
     * Returns 0 when either side is empty so the fallback never claims a
     * false positive.
     */
    private static double hashtagJaccard(FeedPost post, Set<String> viewerInterests) {
        Set<String> postTags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .collect(Collectors.toSet());
        if (postTags.isEmpty()) {
            return 0.0;
        }
        long matches = postTags.stream().filter(viewerInterests::contains).count();
        return (double) matches / postTags.size();
    }
}
