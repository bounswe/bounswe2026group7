package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AttachmentRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedPostService} (#348). Mocks the repositories
 * and the {@link FeedPostMapper} so each test exercises one branch of the
 * service's control flow.
 */
@ExtendWith(MockitoExtension.class)
class FeedPostServiceTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private HashtagNormalizer hashtagNormalizer;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private FeedPostEventPublisher feedPostEventPublisher;
    @InjectMocks private FeedPostService feedPostService;

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void create_rejectsAdminRequester_with403() {
        Admin admin = admin(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> feedPostService.create(99L, "Hello", List.of(), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Admins cannot create");

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void create_rejectsBlankBody_with400() {
        Mentor author = mentor(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> feedPostService.create(1L, "   ", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body must not be blank");

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void create_rejectsNullBody_with400() {
        Mentor author = mentor(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> feedPostService.create(1L, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsMissingAuthor_with404() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.create(404L, "ok", List.of(), null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void create_persists_andDelegatesToMapper() {
        Mentor author = mentor(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(hashtagNormalizer.normalize(any())).thenReturn(java.util.Set.of("data", "ai"));
        when(feedPostRepository.save(any(FeedPost.class))).thenAnswer(inv -> {
            FeedPost p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });
        FeedPostResponse expected = stubResponse(42L, 1L);
        when(feedPostMapper.toResponse(any(FeedPost.class), any())).thenReturn(expected);

        FeedPostResponse result = feedPostService.create(1L, "Hello", List.of("DATA", "ai"), null);

        assertThat(result).isSameAs(expected);
        verify(feedPostRepository).save(any(FeedPost.class));
    }

    @Test
    void create_publishesFeedPostCreatedEvent_afterSave_via_publisherFacade() {
        Mentor author = mentor(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(hashtagNormalizer.normalize(any())).thenReturn(java.util.Set.of());
        when(feedPostRepository.save(any(FeedPost.class))).thenAnswer(inv -> {
            FeedPost p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });
        when(feedPostMapper.toResponse(any(FeedPost.class), any())).thenReturn(stubResponse(42L, 1L));

        feedPostService.create(1L, "Hello", List.of(), null);

        // Verify the facade is invoked with the saved post + author. The
        // FeedPostEventPublisherTest pins the event-shape contract; here
        // we pin the FeedPostService → facade contract.
        org.mockito.ArgumentCaptor<FeedPost> postCaptor =
                org.mockito.ArgumentCaptor.forClass(FeedPost.class);
        verify(feedPostEventPublisher).publishCreated(postCaptor.capture(), eq(author));
        assertThat(postCaptor.getValue().getId()).isEqualTo(42L);
    }

    @Test
    void create_doesNotPublish_whenSaveFails() {
        Mentor author = mentor(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(hashtagNormalizer.normalize(any())).thenReturn(java.util.Set.of());
        when(feedPostRepository.save(any(FeedPost.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> feedPostService.create(1L, "Hello", List.of(), null))
                .isInstanceOf(RuntimeException.class);

        verify(feedPostEventPublisher, never()).publishCreated(any(), any());
    }

    // ── getById ────────────────────────────────────────────────────────────

    @Test
    void getById_returnsDto_whenPostVisible() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(post));
        FeedPostResponse expected = stubResponse(7L, 1L);
        when(feedPostMapper.toResponse(post, 99L)).thenReturn(expected);

        FeedPostResponse result = feedPostService.getById(7L, 99L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getById_throws404_whenPostMissingOrDeleted() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.getById(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── update ─────────────────────────────────────────────────────────────

    @Test
    void update_throws404_whenPostMissing() {
        when(feedPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.update(404L, 1L, "new body", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_throws403_whenRequesterIsNotAuthor() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> feedPostService.update(7L, 999L, "hostile", null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void update_throws404_whenPostSoftDeleted() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.setDeletedAt(OffsetDateTime.now());
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> feedPostService.update(7L, 1L, "new", null, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void update_nullFields_doNotTouchExisting() {
        FeedPost post = freshPost(7L, 1L, "Original");
        OffsetDateTime origUpdated = post.getUpdatedAt();
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, null, null, null);

        // Both fields null = no modification = updatedAt unchanged
        assertThat(post.getBody()).isEqualTo("Original");
        assertThat(post.getUpdatedAt()).isEqualTo(origUpdated);
    }

    @Test
    void update_blankBodyWhenSupplied_throws400() {
        FeedPost post = freshPost(7L, 1L, "Original");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> feedPostService.update(7L, 1L, "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_replacesHashtagsWhenSupplied_andBumpsUpdatedAt() {
        FeedPost post = freshPost(7L, 1L, "Body");
        OffsetDateTime origUpdated = post.getUpdatedAt();
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(hashtagNormalizer.normalize(List.of("new"))).thenReturn(java.util.Set.of("new"));
        when(feedPostRepository.save(post)).thenReturn(post);
        when(feedPostMapper.toResponse(post, 1L)).thenReturn(stubResponse(7L, 1L));

        feedPostService.update(7L, 1L, null, List.of("new"), null);

        assertThat(post.getUpdatedAt()).isAfterOrEqualTo(origUpdated);
        // Hashtags were touched — the LinkedHashSet has the new tag.
        assertThat(post.getHashtags()).hasSize(1);
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_throws404_whenPostMissing() {
        when(feedPostRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedPostService.delete(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throws403_whenRequesterIsNotAuthor() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> feedPostService.delete(7L, 999L))
                .isInstanceOf(AccessDeniedException.class);

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void delete_idempotent_onAlreadySoftDeleted() {
        FeedPost post = freshPost(7L, 1L, "Body");
        post.setDeletedAt(OffsetDateTime.now());
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));

        feedPostService.delete(7L, 1L);  // no exception

        verify(feedPostRepository, never()).save(any(FeedPost.class));
    }

    @Test
    void delete_softDeletes_whenAuthor() {
        FeedPost post = freshPost(7L, 1L, "Body");
        when(feedPostRepository.findById(7L)).thenReturn(Optional.of(post));
        when(feedPostRepository.save(post)).thenReturn(post);

        feedPostService.delete(7L, 1L);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(feedPostRepository).save(post);
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

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

    private static FeedPostResponse stubResponse(Long id, Long authorId) {
        return new FeedPostResponse(id, authorId, "U" + authorId, "body", List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), false, true, List.of());
    }
}
