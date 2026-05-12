package com.group7.backend.scheduler;

import com.group7.backend.service.FeedTrendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly cron that refreshes the {@code feed_trending_24h} materialized
 * view (#487). Default {@code 0 5 * * * *} — five past every hour. The
 * top-of-hour slot is taken by {@code TokenCleanupScheduler}, so we
 * sit off it to avoid coincident DB load spikes.
 *
 * <p>{@code REFRESH MATERIALIZED VIEW CONCURRENTLY} requires the unique
 * index added in V45; without it Postgres rejects the CONCURRENTLY
 * form. The refresh runs in its own transaction (managed inside
 * {@code FeedTrendingRepository}), so this scheduler stays a thin
 * facade.
 *
 * <p>Off by default in the test profile via
 * {@code app.feed.trending.enabled=false}; the dedicated integration
 * test flips the flag back on.
 */
@Component
@ConditionalOnProperty(name = "app.feed.trending.enabled",
        havingValue = "true", matchIfMissing = true)
public class FeedTrendingRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedTrendingRefreshScheduler.class);

    private final FeedTrendingService trendingService;

    public FeedTrendingRefreshScheduler(FeedTrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @Scheduled(cron = "${app.feed.trending.cron:0 5 * * * *}",
            zone = "${app.feed.trending.zone:UTC}")
    public void refresh() {
        long start = System.nanoTime();
        try {
            trendingService.refreshTrendingView();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            log.debug("feed-trending refreshed in {} ms", ms);
        } catch (RuntimeException ex) {
            // Don't propagate — a failed refresh leaves the view stale
            // (acceptable) but should not abort the JVM scheduler thread
            // or skew metrics. The next tick will retry.
            log.error("feed-trending refresh failed; view will be stale until next tick", ex);
        }
    }
}
