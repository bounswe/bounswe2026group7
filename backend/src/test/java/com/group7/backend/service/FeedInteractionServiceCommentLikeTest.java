package com.group7.backend.service;

import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.FeedPostHashtagId;
import com.group7.backend.entity.Mentor;
import com.group7.backend.event.FeedEngagementEvent;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostBookmarkRepository;
import com.group7.backend.repository.FeedPostCommentLikeRepository;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FeedPostShareRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.CommentCountTuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the comment-like slice of {@link FeedInteractionService}
 * (#483). Validates the toggle branch logic, the bandit-engagement hook
 * (toggle-on publishes; toggle-off does not), the addComment short-circuit
 * (no per-row SQL on a fresh comment), and the listComments batch contract
 * (one query per interaction type for an N-comment page).
 */
@ExtendWith(MockitoExtension.class)
class FeedInteractionServiceCommentLikeTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private FeedPostLikeRepository likeRepository;
    @Mock private FeedPostBookmarkRepository bookmarkRepository;
    @Mock private FeedPostShareRepository shareRepository;
    @Mock private FeedPostCommentRepository commentRepository;
    @Mock private FeedPostCommentLikeRepository commentLikeRepository;
    @Mock private UserRepository userRepository;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private NotificationEventPublisher notificationEventPublisher;
    @Mock private ApplicationEventPublisher eventPublisher;

    // Manual construction (not @InjectMocks): the service's primitive
    // boolean (respectVisibility) + Duration parameters cannot be auto-
    // wired by Mockito. Pin both to the production default so this test's
    // assertions stay in sync with the shipped behaviour.
    private FeedInteractionService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new FeedInteractionService(
                feedPostRepository,
                likeRepository,
                bookmarkRepository,
                shareRepository,
                commentRepository,
                commentLikeRepository,
                userRepository,
                feedPostMapper,
                notificationEventPublisher,
                eventPublisher,
                java.time.Duration.ofSeconds(60),
                false);
    }

    // ── toggleCommentLike ─────────────────────────────────────────────────

    @Test
    void toggleCommentLike_firstCall_inserts_publishesEngagement_andReportsLikedTrue() {
        FeedPostComment comment = freshComment(101L, 42L, 1L, "hi");
        FeedPost post = freshPost(42L, 7L);
        attachHashtags(post, "java");
        when(commentRepository.findById(101L)).thenReturn(Optional.of(comment));
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));
        when(commentLikeRepository.existsByIdCommentIdAndIdUserId(101L, 9L)).thenReturn(false);
        when(commentLikeRepository.upsertCommentLike(101L, 9L)).thenReturn(1);
        when(commentLikeRepository.countByIdCommentId(101L)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L, "Author")));

        FeedCommentResponse response = service.toggleCommentLike(101L, 9L);

        assertThat(response.viewerHasLiked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(1L);
        verify(commentLikeRepository).upsertCommentLike(101L, 9L);
        verify(commentLikeRepository, never()).deleteById(any());
        // Bandit hook fired with parent post hashtags.
        ArgumentCaptor<FeedEngagementEvent> evt = ArgumentCaptor.forClass(FeedEngagementEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue().viewerId()).isEqualTo(9L);
        assertThat(evt.getValue().postHashtags()).containsExactly("java");
    }

    @Test
    void toggleCommentLike_secondCall_deletes_doesNOTpublishEngagement_andReportsLikedFalse() {
        FeedPostComment comment = freshComment(101L, 42L, 1L, "hi");
        FeedPost post = freshPost(42L, 7L);
        attachHashtags(post, "java");
        when(commentRepository.findById(101L)).thenReturn(Optional.of(comment));
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));
        when(commentLikeRepository.existsByIdCommentIdAndIdUserId(101L, 9L)).thenReturn(true);
        when(commentLikeRepository.countByIdCommentId(101L)).thenReturn(0L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L, "Author")));

        FeedCommentResponse response = service.toggleCommentLike(101L, 9L);

        assertThat(response.viewerHasLiked()).isFalse();
        assertThat(response.likeCount()).isZero();
        verify(commentLikeRepository).deleteById(any());
        verify(commentLikeRepository, never()).upsertCommentLike(anyLong(), anyLong());
        // Toggle-off MUST NOT republish — without β updates a like→unlike
        // would otherwise double-credit α in the bandit ranker.
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void toggleCommentLike_404_whenCommentMissing() {
        when(commentRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleCommentLike(101L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentLikeRepository, never()).upsertCommentLike(anyLong(), anyLong());
        verify(commentLikeRepository, never()).deleteById(any());
    }

    @Test
    void toggleCommentLike_404_whenCommentSoftDeleted() {
        FeedPostComment deleted = freshComment(101L, 42L, 1L, "hi");
        deleted.setDeletedAt(OffsetDateTime.now());
        when(commentRepository.findById(101L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.toggleCommentLike(101L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(feedPostRepository, never()).findByIdAndDeletedAtIsNull(anyLong());
        verify(commentLikeRepository, never()).upsertCommentLike(anyLong(), anyLong());
    }

    @Test
    void toggleCommentLike_404_whenParentPostSoftDeleted() {
        FeedPostComment comment = freshComment(101L, 42L, 1L, "hi");
        when(commentRepository.findById(101L)).thenReturn(Optional.of(comment));
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleCommentLike(101L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentLikeRepository, never()).upsertCommentLike(anyLong(), anyLong());
    }

    // ── addComment short-circuits the like-load (#483) ────────────────────

    @Test
    void addComment_doesNotIssuePerRowLikeQueries_onFreshComment() {
        FeedPost post = freshPost(42L, 1L);
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));
        when(commentRepository.save(any(FeedPostComment.class))).thenAnswer(inv -> {
            FeedPostComment c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor(1L, "Author")));

        FeedCommentResponse response = service.addComment(42L, 1L, "hello");

        assertThat(response.likeCount()).isZero();
        assertThat(response.viewerHasLiked()).isFalse();
        // The 5-arg overload is invoked directly with (false, 0L) — no
        // existsBy / countBy SQL on the create path.
        verify(commentLikeRepository, never()).existsByIdCommentIdAndIdUserId(anyLong(), anyLong());
        verify(commentLikeRepository, never()).countByIdCommentId(anyLong());
    }

    // ── listComments batches comment-like state (#483) ────────────────────

    @Test
    void listComments_batchLoadsCountsAndViewerLikedExactlyOnce_perPage() {
        FeedPost post = freshPost(42L, 1L);
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));

        List<FeedPostComment> rows = List.of(
                freshComment(101L, 42L, 1L, "a"),
                freshComment(102L, 42L, 1L, "b"),
                freshComment(103L, 42L, 1L, "c")
        );
        Pageable pageable = PageRequest.of(0, 20);
        Page<FeedPostComment> page = new PageImpl<>(rows, pageable, rows.size());
        when(commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(42L, pageable)).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(List.of(mentor(1L, "Author")));
        when(commentLikeRepository.countByCommentIdIn(any())).thenReturn(List.of(
                new CommentCountTuple(101L, 3L),
                new CommentCountTuple(103L, 1L)
        ));
        when(commentLikeRepository.findLikedCommentIdsForViewer(eq(9L), any()))
                .thenReturn(List.of(101L));

        Page<FeedCommentResponse> result = service.listComments(42L, 9L, pageable);

        // Exactly one batch call per interaction type, regardless of page size.
        verify(commentLikeRepository, times(1)).countByCommentIdIn(any());
        verify(commentLikeRepository, times(1)).findLikedCommentIdsForViewer(eq(9L), any());
        verify(commentLikeRepository, never()).countByIdCommentId(anyLong());
        verify(commentLikeRepository, never()).existsByIdCommentIdAndIdUserId(anyLong(), anyLong());
        // Per-row state surfaces correctly.
        List<FeedCommentResponse> items = result.getContent();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).likeCount()).isEqualTo(3L);
        assertThat(items.get(0).viewerHasLiked()).isTrue();
        assertThat(items.get(1).likeCount()).isZero();
        assertThat(items.get(1).viewerHasLiked()).isFalse();
        assertThat(items.get(2).likeCount()).isEqualTo(1L);
        assertThat(items.get(2).viewerHasLiked()).isFalse();
    }

    @Test
    void listComments_emptyPage_skipsAllBatchSQL() {
        FeedPost post = freshPost(42L, 1L);
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));
        Pageable pageable = PageRequest.of(0, 20);
        when(commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(42L, pageable))
                .thenReturn(Page.empty(pageable));

        Page<FeedCommentResponse> result = service.listComments(42L, 9L, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(commentLikeRepository, never()).countByCommentIdIn(any());
        verify(commentLikeRepository, never()).findLikedCommentIdsForViewer(any(), any());
    }

    @Test
    void listComments_anonymousViewer_skipsLikedLookup() {
        FeedPost post = freshPost(42L, 1L);
        when(feedPostRepository.findVisibleById(eq(42L), any(), anyBoolean())).thenReturn(Optional.of(post));
        List<FeedPostComment> rows = List.of(freshComment(101L, 42L, 1L, "a"));
        Pageable pageable = PageRequest.of(0, 20);
        when(commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(42L, pageable))
                .thenReturn(new PageImpl<>(rows, pageable, 1));
        when(userRepository.findAllById(any())).thenReturn(List.of(mentor(1L, "Author")));
        when(commentLikeRepository.countByCommentIdIn(any())).thenReturn(List.of());

        Page<FeedCommentResponse> result = service.listComments(42L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).viewerHasLiked()).isFalse();
        // No viewer → no liked-id lookup.
        verify(commentLikeRepository, never()).findLikedCommentIdsForViewer(any(), any());
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private static Mentor mentor(Long id, String firstName) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(firstName);
        return m;
    }

    private static FeedPost freshPost(Long id, Long authorId) {
        FeedPost p = new FeedPost(authorId, "body");
        p.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setVersion(0L);
        return p;
    }

    private static FeedPostComment freshComment(Long id, Long postId, Long authorId, String body) {
        FeedPostComment c = new FeedPostComment(postId, authorId, body);
        c.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c.setVersion(0L);
        return c;
    }

    private static void attachHashtags(FeedPost post, String... tags) {
        for (String t : tags) {
            FeedPostHashtag h = new FeedPostHashtag();
            h.setId(new FeedPostHashtagId(post.getId(), t));
            h.setPost(post);
            post.getHashtags().add(h);
        }
    }
}
