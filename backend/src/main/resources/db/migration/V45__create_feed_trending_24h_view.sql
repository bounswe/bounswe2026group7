-- 24-hour trending hashtag aggregate (#487). On-demand aggregation
-- would scan feed_post_hashtags + feed_post_likes + feed_post_comments
-- per request and lock the interaction tables under contention; a
-- materialized view refreshed hourly bounds query cost and gives a
-- consistent ordering across requests.
--
-- HAVING COUNT(DISTINCT p.id) >= 2 prevents single-author noise from
-- topping the chart.
--
-- The unique index on (tag) is required by REFRESH MATERIALIZED VIEW
-- CONCURRENTLY (Postgres rejects the CONCURRENTLY form without it).
-- Without CONCURRENTLY the refresh would AccessExclusiveLock the view
-- against readers for the duration.
--
-- The expression index on the score formula keeps the trending list
-- query (ORDER BY score DESC LIMIT 20) on an index scan.
--
-- Note: the LEFT JOIN cartesian on (likes × comments) is acceptable at
-- current scale (HAVING ≥ 2 + 24h window cap candidate count). Revisit
-- as a CTE-with-pre-aggregated-counts if profiling shows refresh cost
-- exceeding the hourly budget.
--
-- The CREATE statement populates the view atomically (no WITH NO DATA),
-- so the first hourly REFRESH at :05 has prior data to compare against.

CREATE MATERIALIZED VIEW feed_trending_24h AS
SELECT
    h.tag,
    COUNT(DISTINCT p.id)         AS post_count,
    COUNT(DISTINCT l.user_id)    AS unique_likers,
    COUNT(DISTINCT c.id)         AS comment_count,
    MAX(p.created_at)            AS latest_post_at
FROM feed_post_hashtags h
JOIN feed_posts p
        ON p.id = h.post_id
       AND p.deleted_at IS NULL
       AND p.created_at >= NOW() - INTERVAL '24 hours'
LEFT JOIN feed_post_likes l
        ON l.post_id = p.id
LEFT JOIN feed_post_comments c
        ON c.post_id = p.id
       AND c.deleted_at IS NULL
GROUP BY h.tag
HAVING COUNT(DISTINCT p.id) >= 2;

CREATE UNIQUE INDEX idx_feed_trending_24h_tag
    ON feed_trending_24h (tag);

CREATE INDEX idx_feed_trending_24h_score
    ON feed_trending_24h ((post_count * 1.0 + unique_likers * 2.0 + comment_count * 3.0) DESC);
