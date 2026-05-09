package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Service surface for the social-feed posts core (#348).
 *
 * <p>Exposes the four CRUD operations the controller needs:
 * {@link #create}, {@link #getById}, {@link #update}, {@link #delete}.
 * All four resolve through the {@link FeedPostMapper} inside the
 * {@code @Transactional} boundary so the lazy {@code hashtags}
 * collection is materialised before return — calling the mapper after
 * the service method returns would surface
 * {@code LazyInitializationException}.
 *
 * <h2>Concurrency</h2>
 * <ul>
 *   <li>{@code @Version} on {@link FeedPost} catches concurrent PATCH
 *       and PATCH-vs-DELETE races; the second writer surfaces
 *       {@code ObjectOptimisticLockingFailureException} →
 *       {@code GlobalExceptionHandler.handleConcurrencyFailure} → 409.
 *       The plan does <b>not</b> attempt to recover inside the
 *       service: catching the exception inside {@code @Transactional}
 *       cannot do useful work because Spring marks the transaction
 *       rollback-only, and any in-method recovery hits
 *       {@code UnexpectedRollbackException} on commit. The retry-friendly
 *       path is the client's: a second DELETE re-loads, sees
 *       {@code deletedAt != null}, and returns the idempotent 204.</li>
 *   <li>Cascade race (user deleted while a post is being PATCHed):
 *       the FK {@code ON DELETE CASCADE} reaps the post; the in-flight
 *       PATCH's version-aware {@code UPDATE} updates 0 rows; Hibernate
 *       surfaces {@code ObjectOptimisticLockingFailureException} → 409.</li>
 * </ul>
 *
 * <h2>Authorisation</h2>
 * <ul>
 *   <li>Create: the requester must not be an {@link Admin}. Mirrors
 *       the project's existing pattern in {@code UserService.getProfileById}
 *       (admin profiles are not visible). Admins are not feed-graph
 *       participants.</li>
 *   <li>Update / delete: the requester must be the post's author. Any
 *       other authenticated user is a 403 via
 *       {@link AccessDeniedException}.</li>
 *   <li>Banned-user gating is upstream (the {@code #280} ban surface;
 *       banned users can't authenticate, so they don't reach the
 *       controller). The service does not double-check.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FeedPostService {

    private static final Logger log = LoggerFactory.getLogger(FeedPostService.class);

    private final FeedPostRepository feedPostRepository;
    private final UserRepository userRepository;
    private final HashtagNormalizer hashtagNormalizer;
    private final FeedPostMapper feedPostMapper;

    public FeedPostService(FeedPostRepository feedPostRepository,
                           UserRepository userRepository,
                           HashtagNormalizer hashtagNormalizer,
                           FeedPostMapper feedPostMapper) {
        this.feedPostRepository = feedPostRepository;
        this.userRepository = userRepository;
        this.hashtagNormalizer = hashtagNormalizer;
        this.feedPostMapper = feedPostMapper;
    }

    /**
     * Creates a fresh post on behalf of {@code authorId}. Rejects
     * {@link Admin} requesters with {@link AccessDeniedException}.
     * Validates body non-blank (defensive — DTO {@code @NotBlank} and
     * DB CHECK are backstops), normalises hashtags, persists, and
     * returns the DTO mapped inside this transaction.
     */
    @Transactional
    public FeedPostResponse create(Long authorId, String body, List<String> rawHashtags) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + authorId));
        if (author instanceof Admin) {
            log.warn("Admin attempted to create feed post: authorId={}", authorId);
            throw new AccessDeniedException("Admins cannot create feed posts");
        }
        if (body == null || body.isBlank()) {
            // Defensive — DTO @NotBlank should have caught this.
            throw new IllegalArgumentException("body must not be blank");
        }
        Set<String> normalisedTags = hashtagNormalizer.normalize(rawHashtags);

        FeedPost post = new FeedPost(authorId, body);
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        // Two-phase persist: parent first so its id is generated, then
        // children built with the now-known parent id (FeedPostHashtag
        // uses @MapsId on the @ManyToOne back-reference).
        FeedPost saved = feedPostRepository.save(post);
        for (String tag : normalisedTags) {
            saved.getHashtags().add(new FeedPostHashtag(saved, tag));
        }
        // Cascade=PERSIST flushes the children at @Transactional commit.

        log.info("Created feed post: id={}, authorId={}, hashtags={}",
                saved.getId(), authorId, normalisedTags.size());

        return feedPostMapper.toResponse(saved, authorId);
    }

    /**
     * Public-visibility lookup. Returns 404 for non-existent or
     * soft-deleted posts (uniform — even the author cannot see a
     * soft-deleted post on this surface; the bookmarked-history use
     * case is owned by {@code #347}).
     */
    public FeedPostResponse getById(Long postId, Long viewerId) {
        FeedPost post = feedPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
        return feedPostMapper.toResponse(post, viewerId);
    }

    /**
     * Author-only partial update. Loads via raw {@code findById} so the
     * service can distinguish 403 (non-author) from 404 (deleted /
     * missing) cleanly. {@code null} fields on the request are not
     * touched (the codebase's partial-update convention).
     */
    @Transactional
    public FeedPostResponse update(Long postId, Long requesterId,
                                    String body, List<String> rawHashtags) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
        assertAuthor(post, requesterId);
        if (post.getDeletedAt() != null) {
            // Deleted posts are not editable — same 404 surface as GET
            // on a deleted post. (The author check fires first so a
            // non-author still gets 403 even on a deleted post.)
            throw new ResourceNotFoundException("Feed post not found with id: " + postId);
        }

        boolean modified = false;
        if (body != null) {
            if (body.isBlank()) {
                throw new IllegalArgumentException("body must not be blank when supplied");
            }
            post.setBody(body);
            modified = true;
        }
        if (rawHashtags != null) {
            Set<String> normalisedTags = hashtagNormalizer.normalize(rawHashtags);
            post.getHashtags().clear();
            for (String tag : normalisedTags) {
                post.getHashtags().add(new FeedPostHashtag(post, tag));
            }
            modified = true;
        }
        if (modified) {
            post.setUpdatedAt(OffsetDateTime.now());
        }
        FeedPost saved = feedPostRepository.save(post);
        log.info("Updated feed post: id={}, authorId={}, bodyTouched={}, hashtagsTouched={}",
                postId, requesterId, body != null, rawHashtags != null);
        return feedPostMapper.toResponse(saved, requesterId);
    }

    /**
     * Author-only soft-delete. Loads via raw {@code findById} so the
     * service can short-circuit on already-deleted (idempotent 204)
     * before doing any mutation. Concurrent calls that race may surface
     * {@code ObjectOptimisticLockingFailureException} → 409; the client
     * retry recovers idempotently because the second pass sees
     * {@code deletedAt != null} and returns 204.
     */
    @Transactional
    public void delete(Long postId, Long requesterId) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
        assertAuthor(post, requesterId);
        if (post.getDeletedAt() != null) {
            // Idempotent — already soft-deleted.
            return;
        }
        post.setDeletedAt(OffsetDateTime.now());
        feedPostRepository.save(post);
        log.info("Soft-deleted feed post: id={}, authorId={}", postId, requesterId);
    }

    private static void assertAuthor(FeedPost post, Long requesterId) {
        if (!post.getAuthorId().equals(requesterId)) {
            log.warn("Non-author attempted to mutate feed post: postId={}, requesterId={}, authorId={}",
                    post.getId(), requesterId, post.getAuthorId());
            throw new AccessDeniedException("Only the post author can perform this action");
        }
    }
}
