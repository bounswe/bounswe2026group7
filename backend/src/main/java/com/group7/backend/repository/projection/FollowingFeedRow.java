package com.group7.backend.repository.projection;

import java.time.Instant;

/**
 * Spring Data interface projection for the merged Following-feed read.
 * The native query UNIONs two branches:
 * <ul>
 *   <li>Original-post branch — posts authored by users the viewer
 *       follows. {@link #getSharedById()}, {@link #getShareCommentary()},
 *       {@link #getShareRowId()}, and {@link #getSharedAt()} are all
 *       null on this branch.</li>
 *   <li>Repost branch — posts that users the viewer follows have
 *       reposted (i.e., {@code feed_post_shares.is_repost = TRUE}). All
 *       four share-metadata getters are populated.</li>
 * </ul>
 *
 * <p>{@link #getSortAt()} drives {@code ORDER BY} in the SQL; it is the
 * post's creation time on the original-post branch and the share's
 * creation time on the repost branch — so a post reposted today by a
 * followee surfaces above a freshly authored post from yesterday.
 *
 * <p>{@link #getShareRowId()} provides a unique pagination tiebreaker:
 * when two reposts of the same post share the same {@code (sort_at,
 * id)}, the share row's surrogate key breaks the tie deterministically.
 *
 * <p>Spring Data binds getter names to native-query column aliases via
 * lowercase-with-underscores camel-casing — {@code shared_by_id}
 * binds to {@link #getSharedById()}. Renaming a SQL alias without
 * updating this interface silently breaks pagination, so any change to
 * either side must be paired with an integration-test assertion.
 *
 * <p>Precedent for interface projections on native queries lives at
 * {@code ConversationLastMessageView}.
 */
public interface FollowingFeedRow {

    /** Post id. Same value across both branches for the same post. */
    Long getId();

    /** Author of the original post. */
    Long getAuthorId();

    /** Post body. */
    String getBody();

    /**
     * Post creation timestamp ({@code feed_posts.created_at}), NOT the
     * share creation time. Stays stable across both branches so the UI
     * can render "posted X ago" regardless of whether the row arrived
     * via the post or the repost surface.
     *
     * <p>Returns {@link Instant} because Spring Data interface projections
     * on native queries materialise TIMESTAMPTZ as {@code Instant} by
     * default; the consuming service converts to {@code OffsetDateTime}
     * at the UTC offset before populating the response DTO.
     */
    Instant getCreatedAt();

    /** User id of the reposting follower; null on the original-post branch. */
    Long getSharedById();

    /** Quote-share commentary; null on bare reposts and on the original-post branch. */
    String getShareCommentary();

    /**
     * Surrogate key of the {@code feed_post_shares} row — null on the
     * original-post branch. Used only by the ORDER BY tiebreaker, not
     * surfaced in the response.
     */
    Long getShareRowId();

    /**
     * Share creation timestamp ({@code feed_post_shares.created_at});
     * null on the original-post branch. The UI uses this to render
     * "reposted X ago" alongside {@link #getCreatedAt()}. Same Instant /
     * OffsetDateTime conversion contract as {@link #getCreatedAt()}.
     */
    Instant getSharedAt();

    /**
     * Sort key for the merged result: post creation time on the original
     * branch, share creation time on the repost branch. Drives the
     * {@code ORDER BY} clause in the native query.
     */
    Instant getSortAt();
}
