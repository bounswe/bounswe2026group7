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

    /**
     * Tolerance (in seconds) between {@code createdAt} and {@code updatedAt}
     * before {@code isEdited} flips to {@code true}. Absorbs same-
     * transaction clock skew (the service sets {@code createdAt} and
     * {@code updatedAt} to the same value on create, but
     * {@code OffsetDateTime.now()} called twice in succession can differ
     * by sub-millisecond amounts that are still strictly increasing).
     */
    private static final long EDIT_TOLERANCE_SECONDS = 1L;

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
        boolean isEdited = post.getUpdatedAt() != null
                && post.getCreatedAt() != null
                && post.getUpdatedAt().isAfter(post.getCreatedAt().plusSeconds(EDIT_TOLERANCE_SECONDS));
        List<String> tags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
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

    // Visible to other components in the same package if they need to
    // resolve names without re-implementing the batch logic. Keep package-
    // private rather than public — exposes intent without bloating the
    // public surface.
    Map<Long, String> resolveNamesForCallers(Set<Long> authorIds) {
        return resolveAuthorNames(authorIds);
    }
}
