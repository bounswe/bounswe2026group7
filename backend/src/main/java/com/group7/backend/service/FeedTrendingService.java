package com.group7.backend.service;

import com.group7.backend.dto.response.FeedTrendingHashtag;
import com.group7.backend.repository.FeedTrendingRepository;
import com.group7.backend.repository.projection.TrendingHashtagTuple;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service surface for the trending-hashtag aggregate (#487). Two
 * responsibilities: read the materialized view ranked, and refresh
 * the view (called by the scheduler).
 *
 * <p>The score formula
 * ({@code postCount + 2 * uniqueLikers + 3 * commentCount})
 * is duplicated in the V45 expression index so the read is an index
 * scan. If you change the formula here, change V45 to match.
 */
@Service
public class FeedTrendingService {

    /** Hard cap on the trending list — clamped server-side regardless of caller. */
    static final int MAX_LIMIT = 50;

    private final FeedTrendingRepository trendingRepository;

    public FeedTrendingService(FeedTrendingRepository trendingRepository) {
        this.trendingRepository = trendingRepository;
    }

    /**
     * Returns the top {@code limit} trending hashtags, ranked by score.
     * {@code limit} is clamped to {@code [1, MAX_LIMIT]} — defence in
     * depth alongside the controller's {@code @Min}/{@code @Max}.
     */
    public List<FeedTrendingHashtag> listTrendingHashtags(int limit) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        return trendingRepository.findTopTrending(clamped).stream()
                .map(FeedTrendingService::toDto)
                .toList();
    }

    /** Refreshes the materialized view (called by the hourly scheduler). */
    public void refreshTrendingView() {
        trendingRepository.refreshConcurrently();
    }

    private static FeedTrendingHashtag toDto(TrendingHashtagTuple t) {
        double score = t.postCount() + 2.0 * t.uniqueLikers() + 3.0 * t.commentCount();
        return new FeedTrendingHashtag(
                t.tag(),
                t.postCount(),
                t.uniqueLikers(),
                t.commentCount(),
                t.latestPostAt(),
                score);
    }
}
