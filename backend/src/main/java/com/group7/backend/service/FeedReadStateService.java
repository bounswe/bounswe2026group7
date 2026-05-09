package com.group7.backend.service;

import com.group7.backend.dto.response.FeedUnreadCountResponse;
import com.group7.backend.entity.LastFeedReadAt;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.LastFeedReadAtRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Per-user feed read-state service (#349). Backs
 * {@code POST /api/feed/mark-read} and {@code GET /api/feed/unread-count}.
 *
 * <p>The cap is configurable via {@code app.feed.unread.cap} (default
 * 99) and env-overrideable via {@code APP_FEED_UNREAD_CAP}. The
 * underlying repository query short-circuits at {@code LIMIT cap+1} so
 * the scan stops once we know the answer is "≥ cap"; we never read all
 * unread rows when the cap suffices.
 */
@Service
public class FeedReadStateService {

    /**
     * Sentinel "no cursor yet" timestamp returned to the
     * {@link FeedPostRepository#countUnreadFollowingPostsCapped} query when
     * a user has never marked the feed as read. Unix epoch in UTC — well
     * within Postgres TIMESTAMPTZ range (4713 BC..294276 AD), unlike
     * {@link OffsetDateTime#MIN} (year -999999999) which would serialize
     * out of range.
     */
    static final OffsetDateTime EPOCH_UTC =
            OffsetDateTime.of(LocalDate.EPOCH, LocalTime.MIN, ZoneOffset.UTC);

    private final LastFeedReadAtRepository repository;
    private final FeedPostRepository feedPostRepository;
    private final int unreadCap;

    public FeedReadStateService(LastFeedReadAtRepository repository,
                                FeedPostRepository feedPostRepository,
                                @Value("${app.feed.unread.cap:99}") int unreadCap) {
        this.repository = repository;
        this.feedPostRepository = feedPostRepository;
        this.unreadCap = unreadCap;
    }

    /**
     * Set {@code userId}'s read cursor to the current statement-start
     * wall clock (server-side {@code clock_timestamp()}). Idempotent
     * under contention — the upsert serializes through the PK.
     */
    @Transactional
    public void markRead(Long userId) {
        repository.markRead(userId);
    }

    /**
     * Return the viewer's unread count, capped at
     * {@code app.feed.unread.cap}. If the cursor row is absent (the
     * viewer has never marked the feed read) the {@link #EPOCH_UTC}
     * sentinel substitutes, so all visible posts in their follow graph
     * count as unread.
     */
    @Transactional(readOnly = true)
    public FeedUnreadCountResponse unreadCount(Long userId) {
        OffsetDateTime since = repository.findById(userId)
                .map(LastFeedReadAt::getLastReadAt)
                .orElse(EPOCH_UTC);
        long raw = feedPostRepository.countUnreadFollowingPostsCapped(
                userId, since, unreadCap + 1);
        boolean cappedAtMax = raw > unreadCap;
        long displayed = Math.min(raw, unreadCap);
        return new FeedUnreadCountResponse(displayed, cappedAtMax);
    }
}
