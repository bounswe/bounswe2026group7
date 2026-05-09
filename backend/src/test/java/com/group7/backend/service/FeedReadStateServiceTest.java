package com.group7.backend.service;

import com.group7.backend.dto.response.FeedUnreadCountResponse;
import com.group7.backend.entity.LastFeedReadAt;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.LastFeedReadAtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedReadStateService} (#349). The cap-boundary
 * cases are pinned explicitly because they're exactly where off-by-one
 * bugs live.
 */
@ExtendWith(MockitoExtension.class)
class FeedReadStateServiceTest {

    private static final int CAP = 99;
    private static final Long USER_ID = 1L;

    @Mock private LastFeedReadAtRepository repository;
    @Mock private FeedPostRepository feedPostRepository;
    private FeedReadStateService service;

    @BeforeEach
    void setUp() {
        service = new FeedReadStateService(repository, feedPostRepository, CAP);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_delegatesToRepository() {
        service.markRead(USER_ID);

        verify(repository).markRead(USER_ID);
    }

    // ── unreadCount: cursor-state branches ────────────────────────────────────

    @Test
    void unreadCount_usesEpochUtc_whenCursorIsAbsent() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(feedPostRepository.countUnreadFollowingPostsCapped(anyLong(), any(), anyInt()))
                .thenReturn(0L);

        service.unreadCount(USER_ID);

        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(feedPostRepository)
                .countUnreadFollowingPostsCapped(eq(USER_ID), sinceCaptor.capture(), eq(CAP + 1));
        assertThat(sinceCaptor.getValue()).isEqualTo(FeedReadStateService.EPOCH_UTC);
    }

    @Test
    void unreadCount_usesCursorTimestamp_whenCursorIsPresent() {
        OffsetDateTime cursor = OffsetDateTime.parse("2026-05-08T10:00:00Z");
        LastFeedReadAt row = new LastFeedReadAt(USER_ID, cursor);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(row));
        when(feedPostRepository.countUnreadFollowingPostsCapped(anyLong(), any(), anyInt()))
                .thenReturn(0L);

        service.unreadCount(USER_ID);

        verify(feedPostRepository)
                .countUnreadFollowingPostsCapped(eq(USER_ID), eq(cursor), eq(CAP + 1));
    }

    // ── unreadCount: cap-boundary cases ───────────────────────────────────────

    @Test
    void unreadCount_returnsZero_whenNothingUnread() {
        stubCount(0L);

        FeedUnreadCountResponse response = service.unreadCount(USER_ID);

        assertThat(response.count()).isZero();
        assertThat(response.cappedAtMax()).isFalse();
    }

    @Test
    void unreadCount_returnsRawCount_whenBelowCap() {
        stubCount(42L);

        FeedUnreadCountResponse response = service.unreadCount(USER_ID);

        assertThat(response.count()).isEqualTo(42L);
        assertThat(response.cappedAtMax()).isFalse();
    }

    @Test
    void unreadCount_returnsCap_whenExactlyAtCap_andDoesNotFlagCapped() {
        stubCount((long) CAP);

        FeedUnreadCountResponse response = service.unreadCount(USER_ID);

        assertThat(response.count()).isEqualTo((long) CAP);
        assertThat(response.cappedAtMax()).isFalse();
    }

    @Test
    void unreadCount_returnsCap_andFlagsCapped_whenStrictlyOverCap() {
        // Repo query LIMITs at cap+1, so the maximum raw value is cap+1.
        stubCount((long) CAP + 1);

        FeedUnreadCountResponse response = service.unreadCount(USER_ID);

        assertThat(response.count()).isEqualTo((long) CAP);
        assertThat(response.cappedAtMax()).isTrue();
    }

    private void stubCount(long raw) {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(feedPostRepository.countUnreadFollowingPostsCapped(anyLong(), any(), anyInt()))
                .thenReturn(raw);
    }
}
