package com.group7.backend.repository.projection;

/**
 * Projection target for grouped comment-id count queries on
 * {@code FeedPostCommentLikeRepository} (#483).
 *
 * <p>Parallel to {@link PostCountTuple} but distinct so the call-site
 * naming reads {@code countByCommentIdIn → CommentCountTuple} rather
 * than reusing {@code PostCountTuple} with a re-purposed field.
 */
public record CommentCountTuple(
        Long commentId,
        Long count
) {
}
