package com.group7.backend.repository.projection;

/**
 * Projection target for grouped feed-post count queries on
 * {@code FeedPostLikeRepository} and {@code FeedPostCommentRepository}.
 *
 * <p>Both repositories use a JPQL constructor expression
 * ({@code SELECT new PostCountTuple(...)}) so the read services never have to
 * unpack a {@code List<Object[]>} when populating per-post interaction counts
 * across a page of feed items.
 */
public record PostCountTuple(
        Long postId,
        Long count
) {
}
