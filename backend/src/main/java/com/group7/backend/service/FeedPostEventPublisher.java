package com.group7.backend.service;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.User;
import com.group7.backend.event.FeedPostCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin facade over {@link ApplicationEventPublisher} for feed-post
 * lifecycle events (#349). Exists so {@link FeedPostService} stays free
 * of event-wiring noise — {@code create} makes one call
 * ({@code publishCreated(post, author)}) instead of constructing the
 * event inline. Future events ({@code FeedPostUpdatedEvent},
 * {@code FeedPostDeletedEvent}) get a uniform home here.
 *
 * <p>Mirrors {@code NotificationEventPublisher} from #273.
 */
@Component
public class FeedPostEventPublisher {

    private final ApplicationEventPublisher publisher;

    public FeedPostEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publish a {@link FeedPostCreatedEvent} for {@code post}. Caller
     * must invoke inside an open {@code @Transactional} boundary so the
     * downstream {@link com.group7.backend.event.FeedFanoutListener}'s
     * AFTER_COMMIT delivery is bound to a real commit. {@code author} is
     * the post's author — passed in (rather than re-fetched here) so we
     * don't hit the DB twice for a value the caller already has.
     */
    public void publishCreated(FeedPost post, User author) {
        publisher.publishEvent(new FeedPostCreatedEvent(
                post.getId(),
                post.getAuthorId(),
                author.getFirstName(),
                post.getCreatedAt()));
    }
}
