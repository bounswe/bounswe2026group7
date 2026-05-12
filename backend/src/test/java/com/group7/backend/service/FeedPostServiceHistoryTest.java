package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostEditEntry;
import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostEditHistory;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostEditHistoryRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the edit-history slice of {@link FeedPostService}
 * (#487). Validates the snapshot-on-update behaviour and the
 * authorisation surface of {@link FeedPostService#getPostHistory}.
 */
@ExtendWith(MockitoExtension.class)
class FeedPostServiceHistoryTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.group7.backend.repository.AttachmentRepository attachmentRepository;
    @Mock private HashtagNormalizer hashtagNormalizer;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private FeedPostEventPublisher feedPostEventPublisher;
    @Mock private FeedPostEditHistoryRepository historyRepository;

    private FeedPostService feedPostService;

    @BeforeEach
    void setUp() {
        // Manual construction: the service's restoreWindowDays primitive
        // parameter cannot be auto-wired by Mockito. 30 matches the
        // production default; restore-specific tests live in
        // FeedPostServiceRestoreTest.
        feedPostService = new FeedPostService(
                feedPostRepository,
                userRepository,
                attachmentRepository,
                hashtagNormalizer,
                feedPostMapper,
                feedPostEventPublisher,
                historyRepository,
                30);
    }

    // ── update() snapshot behaviour ───────────────────────────────────────

    @Test
    void update_bodyChanges_savesSnapshotWithPreviousBodyAndHashtags() {
        FeedPost post = freshPost(7L, 1L, "Original body");
        post.getHashtags().add(new FeedPostHashtag(post, "alpha"));
        post.getHashtags().add(new FeedPostHashtag(post, "beta"));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, "New body", null, null);

        ArgumentCaptor<FeedPostEditHistory> captor = ArgumentCaptor.forClass(FeedPostEditHistory.class);
        verify(historyRepository).save(captor.capture());
        FeedPostEditHistory snap = captor.getValue();
        assertThat(snap.getPostId()).isEqualTo(7L);
        assertThat(snap.getEditorId()).isEqualTo(1L);
        assertThat(snap.getPreviousBody()).isEqualTo("Original body");
        assertThat(snap.getPreviousHashtags()).containsExactly("alpha", "beta");
        assertThat(snap.getEditedAt()).isNotNull();
    }

    @Test
    void update_hashtagsChange_savesSnapshotWithPreviousHashtags() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.getHashtags().add(new FeedPostHashtag(post, "old"));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(hashtagNormalizer.normalize(List.of("new"))).thenReturn(Set.of("new"));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, null, List.of("new"), null);

        ArgumentCaptor<FeedPostEditHistory> captor = ArgumentCaptor.forClass(FeedPostEditHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousHashtags()).containsExactly("old");
        assertThat(captor.getValue().getPreviousBody()).isEqualTo("Body");
    }

    @Test
    void update_bodyAndHashtagsChange_savesExactlyOneSnapshot() {
        FeedPost post = freshPost(7L, 1L, "Old");
        post.getHashtags().add(new FeedPostHashtag(post, "a"));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(hashtagNormalizer.normalize(List.of("b"))).thenReturn(Set.of("b"));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, "New", List.of("b"), null);

        // Exactly one history row even though both fields changed.
        verify(historyRepository).save(any(FeedPostEditHistory.class));
    }

    @Test
    void update_noOpBody_doesNotSaveSnapshot() {
        // Caller PATCHes with the same body that's already on the post.
        FeedPost post = freshPost(7L, 1L, "Same body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, "Same body", null, null);

        verify(historyRepository, never()).save(any(FeedPostEditHistory.class));
    }

    @Test
    void update_noOpHashtags_doesNotSaveSnapshot() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.getHashtags().add(new FeedPostHashtag(post, "tag"));
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(hashtagNormalizer.normalize(List.of("tag"))).thenReturn(Set.of("tag"));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, null, List.of("tag"), null);

        verify(historyRepository, never()).save(any(FeedPostEditHistory.class));
    }

    @Test
    void update_bothFieldsNull_doesNotSaveSnapshot() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, null, null, null);

        verify(historyRepository, never()).save(any(FeedPostEditHistory.class));
    }

    // ── getPostHistory() authorisation surface ────────────────────────────

    @Test
    void getPostHistory_throws404_whenPostMissing() {
        when(feedPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.getPostHistory(404L, 1L, 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPostHistory_authorCanRead_evenWhenPostSoftDeleted() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.setDeletedAt(OffsetDateTime.now());
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(historyRepository.findByPostIdOrderByEditedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(historyRow(7L, 1L, "old", List.of("x"))));

        List<FeedPostEditEntry> result = feedPostService.getPostHistory(7L, 1L, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).previousBody()).isEqualTo("old");
    }

    @Test
    void getPostHistory_adminCanReadAnyPost() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        Admin admin = admin(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(historyRepository.findByPostIdOrderByEditedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        List<FeedPostEditEntry> result = feedPostService.getPostHistory(7L, 99L, 50);

        assertThat(result).isEmpty();
    }

    @Test
    void getPostHistory_throws403_whenNonAuthorNonAdmin() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mentor(2L)));

        assertThatThrownBy(() -> feedPostService.getPostHistory(7L, 2L, 50))
                .isInstanceOf(AccessDeniedException.class);

        verify(historyRepository, never()).findByPostIdOrderByEditedAtDesc(any(), any());
    }

    @Test
    void getPostHistory_throws403_whenActorAccountMissing() {
        // Author check fails (id mismatch), and userRepository can't find
        // the actor either — defensive path returns 403 rather than 404
        // (we don't want to leak whether a non-author user exists).
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.getPostHistory(7L, 404L, 50))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getPostHistory_clampsLimitAbove50() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(historyRepository.findByPostIdOrderByEditedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        feedPostService.getPostHistory(7L, 1L, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByPostIdOrderByEditedAtDesc(eq(7L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getPostHistory_clampsLimitBelow1() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(historyRepository.findByPostIdOrderByEditedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        feedPostService.getPostHistory(7L, 1L, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByPostIdOrderByEditedAtDesc(eq(7L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private static Mentor mentor(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("U" + id);
        return m;
    }

    private static Admin admin(Long id) {
        Admin a = new Admin();
        a.setId(id);
        a.setFirstName("A" + id);
        return a;
    }

    private static FeedPost freshPost(Long id, Long authorId, String body) {
        FeedPost p = new FeedPost(authorId, body);
        p.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setVersion(0L);
        return p;
    }

    private static FeedPostEditHistory historyRow(Long postId, Long editorId,
                                                  String prevBody, List<String> prevTags) {
        FeedPostEditHistory h = new FeedPostEditHistory();
        h.setId(1L);
        h.setPostId(postId);
        h.setEditorId(editorId);
        h.setPreviousBody(prevBody);
        h.setPreviousHashtags(prevTags);
        h.setEditedAt(OffsetDateTime.now());
        return h;
    }

    private static FeedPostResponse stubResponse(Long id, Long authorId) {
        return new FeedPostResponse(id, authorId, "U" + authorId, "body", List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), false, true, List.of());
    }
}
