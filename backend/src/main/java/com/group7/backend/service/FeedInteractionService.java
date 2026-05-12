package com.group7.backend.service;

import com.group7.backend.dto.request.CreateRepostRequest;
import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.dto.response.FeedPostInteractionState;
import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostBookmarkId;
import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.entity.FeedPostLikeId;
import com.group7.backend.entity.FeedPostShare;
import com.group7.backend.entity.User;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.event.FeedEngagementEvent;
import com.group7.backend.event.FeedPostSharedEvent;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostBookmarkRepository;
import com.group7.backend.repository.FeedPostCommentRepository;
import com.group7.backend.repository.FeedPostLikeRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FeedPostShareRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.PostCountTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Per-post like/comment counts assembled by {@link #batchCounts}.
     * Tightly scoped to the read fan-out path; not a wire DTO. The two
     * fields mirror {@code FeedPostListItem.likeCount} /
     * {@code commentCount} positions so the caller can splat them
     * straight into the record constructor.
     */
    public record PostCounts(long likeCount, long commentCount) {}

    private static final Logger log = LoggerFactory.getLogger(FeedInteractionService.class);

    private final FeedPostRepository feedPostRepository;
    private final FeedPostLikeRepository likeRepository;
    private final FeedPostBookmarkRepository bookmarkRepository;
    private final FeedPostShareRepository shareRepository;
    private final FeedPostCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FeedPostMapper feedPostMapper;
    private final NotificationEventPublisher notificationEventPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Window during which an identical-payload repost from the same
     * sharer collapses to the existing row instead of creating a
     * duplicate. Defends against network retries causing double-fanout;
     * not a defence against simultaneous-click races. Configurable for
     * ops tuning, ISO 8601 duration syntax.
     */
    private final Duration repostIdempotencyWindow;

    public FeedInteractionService(FeedPostRepository feedPostRepository,
                                   FeedPostLikeRepository likeRepository,
                                   FeedPostBookmarkRepository bookmarkRepository,
                                   FeedPostShareRepository shareRepository,
                                   FeedPostCommentRepository commentRepository,
                                   UserRepository userRepository,
                                   FeedPostMapper feedPostMapper,
                                   NotificationEventPublisher notificationEventPublisher,
                                   ApplicationEventPublisher eventPublisher,
                                   @Value("${app.feed.repost.idempotency-window:PT60S}")
                                   Duration repostIdempotencyWindow) {
        this.feedPostRepository = feedPostRepository;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.shareRepository = shareRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.feedPostMapper = feedPostMapper;
        this.notificationEventPublisher = notificationEventPublisher;
        this.eventPublisher = eventPublisher;
        this.repostIdempotencyWindow = repostIdempotencyWindow;
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    /**
     * Idempotent like toggle. Returns the post's full interaction state
     * after applying the toggle. 404 if the post is missing or
     * soft-deleted.
     *
     * <p><b>Concurrency caveat — rapid double-click.</b> The toggle is
     * not transactionally atomic across the {@code existsBy*} read and
     * the subsequent insert/delete: under sub-millisecond contention
     * (the user double-clicks, both requests race in parallel), both
     * reads can see the same prior state and both can attempt the same
     * action. The {@code INSERT ... ON CONFLICT DO NOTHING} keeps the
     * row count consistent; the {@code deleteById} is silent on missing.
     * Net effect: the final DB state matches whichever click won the
     * race; the second-arriving response reports the state the racing
     * call already established. Frontend debouncing on the like button
     * is the recommended mitigation; a true atomic toggle would need a
     * stored procedure or a single-row {@code UPSERT ... DO UPDATE} on
     * a boolean — neither is justified at v1.
     */
    @Transactional
    public FeedPostInteractionState toggleLike(Long postId, Long userId) {
        FeedPost post = requireVisiblePost(postId);
        FeedPostLikeId id = new FeedPostLikeId(postId, userId);
        boolean nowLiked;
        if (likeRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            likeRepository.deleteById(id);
            nowLiked = false;
        } else {
            likeRepository.upsertLike(postId, userId);
            nowLiked = true;
            publishEngagement(post, userId);
        }
        log.info("Toggle like: postId={}, userId={}, nowLiked={}", postId, userId, nowLiked);
        if (nowLiked && !userId.equals(post.getAuthorId())) {
            notificationEventPublisher.publishFeedLike(
                    post.getAuthorId(), resolveAuthorName(userId), postId);
        }
        return interactionState(postId, userId);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Idempotent bookmark toggle. Same shape and concurrency caveat as
     * {@link #toggleLike}: under rapid double-click contention the second
     * response reports the state the racing call already established.
     * Frontend debouncing is the recommended mitigation.
     */
    @Transactional
    public FeedPostInteractionState toggleBookmark(Long postId, Long userId) {
        FeedPost post = requireVisiblePost(postId);
        FeedPostBookmarkId id = new FeedPostBookmarkId(postId, userId);
        boolean nowBookmarked;
        if (bookmarkRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            bookmarkRepository.deleteById(id);
            nowBookmarked = false;
        } else {
            bookmarkRepository.upsertBookmark(postId, userId);
            nowBookmarked = true;
            publishEngagement(post, userId);
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
        // Preserve bookmark-recency order from the repo query — findAllById
        // returns rows in indeterminate order, so re-sort via id-keyed map.
        List<Long> orderedIds = postIds.getContent();
        Map<Long, FeedPost> byId = feedPostRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(FeedPost::getId, p -> p));
        List<FeedPost> ordered = orderedIds.stream()
                .map(byId::get)
                .filter(p -> p != null && p.getDeletedAt() == null)
                .toList();
        Map<Long, String> authorNames = new HashMap<>();
        userRepository.findAllById(ordered.stream().map(FeedPost::getAuthorId)
                .collect(Collectors.toSet()))
                .forEach(u -> authorNames.put(u.getId(), u.getFirstName()));
        Map<Long, PostCounts> counts = batchCounts(
                ordered.stream().map(FeedPost::getId).toList());
        List<FeedPostListItem> items = ordered.stream().map(p -> {
            PostCounts c = counts.get(p.getId());
            long likeCount = (c == null) ? 0L : c.likeCount();
            long commentCount = (c == null) ? 0L : c.commentCount();
            return new FeedPostListItem(
                    p.getId(),
                    p.getAuthorId(),
                    authorNames.getOrDefault(p.getAuthorId(), null),
                    p.getBody(),
                    p.getHashtags().stream().map(h -> h.getId().getTag()).sorted().toList(),
                    p.getCreatedAt(),
                    likeCount,
                    commentCount,
                    List.of(),
                    null,   // sharedById — bookmarks are user-scoped, not a repost surface
                    null,   // sharedByFirstName
                    null,   // shareCommentary
                    null    // sharedAt
            );
        }).toList();
        return new PageImpl<>(items, pageable, postIds.getTotalElements());
    }

    /**
     * Aggregate like / visible-comment counts across many posts in a
     * single round-trip per interaction type. Used by the feed list
     * endpoints to avoid an N+1 fan-out when populating
     * {@code FeedPostListItem.likeCount} / {@code commentCount}.
     *
     * <p>Contract: the returned map contains an entry for <b>every</b>
     * postId supplied — posts with zero likes / zero visible comments
     * surface as {@code new PostCounts(0L, 0L)} rather than being absent.
     * Callers can therefore index directly without {@code getOrDefault}.
     *
     * <p>Short-circuits on an empty input: Postgres rejects
     * {@code WHERE id IN ()}, so an empty {@code postIds} returns
     * {@code Map.of()} before any SQL is issued.
     */
    public Map<Long, PostCounts> batchCounts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> likeCounts = likeRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostCountTuple::postId, PostCountTuple::count));
        Map<Long, Long> commentCounts = commentRepository.countVisibleByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostCountTuple::postId, PostCountTuple::count));
        Map<Long, PostCounts> result = new LinkedHashMap<>(postIds.size());
        for (Long id : postIds) {
            result.put(id, new PostCounts(
                    likeCounts.getOrDefault(id, 0L),
                    commentCounts.getOrDefault(id, 0L)));
        }
        return result;
    }

    // ── Shares ─────────────────────────────────────────────────────────────

    @Transactional
    public FeedPostInteractionState recordShare(Long postId, Long sharerId) {
        FeedPost post = requireVisiblePost(postId);
        shareRepository.save(new FeedPostShare(postId, sharerId));
        publishEngagement(post, sharerId);
        log.info("Recorded share: postId={}, sharerId={}", postId, sharerId);
        if (!sharerId.equals(post.getAuthorId())) {
            notificationEventPublisher.publishFeedShare(
                    post.getAuthorId(), resolveAuthorName(sharerId), postId);
        }
        return interactionState(postId, sharerId);
    }

    /**
     * Repost or quote-share a post. Distinct from {@link #recordShare}:
     * the row is written with {@code is_repost = TRUE} and the share
     * fans out via STOMP to the sharer's followers. The original post
     * surfaces in those followers' Following feeds with "shared by X"
     * attribution.
     *
     * <p>Idempotency is enforced at the service layer with a sliding
     * {@link #repostIdempotencyWindow}: an identical
     * {@code (post_id, sharer_id, body)} write inside the window
     * collapses to the existing row, returning the same interaction
     * state without re-firing fanout or notification. NULL bodies match
     * NULL bodies via Postgres' {@code IS NOT DISTINCT FROM}.
     *
     * <p>The race where two simultaneous identical requests both pass
     * the idempotency check is a known v1 limitation; both rows insert
     * and both fanouts fire. Realistic client retries are serialised by
     * the network round-trip, so the window is enough in practice.
     */
    @Transactional
    public FeedPostInteractionState recordRepost(Long postId, Long sharerId,
                                                  CreateRepostRequest request) {
        FeedPost post = requireVisiblePost(postId);
        String body = blankToNull(request != null ? request.body() : null);

        Optional<FeedPostShare> recent = shareRepository.findRecentRepost(
                postId, sharerId, body,
                OffsetDateTime.now().minus(repostIdempotencyWindow));
        if (recent.isPresent()) {
            log.info("Repost idempotent collapse: postId={}, sharerId={}, withinSec={}",
                    postId, sharerId, repostIdempotencyWindow.toSeconds());
            return interactionState(postId, sharerId);
        }

        FeedPostShare share = new FeedPostShare(postId, sharerId);
        share.setBody(body);
        share.setRepost(true);
        FeedPostShare saved = shareRepository.save(share);

        String sharerFirstName = resolveAuthorName(sharerId);

        // FeedPostShare.createdAt is insertable=false, so the entity field is
        // NULL right after save() until a refresh. Use the JVM clock — same
        // transaction, sub-millisecond drift from the DB DEFAULT NOW(), and
        // STOMP fanout ordering does not depend on the exact persisted
        // microsecond.
        eventPublisher.publishEvent(new FeedPostSharedEvent(
                saved.getId(),
                saved.getPostId(),
                saved.getSharerId(),
                sharerFirstName,
                saved.getBody(),
                OffsetDateTime.now()));

        publishEngagement(post, sharerId);
        log.info("Recorded repost: postId={}, sharerId={}, hasCommentary={}",
                postId, sharerId, body != null);

        if (!sharerId.equals(post.getAuthorId())) {
            notificationEventPublisher.publishFeedShare(
                    post.getAuthorId(), sharerFirstName, postId);
        }
        return interactionState(postId, sharerId);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ── Comments ───────────────────────────────────────────────────────────

    @Transactional
    public FeedCommentResponse addComment(Long postId, Long authorId, String body) {
        FeedPost post = requireVisiblePost(postId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body must not be blank");
        }
        FeedPostComment comment = new FeedPostComment(postId, authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        FeedPostComment saved = commentRepository.save(comment);
        publishEngagement(post, authorId);
        log.info("Created comment: id={}, postId={}, authorId={}", saved.getId(), postId, authorId);
        String actorFirstName = resolveAuthorName(authorId);
        if (!authorId.equals(post.getAuthorId())) {
            notificationEventPublisher.publishFeedComment(
                    post.getAuthorId(), actorFirstName, postId);
        }
        return mapComment(saved, authorId, actorFirstName);
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

    /**
     * Public read of the interaction state for a single post (#347).
     * Companion to {@code GET /api/feed/posts/{id}} — returns counts +
     * viewer-relative toggles so the UI can render the post detail
     * fully in one fetch pair, without having to toggle to learn the
     * current state.
     *
     * <p>404 if the post is missing or soft-deleted (uniform with
     * {@code FeedPostService.getById}).
     */
    public FeedPostInteractionState getInteractionState(Long postId, Long viewerId) {
        requireVisiblePost(postId);
        return interactionState(postId, viewerId);
    }

    /**
     * Internal read used by the toggle / share endpoints to decorate
     * their responses. Skips the visibility check because the calling
     * mutation already validated the post exists; calling this directly
     * for an invisible post would silently return zero counts, which
     * isn't what any caller wants.
     */
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

    private FeedPost requireVisiblePost(Long postId) {
        return feedPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
    }

    /**
     * Publishes a {@link FeedEngagementEvent} for the bandit α-update
     * trampoline. Called only from positive-engagement (insert) branches;
     * toggle-off paths must NOT publish (without β updates a
     * like-then-unlike would otherwise double-credit α).
     *
     * <p>The event payload carries the post's normalized hashtag set so
     * the listener doesn't need to re-load the post in its own
     * transaction. Hashtags are read inside the calling {@code @Transactional}
     * method so the LAZY collection populates before commit; the listener
     * receives a defensive copy via the event's compact constructor. The
     * caller passes the post entity from its own {@code requireVisiblePost}
     * call so the bandit hook doesn't re-issue a SELECT.
     */
    private void publishEngagement(FeedPost post, Long viewerId) {
        if (post == null) return;
        Set<String> hashtags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .collect(Collectors.toSet());
        if (!hashtags.isEmpty()) {
            eventPublisher.publishEvent(new FeedEngagementEvent(viewerId, hashtags));
        }
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
}
