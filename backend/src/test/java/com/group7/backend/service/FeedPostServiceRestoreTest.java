package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.exception.FeedPostExpiredRestoreException;
import com.group7.backend.exception.FeedPostNotDeletedException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostEditHistoryRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the soft-delete restore slice of {@link FeedPostService}
 * (#487). The service is constructed manually so the
 * {@code restoreWindowDays} primitive can be set deterministically;
 * {@code @InjectMocks} would default it to {@code 0} (then clamped to
 * {@code 1}), which is too short for normal restore-success scenarios.
 */
@ExtendWith(MockitoExtension.class)
class FeedPostServiceRestoreTest {

    private static final int RESTORE_WINDOW_DAYS = 30;

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.group7.backend.repository.AttachmentRepository attachmentRepository;
    @Mock private HashtagNormalizer hashtagNormalizer;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private FeedPostEventPublisher feedPostEventPublisher;
    @Mock private FeedPostEditHistoryRepository historyRepository;

    private FeedPostService service;

    @BeforeEach
    void setUp() {
        service = new FeedPostService(
                feedPostRepository,
                userRepository,
                attachmentRepository,
                hashtagNormalizer,
                feedPostMapper,
                feedPostEventPublisher,
                historyRepository,
                RESTORE_WINDOW_DAYS);
    }

    @Test
    void restorePost_clearsDeletedAt_andBumpsUpdatedAt_whenAuthorWithinWindow() {
        FeedPost post = freshPost(7L, 1L, "Body");
        OffsetDateTime softDeletedAt = OffsetDateTime.now().minusDays(5);
        post.setDeletedAt(softDeletedAt);
        OffsetDateTime origUpdated = post.getUpdatedAt();
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        FeedPostResponse response = service.restorePost(7L, 1L);

        assertThat(response).isNotNull();
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getUpdatedAt()).isAfterOrEqualTo(origUpdated);
        verify(feedPostRepository).save(post);
    }

    @Test
    void restorePost_throws404_whenPostMissing() {
        when(feedPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restorePost(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void restorePost_throws403_whenActorIsNotAuthor() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.setDeletedAt(OffsetDateTime.now().minusDays(1));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.restorePost(7L, 999L))
                .isInstanceOf(AccessDeniedException.class);

        // Author check fires before deletedAt inspection — non-author
        // cannot use the restore path to probe a post's deletion state.
        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void restorePost_throws409_whenPostIsLive() {
        FeedPost post = freshPost(7L, 1L, "Body");
        // deletedAt is null — post is currently visible.
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.restorePost(7L, 1L))
                .isInstanceOf(FeedPostNotDeletedException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void restorePost_throws410_whenWindowHasExpired() {
        FeedPost post = freshPost(7L, 1L, "Body");
        // Soft-deleted 31 days ago, window is 30 days.
        post.setDeletedAt(OffsetDateTime.now().minusDays(31));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.restorePost(7L, 1L))
                .isInstanceOf(FeedPostExpiredRestoreException.class)
                .hasMessageContaining("30 days");

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void restorePost_succeedsAtExactWindowBoundary_minusOneSecond() {
        FeedPost post = freshPost(7L, 1L, "Body");
        // 30 days minus one second ago — still within the window.
        post.setDeletedAt(OffsetDateTime.now().minusDays(30).plusSeconds(1));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        service.restorePost(7L, 1L);

        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    void restoreWindowDaysClampedTo1_whenConfigured0() {
        // Reconstruct with a misconfigured 0; the constructor clamps to 1.
        FeedPostService clampedService = new FeedPostService(
                feedPostRepository, userRepository, attachmentRepository, hashtagNormalizer,
                feedPostMapper, feedPostEventPublisher, historyRepository, 0);

        FeedPost post = freshPost(7L, 1L, "Body");
        // Soft-deleted 2 days ago — past the clamped 1-day window.
        post.setDeletedAt(OffsetDateTime.now().minusDays(2));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> clampedService.restorePost(7L, 1L))
                .isInstanceOf(FeedPostExpiredRestoreException.class);
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static FeedPost freshPost(Long id, Long authorId, String body) {
        FeedPost p = new FeedPost(authorId, body);
        p.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setVersion(0L);
        return p;
    }

    private static FeedPostResponse stubResponse(Long id, Long authorId) {
        return new FeedPostResponse(id, authorId, "U" + authorId, "body", List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), false, true, List.of());
    }
}
