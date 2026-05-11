package com.group7.backend.listener;

import com.group7.backend.event.FeedEngagementEvent;
import com.group7.backend.service.bandit.ThompsonSamplingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link FeedEngagementBanditListener}. The listener
 * is the AFTER_COMMIT + REQUIRES_NEW trampoline between
 * {@code FeedInteractionService} and the bandit's α-update path;
 * these tests call {@code onFeedEngagement} directly to bypass Spring's
 * proxy. AFTER_COMMIT firing and rollback semantics are covered
 * end-to-end in {@code AdvancedForYouFeedIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class FeedEngagementBanditListenerTest {

    @Mock private ThompsonSamplingService bandit;
    @InjectMocks private FeedEngagementBanditListener listener;

    @Test
    void event_forwardsViewerAndHashtagsToBandit() {
        FeedEngagementEvent event = new FeedEngagementEvent(7L, Set.of("react", "javascript"));

        listener.onFeedEngagement(event);

        ArgumentCaptor<Set<String>> tagCaptor = ArgumentCaptor.forClass(Set.class);
        verify(bandit).recordEngagement(eq7L(), tagCaptor.capture());
        assertThat(tagCaptor.getValue()).containsExactlyInAnyOrder("react", "javascript");
    }

    @Test
    void nullEvent_noop() {
        listener.onFeedEngagement(null);
        verify(bandit, never()).recordEngagement(anyLong(), any());
    }

    @Test
    void nullViewerId_noop() {
        listener.onFeedEngagement(new FeedEngagementEvent(null, Set.of("react")));
        verify(bandit, never()).recordEngagement(anyLong(), any());
    }

    @Test
    void emptyHashtags_stillForwardsAndBanditDecides() {
        // The listener doesn't filter empty tags — the bandit's recordEngagement
        // is responsible for that. Sending the event through keeps the contract
        // tight and lets the bandit handle "what counts as engagement worth
        // remembering" in one place.
        FeedEngagementEvent event = new FeedEngagementEvent(7L, Set.of());

        listener.onFeedEngagement(event);

        verify(bandit).recordEngagement(eq7L(), eqEmptySet());
    }

    @Test
    void banditException_isCaughtNotPropagated() {
        doThrow(new RuntimeException("upsert exploded"))
                .when(bandit).recordEngagement(anyLong(), any());

        // Must not throw — bandit consistency is best-effort. The listener
        // catches and WARN-logs so a malformed event or DB blip never bubbles
        // up to the (already-committed) request thread.
        listener.onFeedEngagement(new FeedEngagementEvent(7L, Set.of("react")));

        verify(bandit).recordEngagement(eq7L(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Long eq7L() {
        return org.mockito.ArgumentMatchers.eq(7L);
    }

    private static Set<String> eqEmptySet() {
        return org.mockito.ArgumentMatchers.eq(Set.of());
    }
}
