package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedPostMapper} (#348). Exercises the single
 * and list mapping paths plus the {@code isAuthor} / {@code isEdited}
 * computations that the service relies on.
 */
@ExtendWith(MockitoExtension.class)
class FeedPostMapperTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private FeedPostMapper feedPostMapper;

    @Test
    void toResponse_setsIsAuthorTrue_whenViewerIsAuthor() {
        Mentor author = mentor(1L, "Alice");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

        FeedPost post = freshPost(42L, 1L, "Hello", List.of("data"));

        FeedPostResponse response = feedPostMapper.toResponse(post, 1L);

        assertThat(response.isAuthor()).isTrue();
        assertThat(response.authorFirstName()).isEqualTo("Alice");
        assertThat(response.body()).isEqualTo("Hello");
        assertThat(response.hashtags()).containsExactly("data");
        assertThat(response.isEdited()).isFalse();  // updatedAt == createdAt on fresh post
    }

    @Test
    void toResponse_setsIsAuthorFalse_whenViewerIsDifferent() {
        Mentor author = mentor(1L, "Alice");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

        FeedPost post = freshPost(42L, 1L, "Hello", List.of());

        FeedPostResponse response = feedPostMapper.toResponse(post, 99L);

        assertThat(response.isAuthor()).isFalse();
    }

    @Test
    void toResponse_setsIsAuthorFalse_whenViewerIsNull() {
        Mentor author = mentor(1L, "Alice");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

        FeedPost post = freshPost(42L, 1L, "Hello", List.of());

        FeedPostResponse response = feedPostMapper.toResponse(post, null);

        assertThat(response.isAuthor()).isFalse();
    }

    @Test
    void toResponse_setsIsEditedTrue_whenUpdatedAtAfterCreatedAt() {
        Mentor author = mentor(1L, "Alice");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

        FeedPost post = freshPost(42L, 1L, "Hello", List.of());
        post.setUpdatedAt(post.getCreatedAt().plusSeconds(5));

        FeedPostResponse response = feedPostMapper.toResponse(post, 1L);

        assertThat(response.isEdited()).isTrue();
    }

    @Test
    void toResponse_alphabeticalHashtagOrder_regardlessOfInsertionOrder() {
        Mentor author = mentor(1L, "Alice");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(author));

        // Insert in non-alphabetical order; mapper sorts to alphabetical.
        FeedPost post = freshPost(42L, 1L, "Hello", List.of("zebra", "apple", "mango"));

        FeedPostResponse response = feedPostMapper.toResponse(post, 1L);

        assertThat(response.hashtags()).containsExactly("apple", "mango", "zebra");
    }

    @Test
    void toResponse_authorFirstNameNull_whenUserNotFound() {
        // Empty findAllById return — author got deleted between load and map.
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        FeedPost post = freshPost(42L, 1L, "Hello", List.of());

        FeedPostResponse response = feedPostMapper.toResponse(post, 1L);

        assertThat(response.authorFirstName()).isNull();
        assertThat(response.authorId()).isEqualTo(1L);
    }

    @Test
    void toResponses_listPath_batchesAuthorLookup() {
        Mentor a = mentor(1L, "Alice");
        Mentor b = mentor(2L, "Bob");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(a, b));

        FeedPost p1 = freshPost(10L, 1L, "by A", List.of("x"));
        FeedPost p2 = freshPost(20L, 2L, "by B", List.of("y"));
        FeedPost p3 = freshPost(30L, 1L, "by A again", List.of());

        List<FeedPostResponse> responses = feedPostMapper.toResponses(List.of(p1, p2, p3), 99L);

        assertThat(responses).hasSize(3);
        assertThat(responses).extracting(FeedPostResponse::authorFirstName)
                .containsExactly("Alice", "Bob", "Alice");
        // Single batch lookup despite 3 posts → no N+1.
        verify(userRepository, times(1)).findAllById(anyIterable());
    }

    @Test
    void toResponses_emptyInput_returnsEmptyList_withoutDbCall() {
        List<FeedPostResponse> responses = feedPostMapper.toResponses(List.of(), 1L);

        assertThat(responses).isEmpty();
        verify(userRepository, times(0)).findAllById(anyIterable());
    }

    @Test
    void toResponses_nullInput_returnsEmptyList() {
        List<FeedPostResponse> responses = feedPostMapper.toResponses(null, 1L);

        assertThat(responses).isEmpty();
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static Mentor mentor(Long id, String firstName) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(firstName);
        return m;
    }

    private static FeedPost freshPost(Long id, Long authorId, String body, List<String> tags) {
        FeedPost post = new FeedPost(authorId, body);
        post.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        LinkedHashSet<FeedPostHashtag> set = new LinkedHashSet<>();
        for (String tag : tags) {
            set.add(new FeedPostHashtag(post, tag));
        }
        post.setHashtags(set);
        return post;
    }
}
