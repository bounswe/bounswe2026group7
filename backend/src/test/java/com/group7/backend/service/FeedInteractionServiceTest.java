package com.group7.backend.service;

import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.dto.response.FeedPostInteractionState;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.FeedPostShare;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostBookmarkRepository;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FeedPostShareRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.PostCountTuple;
import com.group7.backend.service.FeedInteractionService.PostCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedInteractionService}. Mocks every repository
 * and the two publisher facades so each test exercises one branch of the
 * service's control flow without standing up Spring or Postgres.
 *
 * <p>Three patterns recur throughout the suite:
 * <ul>
 *   <li>The {@code requireVisiblePost} guard surfaces as a 404 from every
 *       write path — covered once per surface (like, bookmark, share,
 *       comment, getComment) so a regression there can't slip through any
 *       single surface unnoticed.</li>
 *   <li>The self-vs-other notification carve-out (author skips their own
 *       like / comment / share) is asserted on each surface separately
 *       because each surface has its own publisher call.</li>
 *   <li>State after a toggle is read back through
 *       {@link FeedInteractionService#interactionState} — the count + flag
 *       queries are mocked there rather than re-asserted on every test.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FeedInteractionServiceTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private FeedPostLikeRepository likeRepository;
    @Mock private FeedPostBookmarkRepository bookmarkRepository;
    @Mock private FeedPostShareRepository shareRepository;
    @Mock private FeedPostCommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @Mock private ApplicationEventPublisher eventPublisher;

    private FeedInteractionService service;

    @BeforeEach
    void setUp() {
        service = new FeedInteractionService(
                feedPostRepository,
                likeRepository,
                bookmarkRepository,
                shareRepository,
                commentRepository,
                userRepository,
                feedPostMapper,
                notificationEventPublisher,
                eventPublisher);
    }

    // ── toggleLike ─────────────────────────────────────────────────────────

    @Test
    void toggleLike_throws404_whenPostMissingOrSoftDeleted() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleLike(7L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(likeRepository, never()).upsertLike(anyLong(), anyLong());
        verify(notificationEventPublisher, never()).publishFeedLike(anyLong(), any(), anyLong());
    }

    @Test
    void toggleLike_firstCall_insertsAndNotifiesNonSelfAuthor() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        // First existsBy call (in toggleLike) → not yet liked, so service
        // hits the upsert branch. The second call (inside interactionState)
        // → returns true because the row now exists in production. Mockito's
        // sequenced thenReturn(...) reproduces that flip without a real DB.
        when(likeRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(false, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));

        FeedPostInteractionState state = service.toggleLike(7L, 1L);

        verify(likeRepository).upsertLike(7L, 1L);
        verify(likeRepository, never()).deleteById(any());
        verify(notificationEventPublisher).publishFeedLike(eq(99L), any(), eq(7L));
        assertThat(state.viewerHasLiked()).isTrue();
    }

    @Test
    void toggleLike_secondCall_deletesAndSkipsNotification() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        // First existsBy → already liked; second (inside interactionState
        // after the row is gone) → false.
        when(likeRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(true, false);

        FeedPostInteractionState state = service.toggleLike(7L, 1L);

        verify(likeRepository).deleteById(any());
        verify(likeRepository, never()).upsertLike(anyLong(), anyLong());
        // Unliking never produces a notification — only the insert path does.
        verify(notificationEventPublisher, never()).publishFeedLike(anyLong(), any(), anyLong());
        assertThat(state.viewerHasLiked()).isFalse();
    }

    @Test
    void toggleLike_selfLike_skipsNotificationButStillInserts() {
        FeedPost post = freshPost(7L, 1L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(false);

        service.toggleLike(7L, 1L);

        verify(likeRepository).upsertLike(7L, 1L);
        // Self-like → no notification fires.
        verify(notificationEventPublisher, never()).publishFeedLike(anyLong(), any(), anyLong());
    }

    // ── toggleBookmark ─────────────────────────────────────────────────────

    @Test
    void toggleBookmark_throws404_whenPostMissing() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleBookmark(7L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bookmarkRepository, never()).upsertBookmark(anyLong(), anyLong());
    }

    @Test
    void toggleBookmark_firstCall_inserts() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        // Flip mirrors the toggleLike test: first call (in toggle) returns
        // false, second call (in interactionState after the upsert) returns
        // true.
        when(bookmarkRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(false, true);

        FeedPostInteractionState state = service.toggleBookmark(7L, 1L);

        verify(bookmarkRepository).upsertBookmark(7L, 1L);
        assertThat(state.viewerHasBookmarked()).isTrue();
    }

    @Test
    void toggleBookmark_secondCall_deletes() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        when(bookmarkRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(true, false);

        FeedPostInteractionState state = service.toggleBookmark(7L, 1L);

        verify(bookmarkRepository).deleteById(any());
        assertThat(state.viewerHasBookmarked()).isFalse();
    }

    // ── recordShare ────────────────────────────────────────────────────────

    @Test
    void recordShare_throws404_whenPostMissing() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordShare(7L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(shareRepository, never()).save(any());
    }

    @Test
    void recordShare_notifiesNonSelfAuthor() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));

        service.recordShare(7L, 1L);

        verify(shareRepository).save(any(FeedPostShare.class));
        verify(notificationEventPublisher).publishFeedShare(eq(99L), any(), eq(7L));
    }

    @Test
    void recordShare_selfShare_skipsNotification() {
        FeedPost post = freshPost(7L, 1L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));

        service.recordShare(7L, 1L);

        verify(shareRepository).save(any(FeedPostShare.class));
        verify(notificationEventPublisher, never()).publishFeedShare(anyLong(), any(), anyLong());
    }

    // ── addComment ─────────────────────────────────────────────────────────

    @Test
    void addComment_throws404_whenPostMissing() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(7L, 1L, "hello"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_throws400_onBlankBody() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.addComment(7L, 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        verify(commentRepository, never()).save(any());
        verify(notificationEventPublisher, never()).publishFeedComment(anyLong(), any(), anyLong());
    }

    @Test
    void addComment_throws400_onNullBody() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.addComment(7L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addComment_savesAndNotifiesNonSelfAuthor() {
        FeedPost post = freshPost(7L, 99L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));
        when(commentRepository.save(any(FeedPostComment.class))).thenAnswer(inv -> {
            FeedPostComment c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        FeedCommentResponse result = service.addComment(7L, 1L, "great post");

        verify(commentRepository).save(any(FeedPostComment.class));
        verify(notificationEventPublisher).publishFeedComment(eq(99L), any(), eq(7L));
        assertThat(result.id()).isEqualTo(42L);
    }

    @Test
    void addComment_selfComment_skipsNotification() {
        FeedPost post = freshPost(7L, 1L);
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));
        when(commentRepository.save(any(FeedPostComment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addComment(7L, 1L, "self-comment");

        verify(notificationEventPublisher, never()).publishFeedComment(anyLong(), any(), anyLong());
    }

    // ── editComment ────────────────────────────────────────────────────────

    @Test
    void editComment_throws404_whenCommentMissing() {
        when(commentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editComment(42L, 1L, "new"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editComment_throws403_whenRequesterIsNotAuthor() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "old");
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.editComment(42L, 999L, "new"))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void editComment_throws404_whenSoftDeleted() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "old");
        c.setDeletedAt(OffsetDateTime.now().minusHours(1));
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.editComment(42L, 1L, "new"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editComment_throws400_onBlankBody() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "old");
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.editComment(42L, 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void editComment_updatesBodyAndUpdatedAt() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "old");
        OffsetDateTime originalUpdated = c.getUpdatedAt();
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));
        when(commentRepository.save(c)).thenReturn(c);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));

        service.editComment(42L, 1L, "new body");

        assertThat(c.getBody()).isEqualTo("new body");
        assertThat(c.getUpdatedAt()).isAfterOrEqualTo(originalUpdated);
        verify(commentRepository).save(c);
    }

    // ── deleteComment ──────────────────────────────────────────────────────

    @Test
    void deleteComment_throws404_whenCommentMissing() {
        when(commentRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(42L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteComment_throws403_whenRequesterIsNotAuthor() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "x");
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteComment(42L, 999L))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_setsDeletedAt_andSaves() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "x");
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));
        when(commentRepository.save(c)).thenReturn(c);

        service.deleteComment(42L, 1L);

        assertThat(c.getDeletedAt()).isNotNull();
        verify(commentRepository).save(c);
    }

    @Test
    void deleteComment_isIdempotent_whenAlreadySoftDeleted() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "x");
        OffsetDateTime priorDelete = OffsetDateTime.now().minusHours(2);
        c.setDeletedAt(priorDelete);
        when(commentRepository.findById(42L)).thenReturn(Optional.of(c));

        service.deleteComment(42L, 1L);

        // Same deletedAt — the service short-circuits without re-saving.
        assertThat(c.getDeletedAt()).isEqualTo(priorDelete);
        verify(commentRepository, never()).save(any());
    }

    // ── getComment (permalink) ─────────────────────────────────────────────

    @Test
    void getComment_throws404_whenCommentMissingOrSoftDeleted() {
        when(commentRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComment(42L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getComment_throws404_whenParentPostSoftDeleted() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "x");
        when(commentRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(c));
        // Parent post invisible → orphan-permalink guard fires.
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getComment(42L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getComment_returnsDto_whenBothVisible() {
        FeedPostComment c = freshComment(42L, 7L, 1L, "body");
        when(commentRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(c));
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(freshPost(7L, 99L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L)));

        FeedCommentResponse result = service.getComment(42L, 1L);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.body()).isEqualTo("body");
    }

    // ── interactionState ───────────────────────────────────────────────────

    @Test
    void interactionState_withViewer_consultsAllRepositories() {
        when(likeRepository.countByIdPostId(7L)).thenReturn(5L);
        when(bookmarkRepository.countByIdPostId(7L)).thenReturn(2L);
        when(shareRepository.countByPostId(7L)).thenReturn(3L);
        when(commentRepository.countByPostIdAndDeletedAtIsNull(7L)).thenReturn(4L);
        when(likeRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(true);
        when(bookmarkRepository.existsByIdPostIdAndIdUserId(7L, 1L)).thenReturn(false);

        FeedPostInteractionState state = service.interactionState(7L, 1L);

        assertThat(state.likeCount()).isEqualTo(5L);
        assertThat(state.commentCount()).isEqualTo(4L);
        assertThat(state.shareCount()).isEqualTo(3L);
        assertThat(state.bookmarkCount()).isEqualTo(2L);
        assertThat(state.viewerHasLiked()).isTrue();
        assertThat(state.viewerHasBookmarked()).isFalse();
    }

    @Test
    void interactionState_nullViewer_skipsViewerProbes() {
        when(likeRepository.countByIdPostId(7L)).thenReturn(5L);
        when(bookmarkRepository.countByIdPostId(7L)).thenReturn(2L);
        when(shareRepository.countByPostId(7L)).thenReturn(3L);
        when(commentRepository.countByPostIdAndDeletedAtIsNull(7L)).thenReturn(4L);

        FeedPostInteractionState state = service.interactionState(7L, null);

        // Anonymous reads skip the viewer-relative existsBy probes
        // so the SQL footprint of the public timeline stays bounded.
        assertThat(state.viewerHasLiked()).isFalse();
        assertThat(state.viewerHasBookmarked()).isFalse();
        verify(likeRepository, never()).existsByIdPostIdAndIdUserId(anyLong(), anyLong());
        verify(bookmarkRepository, never()).existsByIdPostIdAndIdUserId(anyLong(), anyLong());
    }

    // ── batchCounts ────────────────────────────────────────────────────────

    @Test
    void batchCounts_emptyInput_returnsEmptyMapWithoutQuerying() {
        Map<Long, PostCounts> result = service.batchCounts(List.of());

        assertThat(result).isEmpty();
        // Postgres rejects WHERE id IN () — the short-circuit prevents an
        // exception masquerading as a count query.
        verify(likeRepository, never()).countByPostIdIn(any());
        verify(commentRepository, never()).countVisibleByPostIdIn(any());
    }

    @Test
    void batchCounts_emitsZerosForPostsWithNoLikesOrComments() {
        // postId 7 has 5 likes + 4 comments; postId 8 has nothing → assert
        // the second id surfaces as (0, 0) rather than being absent.
        when(likeRepository.countByPostIdIn(any())).thenReturn(List.of(new PostCountTuple(7L, 5L)));
        when(commentRepository.countVisibleByPostIdIn(any())).thenReturn(List.of(new PostCountTuple(7L, 4L)));

        Map<Long, PostCounts> result = service.batchCounts(List.of(7L, 8L));

        assertThat(result).containsOnlyKeys(7L, 8L);
        assertThat(result.get(7L)).isEqualTo(new PostCounts(5L, 4L));
        assertThat(result.get(8L)).isEqualTo(new PostCounts(0L, 0L));
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static Mentor mentor(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("U" + id);
        return m;
    }

    private static FeedPost freshPost(Long id, Long authorId) {
        FeedPost p = new FeedPost(authorId, "body");
        p.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }

    private static FeedPostComment freshComment(Long id, Long postId, Long authorId, String body) {
        FeedPostComment c = new FeedPostComment(postId, authorId, body);
        c.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }
}
