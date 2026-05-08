package com.group7.backend.event;

import com.group7.backend.dto.feed.FeedTopics;
import com.group7.backend.dto.response.FeedPostPushPayload;
import com.group7.backend.repository.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedFanoutListener} (#349). Tests call the
 * listener method directly — bypassing Spring's {@code @Async} proxy —
 * which is the cleanest way to assert behaviour deterministically
 * without a {@code CountDownLatch}. The proxy-based async dispatch is
 * exercised end-to-end in {@code FeedRealtimeIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class FeedFanoutListenerTest {

    @Mock private FollowRepository followRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @InjectMocks private FeedFanoutListener listener;

    private FeedPostCreatedEvent event;

    @BeforeEach
    void setUp() {
        event = new FeedPostCreatedEvent(
                42L, 17L, "Ada", OffsetDateTime.parse("2026-05-08T10:00:00Z"));
    }

    @Test
    void onFeedPostCreated_broadcastsToEveryFollower_whenAuthorHasFollowers() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of(101L, 102L, 103L));

        listener.onFeedPostCreated(event);

        verify(messagingTemplate, times(3))
                .convertAndSend(any(String.class), any(FeedPostPushPayload.class));
    }

    @Test
    void onFeedPostCreated_buildsPayloadFromEventFields() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of(101L));

        listener.onFeedPostCreated(event);

        ArgumentCaptor<FeedPostPushPayload> captor =
                ArgumentCaptor.forClass(FeedPostPushPayload.class);
        verify(messagingTemplate).convertAndSend(any(String.class), captor.capture());

        FeedPostPushPayload payload = captor.getValue();
        assertThat(payload.postId()).isEqualTo(42L);
        assertThat(payload.authorId()).isEqualTo(17L);
        assertThat(payload.authorFirstName()).isEqualTo("Ada");
        assertThat(payload.createdAt()).isEqualTo(event.createdAt());
    }

    @Test
    void onFeedPostCreated_destinationsAllUseFeedPrefix() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of(101L, 102L));

        listener.onFeedPostCreated(event);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(2))
                .convertAndSend(destCaptor.capture(), any(FeedPostPushPayload.class));
        List<String> destinations = destCaptor.getAllValues();
        assertThat(destinations).allMatch(d -> d.startsWith(FeedTopics.FEED_PREFIX));
        assertThat(destinations).containsExactlyInAnyOrder(
                FeedTopics.FEED_PREFIX + "101",
                FeedTopics.FEED_PREFIX + "102");
    }

    @Test
    void onFeedPostCreated_isNoOp_whenAuthorHasNoFollowers() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of());

        listener.onFeedPostCreated(event);

        verify(messagingTemplate, never())
                .convertAndSend(any(String.class), any(FeedPostPushPayload.class));
    }

    @Test
    void onFeedPostCreated_continuesFanout_whenBrokerThrowsForOneRecipient() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of(101L, 102L, 103L));
        // First broker call throws; subsequent calls must still be made.
        doThrow(new MessagingException("broker down for 101"))
                .when(messagingTemplate)
                .convertAndSend(eq(FeedTopics.FEED_PREFIX + "101"), any(FeedPostPushPayload.class));

        listener.onFeedPostCreated(event);

        verify(messagingTemplate, times(3))
                .convertAndSend(any(String.class), any(FeedPostPushPayload.class));
    }

    @Test
    void onFeedPostCreated_doesNotPropagate_whenFollowerLookupThrows() {
        // Top-level try/catch is load-bearing because the codebase has no
        // AsyncUncaughtExceptionHandler — without it this throw would be
        // silently swallowed by SimpleAsyncTaskExecutor.
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenThrow(new RuntimeException("DB down"));

        listener.onFeedPostCreated(event);  // must not throw

        verify(messagingTemplate, never())
                .convertAndSend(any(String.class), any(FeedPostPushPayload.class));
    }

    @Test
    void onFeedPostCreated_singleFollower_singleBroadcast() {
        when(followRepository.findFollowerIdsByFolloweeId(17L))
                .thenReturn(Set.of(99L));

        listener.onFeedPostCreated(event);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq(FeedTopics.FEED_PREFIX + "99"), any(FeedPostPushPayload.class));
    }
}
