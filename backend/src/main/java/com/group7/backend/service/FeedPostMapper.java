package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.repository.FeedPostBookmarkRepository;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 * <p>Constructor-injects {@link AttachmentUrlBuilder} so every emitted
 * attachment URL — list or detail — routes through the single source of
 * truth and gets the feed-scoped path ({@code /api/uploads/feed-media/}).
 *
 * <p><b>Transactional context required.</b> Both methods touch the
 * lazy {@link FeedPost#getHashtags()} and
 * {@code FeedPost.getAttachments()} collections; calling either outside
 * an open {@code @Transactional} boundary will surface
 * {@code LazyInitializationException}. The convention across this
 * codebase is that DTO mapping happens inside the service-layer
 * transaction before the entity is returned to the controller.
 */
@Component
public class FeedPostMapper {

    private final UserRepository userRepository;
    private final AttachmentUrlBuilder attachmentUrlBuilder;
    private final FeedPostLikeRepository likeRepository;
    private final FeedPostBookmarkRepository bookmarkRepository;

    public FeedPostMapper(UserRepository userRepository,
                          AttachmentUrlBuilder attachmentUrlBuilder,
                          FeedPostLikeRepository likeRepository,
                          FeedPostBookmarkRepository bookmarkRepository) {
        this.userRepository = userRepository;
        this.attachmentUrlBuilder = attachmentUrlBuilder;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    /**
     * Single-post mapping. Resolves the author name with one
     * {@code findById} call and delegates to the list-shape helper so
     * the computation stays consistent across call sites.
     */
    public FeedPostResponse toResponse(FeedPost post, Long viewerId) {
        Map<Long, String> names = resolveAuthorNames(Set.of(post.getAuthorId()));
        Set<Long> postIds = Set.of(post.getId());
        Set<Long> liked = resolveLikedPostIds(viewerId, postIds);
        Set<Long> bookmarked = resolveBookmarkedPostIds(viewerId, postIds);
        return mapOne(post, viewerId, names, liked, bookmarked);
    }

    /**
     * List mapping. Collects all author ids first, does one batch
     * {@code findAllById} for the user lookup, then maps each post.
     * This is the design move that keeps {@code #350}'s list endpoints
     * free of N+1 reads. The attachments {@code @ManyToMany} is
     * {@code @BatchSize(100)}, so a page of 20 posts collapses to one
     * junction-join query rather than 20.
     */
    public List<FeedPostResponse> toResponses(List<FeedPost> posts, Long viewerId) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = posts.stream()
                .map(FeedPost::getAuthorId)
                .collect(Collectors.toSet());
        Set<Long> postIds = posts.stream()
                .map(FeedPost::getId)
                .collect(Collectors.toSet());
        Map<Long, String> names = resolveAuthorNames(authorIds);
        Set<Long> liked = resolveLikedPostIds(viewerId, postIds);
        Set<Long> bookmarked = resolveBookmarkedPostIds(viewerId, postIds);
        return posts.stream()
                .map(p -> mapOne(p, viewerId, names, liked, bookmarked))
                .toList();
    }

    /**
     * Slim list-item mapping for the feed read endpoints in {@code #350}.
     * Callers pre-compute the per-post like / comment counts via
     * {@link FeedInteractionService#batchCounts} (single round-trip per
     * interaction type — N+1-free), then hand them in here so DTO assembly
     * is a pure transformation. Attachments are read from the entity's
     * {@code @BatchSize(100)} collection, which Hibernate collapses into
     * one junction-join query per page.
     *
     * <p>If a post id is missing from {@code counts} or {@code factors}
     * (race between the page fetch and the count fetch, or a non-ranked
     * feed) we default to zero / empty rather than crashing the response.
     * Callers that want strict mapping can verify the maps cover every id
     * before calling.
     *
     * @param factors per-post ranker explanation codes. Pass {@link Map#of()}
     *                for non-ranked feeds (Following, search, profile posts);
     *                pass a populated map for ranked feeds (For-You) so the
     *                frontend can render the "why this post" chips.
     */
    public List<FeedPostListItem> toListItems(List<FeedPost> posts, Long viewerId,
                                              Map<Long, FeedInteractionService.PostCounts> counts,
                                              Map<Long, List<String>> factors) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = posts.stream()
                .map(FeedPost::getAuthorId)
                .collect(Collectors.toSet());
        Set<Long> postIds = posts.stream()
                .map(FeedPost::getId)
                .collect(Collectors.toSet());
        Map<Long, String> names = resolveAuthorNames(authorIds);
        Set<Long> liked = resolveLikedPostIds(viewerId, postIds);
        Set<Long> bookmarked = resolveBookmarkedPostIds(viewerId, postIds);
        return posts.stream()
                .map(p -> mapListItem(p, names, counts, factors, liked, bookmarked))
                .toList();
    }

    /** Convenience overload for non-ranked feeds — empty factors map. */
    public List<FeedPostListItem> toListItems(List<FeedPost> posts, Long viewerId,
                                              Map<Long, FeedInteractionService.PostCounts> counts) {
        return toListItems(posts, viewerId, counts, Map.of());
    }

    private Map<Long, String> resolveAuthorNames(Set<Long> authorIds) {
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> names.put(u.getId(), u.getFirstName()));
        return names;
    }

    /**
     * One batched lookup per page of posts. Empty set for anonymous reads
     * so the {@code viewerHasLiked} field always serialises to {@code false}
     * without a wasted DB round-trip. Public so the Following-feed read in
     * {@link FeedReadService} (which builds {@link FeedPostListItem}s
     * directly from a UNION-ALL projection rather than via
     * {@link #toListItems}) can share the same batch lookup.
     */
    public Set<Long> resolveLikedPostIds(Long viewerId, Set<Long> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(likeRepository.findLikedPostIdsByViewer(viewerId, postIds));
    }

    public Set<Long> resolveBookmarkedPostIds(Long viewerId, Set<Long> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(bookmarkRepository.findBookmarkedPostIdsByViewer(viewerId, postIds));
    }

    private FeedPostResponse mapOne(FeedPost post, Long viewerId, Map<Long, String> names,
                                     Set<Long> likedPostIds, Set<Long> bookmarkedPostIds) {
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
                isAuthor,
                toSummaries(post.getAttachments()),
                likedPostIds.contains(post.getId()),
                bookmarkedPostIds.contains(post.getId())
        );
    }

    private FeedPostListItem mapListItem(FeedPost post,
                                         Map<Long, String> names,
                                         Map<Long, FeedInteractionService.PostCounts> counts,
                                         Map<Long, List<String>> factors,
                                         Set<Long> likedPostIds,
                                         Set<Long> bookmarkedPostIds) {
        List<String> tags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .sorted()
                .toList();
        FeedInteractionService.PostCounts c =
                counts.getOrDefault(post.getId(), new FeedInteractionService.PostCounts(0L, 0L));
        return new FeedPostListItem(
                post.getId(),
                post.getAuthorId(),
                names.getOrDefault(post.getAuthorId(), null),
                post.getBody(),
                tags,
                post.getCreatedAt(),
                c.likeCount(),
                c.commentCount(),
                factors.getOrDefault(post.getId(), List.of()),
                toSummaries(post.getAttachments()),
                likedPostIds.contains(post.getId()),
                bookmarkedPostIds.contains(post.getId()),
                null,   // sharedById — not a repost surface for this mapper
                null,   // sharedByFirstName
                null,   // shareCommentary
                null    // sharedAt
        );
    }

    /**
     * Maps an ordered list of {@link Attachment} entities to their public
     * feed-media DTO shape. Returns an immutable empty list for the no-media
     * case so JSON consumers always see a stable type.
     */
    public List<AttachmentSummary> toSummaries(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> AttachmentSummary.of(a, attachmentUrlBuilder.feedMediaUrl(a.getId())))
                .toList();
    }
}
