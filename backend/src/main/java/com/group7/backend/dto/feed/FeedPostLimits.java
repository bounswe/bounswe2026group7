package com.group7.backend.dto.feed;

/**
 * Domain limits for the social-feed posts surface (#348).
 *
 * <p>Single source of truth referenced by:
 * <ul>
 *   <li>DTO {@code @Size} annotations on {@code CreateFeedPostRequest} and
 *       {@code UpdateFeedPostRequest};</li>
 *   <li>service-side defensive caps in {@code FeedPostService} and
 *       {@code HashtagNormalizer};</li>
 *   <li>the {@code V23__create_feed_posts.sql} migration's {@code CHECK}
 *       constraint comments — the literal numeric values must stay in
 *       sync with the constants here, otherwise startup-time
 *       {@code ddl-auto = validate} will surface the mismatch
 *       immediately.</li>
 * </ul>
 *
 * <p>Limits chosen with reference to social-media conventions in 2026
 * (LinkedIn 3000 / Mastodon 500 / Bluesky 300 / Twitter free 280) and the
 * project's own separation between short-form feed posts (#348) and
 * long-form Mentor Blog posts (#339). 2000 characters is generous enough
 * for thoughtful posts while keeping the feed scannable.
 */
public final class FeedPostLimits {

    /**
     * Maximum body length in characters. Enforced by the DTO {@code @Size}
     * annotation and by the {@code feed_posts_body_length} DB {@code CHECK}.
     */
    public static final int MAX_BODY_LENGTH = 2_000;

    /**
     * Maximum number of hashtags per post. Enforced by the DTO
     * {@code @Size(max = ...)} on the hashtag list and by
     * {@code HashtagNormalizer}'s defensive cap (which throws
     * {@code IllegalArgumentException} → 400 if exceeded after normalisation).
     */
    public static final int MAX_HASHTAGS = 10;

    /**
     * Maximum length of a single hashtag, in characters, after normalisation
     * (lowercased, leading {@code #} stripped). Enforced by the
     * {@code HashtagNormalizer} regex, by the DTO {@code @Size} on each
     * hashtag list element, and by the {@code feed_post_hashtags_tag_length}
     * DB {@code CHECK}.
     */
    public static final int MAX_HASHTAG_LENGTH = 50;

    private FeedPostLimits() {
        // No instances. Constants only.
    }
}
