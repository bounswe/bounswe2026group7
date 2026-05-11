package com.group7.backend.event;

import java.util.Set;

/**
 * Published by {@code FeedInteractionService} after a viewer performs a
 * positive engagement (like-on, comment-create, share, bookmark-on) on a
 * post. Drives the Thompson-sampling bandit's α update via
 * {@code FeedEngagementBanditListener}.
 *
 * <p>Only insert branches publish; toggle-off events (unlike, unbookmark)
 * do NOT publish. Without β updates in v1, crediting α on a like-then-
 * unlike would double-count the user's interest signal.
 *
 * @param viewerId      the engaging user
 * @param postHashtags  normalized hashtags on the engaged post
 */
public record FeedEngagementEvent(Long viewerId, Set<String> postHashtags) {

    public FeedEngagementEvent {
        postHashtags = (postHashtags == null) ? Set.of() : Set.copyOf(postHashtags);
    }
}
