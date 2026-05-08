package com.group7.backend.service;

import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.dto.response.FeedPostInteractionState;
import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostBookmark;
import com.group7.backend.entity.FeedPostBookmarkId;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.FeedPostLike;
import com.group7.backend.entity.FeedPostLikeId;
import com.group7.backend.entity.FeedPostShare;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostBookmarkRepository;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FeedPostShareRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for the social-feed interaction surface (#347).
 *
 * <p>Like / bookmark are idempotent toggles: the toggle method's first
 * call inserts (returning {@code true} = now active), the second call
 * deletes (returning {@code false} = now inactive). The native
 * {@code INSERT ... ON CONFLICT DO NOTHING} keeps the create path
 * race-safe (mirrors {@code FollowService.follow}'s pattern from
 * #343); the delete path uses {@code deleteById} which is silent on
 * missing in Spring Data 3.x.
 *
 * <p>Comment edit / delete are author-only — non-author requests
 * surface 403 via {@link AccessDeniedException}. Soft-deleted
 * comments persist in the listing with a {@code [deleted]} placeholder
 * body so reply context survives.
 *
 * <p>Share is append-only — each call records a new event row.
 */
@Service
@Transactional(readOnly = true)
public class FeedInteractionService {

    private static final Logger log = LoggerFactory.getLogger(FeedInteractionService.class);

    private final FeedPostRepository feedPostRepository;
    private final FeedPostLikeRepository likeRepository;
    private final FeedPostBookmarkRepository bookmarkRepository;
    private final FeedPostShareRepository shareRepository;
    private final FeedPostCommentRepository commentRepository;
    private final UserRepository userRepository;

    public FeedInteractionService(FeedPostRepository feedPostRepository,
                                   FeedPostLikeRepository likeRepository,
                                   FeedPostBookmarkRepository bookmarkRepository,
                                   FeedPostShareRepository shareRepository,
                                   FeedPostCommentRepository commentRepository,
                                   UserRepository userRepository) {
        this.feedPostRepository = feedPostRepository;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.shareRepository = shareRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    /**
     * Idempotent like toggle. Returns the new state ({@code true} =
     * now liked, {@code false} = now unliked). 404 if the post is
     * missing or soft-deleted.
     */
    @Transactional
    public FeedPostInteractionState toggleLike(Long postId, Long userId) {
        requireVisiblePost(postId);
        FeedPostLikeId id = new FeedPostLikeId(postId, userId);
        boolean nowLiked;
        if (likeRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            likeRepository.deleteById(id);
            nowLiked = false;
        } else {
            likeRepository.upsertLike(postId, userId);
            nowLiked = true;
        }
        log.info("Toggle like: postId={}, userId={}, nowLiked={}", postId, userId, nowLiked);
        return interactionState(postId, userId);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @Transactional
    public FeedPostInteractionState toggleBookmark(Long postId, Long userId) {
        requireVisiblePost(postId);
        FeedPostBookmarkId id = new FeedPostBookmarkId(postId, userId);
        boolean nowBookmarked;
        if (bookmarkRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            bookmarkRepository.deleteById(id);
            nowBookmarked = false;
        } else {
            bookmarkRepository.upsertBookmark(postId, userId);
            nowBookmarked = true;
        }
        log.info("Toggle bookmark: postId={}, userId={}, nowBookmarked={}",
                postId, userId, nowBookmarked);
        return interactionState(postId, userId);
    }

    public Page<FeedPostListItem> listBookmarks(Long userId, Pageable pageable) {
        Page<Long> postIds = bookmarkRepository.findBookmarkedPostIdsByUser(userId, pageable);
        if (postIds.isEmpty()) {
            return Page.empty(pageable);
        }
        // Preserve bookmark-recency order from the repo query.
        List<Long> orderedIds = postIds.getContent();
        List<FeedPost> posts = feedPostRepository.findAllById(orderedIds);
        Map<Long, FeedPost> byId = posts.stream()
                .collect(Collectors.toMap(FeedPost::getId, p -> p));
        Map<Long, String> authorNames = resolveAuthorNames(posts);
        List<FeedPostListItem> items = orderedIds.stream()
                .map(byId::get)
                .filter(p -> p != null && p.getDeletedAt() == null)
                .map(p -> toListItem(p, authorNames))
                .toList();
        return new PageImpl<>(items, pageable, postIds.getTotalElements());
    }

    // ── Shares ─────────────────────────────────────────────────────────────

    @Transactional
    public FeedPostInteractionState recordShare(Long postId, Long sharerId) {
        requireVisiblePost(postId);
        shareRepository.save(new FeedPostShare(postId, sharerId));
        log.info("Recorded share: postId={}, sharerId={}", postId, sharerId);
        return interactionState(postId, sharerId);
    }

    // ── Comments ───────────────────────────────────────────────────────────

    @Transactional
    public FeedCommentResponse addComment(Long postId, Long authorId, String body) {
        requireVisiblePost(postId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body must not be blank");
        }
        FeedPostComment comment = new FeedPostComment(postId, authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        FeedPostComment saved = commentRepository.save(comment);
        log.info("Created comment: id={}, postId={}, authorId={}", saved.getId(), postId, authorId);
        return mapComment(saved, authorId, resolveAuthorName(authorId));
    }

    public Page<FeedCommentResponse> listComments(Long postId, Long viewerId, Pageable pageable) {
        requireVisiblePost(postId);
        Page<FeedPostComment> page = commentRepository
                .findByPostIdOrderByCreatedAtAscIdAsc(postId, pageable);
        if (page.isEmpty()) {
            return Page.empty(pageable);
        }
        Set<Long> authorIds = page.getContent().stream()
                .map(FeedPostComment::getAuthorId).collect(Collectors.toSet());
        Map<Long, String> names = resolveAuthorNamesByIds(authorIds);
        return page.map(c -> mapComment(c, viewerId, names.get(c.getAuthorId())));
    }

    @Transactional
    public FeedCommentResponse editComment(Long commentId, Long requesterId, String body) {
        FeedPostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        if (!comment.getAuthorId().equals(requesterId)) {
            throw new AccessDeniedException("Only the comment author can edit");
        }
        if (comment.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Comment not found with id: " + commentId);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body must not be blank");
        }
        comment.setBody(body);
        comment.setUpdatedAt(OffsetDateTime.now());
        FeedPostComment saved = commentRepository.save(comment);
        return mapComment(saved, requesterId, resolveAuthorName(requesterId));
    }

    @Transactional
    public void deleteComment(Long commentId, Long requesterId) {
        FeedPostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        if (!comment.getAuthorId().equals(requesterId)) {
            throw new AccessDeniedException("Only the comment author can delete");
        }
        if (comment.getDeletedAt() != null) {
            return;
        }
        comment.setDeletedAt(OffsetDateTime.now());
        commentRepository.save(comment);
        log.info("Soft-deleted comment: id={}, authorId={}", commentId, requesterId);
    }

    // ── Aggregate state ────────────────────────────────────────────────────

    public FeedPostInteractionState interactionState(Long postId, Long viewerId) {
        long likes = likeRepository.countByIdPostId(postId);
        long bookmarks = bookmarkRepository.countByIdPostId(postId);
        long shares = shareRepository.countByPostId(postId);
        long comments = commentRepository.countByPostIdAndDeletedAtIsNull(postId);
        boolean liked = viewerId != null
                && likeRepository.existsByIdPostIdAndIdUserId(postId, viewerId);
        boolean bookmarked = viewerId != null
                && bookmarkRepository.existsByIdPostIdAndIdUserId(postId, viewerId);
        return new FeedPostInteractionState(
                likes, comments, shares, bookmarks, liked, bookmarked);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void requireVisiblePost(Long postId) {
        feedPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
    }

    private FeedCommentResponse mapComment(FeedPostComment c, Long viewerId, String authorName) {
        boolean isAuthor = viewerId != null && viewerId.equals(c.getAuthorId());
        boolean isDeleted = c.getDeletedAt() != null;
        boolean isEdited = c.getUpdatedAt() != null && c.getCreatedAt() != null
                && c.getUpdatedAt().isAfter(c.getCreatedAt());
        return new FeedCommentResponse(
                c.getId(),
                c.getPostId(),
                isDeleted ? null : c.getAuthorId(),
                isDeleted ? null : authorName,
                isDeleted ? null : c.getBody(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                isEdited,
                isAuthor,
                isDeleted
        );
    }

    private String resolveAuthorName(Long authorId) {
        return userRepository.findById(authorId)
                .map(User::getFirstName)
                .orElse(null);
    }

    private Map<Long, String> resolveAuthorNamesByIds(Set<Long> authorIds) {
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> names.put(u.getId(), u.getFirstName()));
        return names;
    }

    private Map<Long, String> resolveAuthorNames(List<FeedPost> posts) {
        Set<Long> ids = posts.stream().map(FeedPost::getAuthorId).collect(Collectors.toSet());
        return resolveAuthorNamesByIds(ids);
    }

    private static FeedPostListItem toListItem(FeedPost post, Map<Long, String> authorNames) {
        List<String> tags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .sorted()
                .toList();
        return new FeedPostListItem(
                post.getId(),
                post.getAuthorId(),
                authorNames.getOrDefault(post.getAuthorId(), null),
                post.getBody(),
                tags,
                post.getCreatedAt(),
                0L,
                0L
        );
    }
}
