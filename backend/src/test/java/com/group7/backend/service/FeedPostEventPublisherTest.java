package com.group7.backend.service;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentee;
import com.group7.backend.event.FeedPostCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link FeedPostEventPublisher} (#349). Verifies the
 * facade maps a {@link FeedPost} entity + author to a
 * {@link FeedPostCreatedEvent} with all four fields.
 */
@ExtendWith(MockitoExtension.class)
class FeedPostEventPublisherTest {

    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks private FeedPostEventPublisher publisher;

    private FeedPost post;
    private Mentee author;
    private OffsetDateTime createdAt;

    @BeforeEach
    void setUp() {
        createdAt = OffsetDateTime.parse("2026-05-08T10:00:00Z");

        author = new Mentee();
        author.setId(17L);
        author.setFirstName("Ada");
        author.setLastName("Lovelace");
        author.setEmail("ada@example.com");

        post = new FeedPost(17L, "Hello world");
        post.setId(42L);
        post.setCreatedAt(createdAt);
        post.setUpdatedAt(createdAt);
    }

    @Test
    void publishCreated_emitsEventWithAllFourFieldsFromEntityAndAuthor() {
        publisher.publishCreated(post, author);

        ArgumentCaptor<FeedPostCreatedEvent> captor =
                ArgumentCaptor.forClass(FeedPostCreatedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        FeedPostCreatedEvent event = captor.getValue();
        assertThat(event.postId()).isEqualTo(42L);
        assertThat(event.authorId()).isEqualTo(17L);
        assertThat(event.authorFirstName()).isEqualTo("Ada");
        assertThat(event.createdAt()).isEqualTo(createdAt);
    }
}
