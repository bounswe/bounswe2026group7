package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@link FeedPost} entities into {@link FeedPostResponse} DTOs (#348).
 *
 * <p>Constructor-injects {@link UserRepository} so the author display name
 * can be resolved alongside the post in a way that scales: the
 * single-post path ({@link #toResponse}) and the list path
 * ({@link #toResponses}) share one batch-resolution helper, which
 * downstream feed reads in {@code #350} reuse to keep their list
 * endpoints N+1-free.
 *
 * <p><b>Transactional context required.</b> Both methods touch the
 * lazy {@link FeedPost#getHashtags()} collection; calling either
 * outside an open {@code @Transactional} boundary will surface
 * {@code LazyInitializationException}. The convention across this
 * codebase is that DTO mapping happens inside the service-layer
 * transaction before the entity is returned to the controller.
 */
@Component
public class FeedPostMapper {

    private final UserRepository userRepository;

    public FeedPostMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Single-post mapping. Resolves the author name with one
     * {@code findById} call and delegates to the list-shape helper so
     * the computation stays consistent across call sites.
     */
    public FeedPostResponse toResponse(FeedPost post, Long viewerId) {
        Map<Long, String> names = resolveAuthorNames(Set.of(post.getAuthorId()));
        return mapOne(post, viewerId, names);
    }

    /**
     * List mapping. Collects all author ids first, does one batch
     * {@code findAllById} for the user lookup, then maps each post.
     * This is the design move that keeps {@code #350}'s list endpoints
     * free of N+1 reads.
     */
    public List<FeedPostResponse> toResponses(List<FeedPost> posts, Long viewerId) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = posts.stream()
                .map(FeedPost::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, String> names = resolveAuthorNames(authorIds);
        return posts.stream()
                .map(p -> mapOne(p, viewerId, names))
                .toList();
    }

    private Map<Long, String> resolveAuthorNames(Set<Long> authorIds) {
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> names.put(u.getId(), u.getFirstName()));
        return names;
    }

    private static FeedPostResponse mapOne(FeedPost post, Long viewerId, Map<Long, String> names) {
        boolean isAuthor = viewerId != null && viewerId.equals(post.getAuthorId());
        // Strict isAfter: the service explicitly sets createdAt and
        // updatedAt to the exact same OffsetDateTime instance on create,
        // so equality holds for fresh posts. Any later PATCH calls
        // OffsetDateTime.now() afresh, which is guaranteed to be after
        // createdAt because System.nanoTime is monotonic on every
        // supported platform. No tolerance needed.
        boolean isEdited = post.getUpdatedAt() != null
                && post.getCreatedAt() != null
                && post.getUpdatedAt().isAfter(post.getCreatedAt());
        // Always alphabetical in the DTO. The entity's @OrderBy("id.tag ASC")
        // already returns reads in this order, but the create / update paths
        // build the in-memory collection in insertion order; sorting in the
        // mapper guarantees the DTO is identical whether the entity was
        // freshly built (insertion-ordered LinkedHashSet) or reloaded from
        // the DB (alphabetical via @OrderBy). One contract, one path.
        List<String> tags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .sorted()
                .toList();
        return new FeedPostResponse(
                post.getId(),
                post.getAuthorId(),
                names.getOrDefault(post.getAuthorId(), null),
                post.getBody(),
                tags,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                isEdited,
                isAuthor
        );
    }

}
