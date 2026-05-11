package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostEditEntry;
import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostEditHistory;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AttachmentRepository;
import com.group7.backend.repository.FeedPostEditHistoryRepository;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

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

    /**
     * Hard cap on attachments per post. Mirrored at the request DTO
     * layer ({@code @Size(max = 4)}) and at the DB layer (junction CHECK
     * {@code position BETWEEN 0 AND 3}). Defense in depth: the DTO bound
     * fails fast on user input; the service constant catches programmatic
     * callers; the DB CHECK is the final invariant.
     */
    private static final int MAX_ATTACHMENTS_PER_POST = 4;

    /**
     * Feed-only image content-type allow-list. The shared upload pipeline
     * accepts PDF / DOCX / text for chat, but the feed surface restricts to
     * statically-renderable image types — animated / video / document media
     * is out of scope for v1 per the issue spec.
     */
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * Hard cap on the {@code limit} accepted by {@link #getPostHistory}.
     * Mirrored by an {@code @Max} on the controller {@code @RequestParam}
     * so abusive callers get a 400 long before the service is reached;
     * the service-side clamp is defence-in-depth.
     */
    static final int HISTORY_PAGE_CAP = 50;

    private final FeedPostRepository feedPostRepository;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final HashtagNormalizer hashtagNormalizer;
    private final FeedPostMapper feedPostMapper;
    private final FeedPostEventPublisher feedPostEventPublisher;
    private final FeedPostEditHistoryRepository historyRepository;

    public FeedPostService(FeedPostRepository feedPostRepository,
                           UserRepository userRepository,
                           AttachmentRepository attachmentRepository,
                           HashtagNormalizer hashtagNormalizer,
                           FeedPostMapper feedPostMapper,
                           FeedPostEventPublisher feedPostEventPublisher,
                           FeedPostEditHistoryRepository historyRepository) {
        this.feedPostRepository = feedPostRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.hashtagNormalizer = hashtagNormalizer;
        this.feedPostMapper = feedPostMapper;
        this.feedPostEventPublisher = feedPostEventPublisher;
        this.historyRepository = historyRepository;
    }

    /**
     * Creates a fresh post on behalf of {@code authorId}. Rejects
     * {@link Admin} requesters with {@link AccessDeniedException}.
     * Validates body non-blank (defensive — DTO {@code @NotBlank} and
     * DB CHECK are backstops), normalises hashtags, attaches any supplied
     * image attachments after their provenance + content-type checks pass,
     * persists, and returns the DTO mapped inside this transaction.
     *
     * @param attachmentIds optional image-attachment UUIDs (0-4). Each must
     *                      be owned by {@code authorId} and have an image
     *                      content type. Null or empty creates a text-only
     *                      post.
     */
    @Transactional
    public FeedPostResponse create(Long authorId, String body, List<String> rawHashtags,
                                    List<UUID> attachmentIds) {
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
        List<Attachment> resolvedAttachments = resolveAttachments(attachmentIds, authorId);

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
        if (!resolvedAttachments.isEmpty()) {
            // @OrderColumn writes positions 0..n in iteration order on flush.
            // The list is empty at this point — adding to a fresh ArrayList is
            // the only mutation the @ManyToMany ever sees on the create path,
            // so the @OrderColumn-fragile partial-mutation case cannot arise.
            saved.getAttachments().addAll(resolvedAttachments);
        }
        // Cascade=PERSIST flushes the hashtag children, and the dirty
        // @ManyToMany collection writes the junction rows, at @Transactional
        // commit.

        log.info("Created feed post: id={}, authorId={}, hashtags={}, attachments={}",
                saved.getId(), authorId, normalisedTags.size(), resolvedAttachments.size());

        // Publish inside the @Transactional boundary so AFTER_COMMIT
        // delivery in FeedFanoutListener is bound to a real commit (#349).
        // Spring's TransactionSynchronizationManager queues the event and
        // delivers only on successful commit; rollback discards it.
        feedPostEventPublisher.publishCreated(saved, author);

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
     *
     * <p>{@code attachmentIds == null} leaves the existing attachment list
     * unchanged; an empty list removes all attachments; a non-empty list
     * fully replaces the set in the supplied order.
     */
    @Transactional
    public FeedPostResponse update(Long postId, Long requesterId,
                                    String body, List<String> rawHashtags,
                                    List<UUID> attachmentIds) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
        assertAuthor(post, requesterId);
        if (post.getDeletedAt() != null) {
            // Deleted posts are not editable — same 404 surface as GET
            // on a deleted post. (The author check fires first so a
            // non-author still gets 403 even on a deleted post.)
            throw new ResourceNotFoundException("Feed post not found with id: " + postId);
        }

        // Pre-compute the normalised target hashtag set (if supplied)
        // up front so the snapshot decision can compare set membership
        // against what we are actually about to write — not the raw
        // unparsed input the caller sent.
        Set<String> normalisedTags = rawHashtags == null
                ? null
                : hashtagNormalizer.normalize(rawHashtags);
        Set<String> currentTags = post.getHashtags().stream()
                .map(h -> h.getId().getTag())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        boolean bodyChanges = body != null && !body.isBlank() && !body.equals(post.getBody());
        boolean hashtagsChange = normalisedTags != null
                && !currentTags.equals(new TreeSet<>(normalisedTags));

        // Snapshot the prior state BEFORE mutating (#487). The history row
        // is written inside the same @Transactional boundary as the post
        // mutation; if the UPDATE later rolls back (optimistic-lock 409,
        // FK cascade race, etc.) the INSERT rolls back with it. A no-op
        // PATCH (caller sent the existing values, or both fields null)
        // produces no history row.
        if (bodyChanges || hashtagsChange) {
            FeedPostEditHistory snapshot = new FeedPostEditHistory();
            snapshot.setPostId(post.getId());
            snapshot.setEditorId(requesterId);
            snapshot.setPreviousBody(post.getBody());
            snapshot.setPreviousHashtags(List.copyOf(currentTags));
            snapshot.setEditedAt(OffsetDateTime.now());
            historyRepository.save(snapshot);
        }

        boolean modified = false;
        if (body != null) {
            if (body.isBlank()) {
                throw new IllegalArgumentException("body must not be blank when supplied");
            }
            post.setBody(body);
            modified = true;
        }
        if (normalisedTags != null) {
            post.getHashtags().clear();
            for (String tag : normalisedTags) {
                post.getHashtags().add(new FeedPostHashtag(post, tag));
            }
            modified = true;
        }
        if (attachmentIds != null) {
            // Resolve + authorise BEFORE mutating the collection — if any id
            // fails the provenance or content-type gate we abort with a 4xx
            // before the @OrderColumn delete/re-insert begins.
            List<Attachment> resolved = resolveAttachments(attachmentIds, requesterId);
            // @OrderColumn-driven @ManyToMany: only clear() + addAll()
            // preserves the position invariant. Partial mutations
            // ({@code list.set}, {@code list.remove(int)}) de-sync the column.
            post.getAttachments().clear();
            post.getAttachments().addAll(resolved);
            modified = true;
        }
        if (modified) {
            post.setUpdatedAt(OffsetDateTime.now());
        }
        FeedPost saved = feedPostRepository.save(post);
        log.info("Updated feed post: id={}, authorId={}, bodyTouched={}, hashtagsTouched={}, attachmentsTouched={}, historySaved={}",
                postId, requesterId, body != null, rawHashtags != null, attachmentIds != null,
                bodyChanges || hashtagsChange);
        return feedPostMapper.toResponse(saved, requesterId);
    }

    /**
     * Returns the edit history of a feed post (#487), newest first.
     * Visible to the post author and to any {@link Admin}; everyone
     * else gets {@code 403}. Operates regardless of the post's
     * {@code deletedAt} state — a soft-deleted post still has a
     * history that the author or an admin can audit before the
     * cleanup scheduler hard-deletes it.
     *
     * <p>The {@code limit} is clamped to {@link #HISTORY_PAGE_CAP}
     * inside the service so any caller bypassing the controller's
     * {@code @Max} cannot flood the response.
     */
    public List<FeedPostEditEntry> getPostHistory(Long postId, Long actorId, int limit) {
        FeedPost post = feedPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed post not found with id: " + postId));
        if (!post.getAuthorId().equals(actorId)) {
            User actor = userRepository.findById(actorId)
                    .orElseThrow(() -> new AccessDeniedException("Only the post author or an admin can view history"));
            if (!(actor instanceof Admin)) {
                log.warn("Non-author non-admin attempted to read feed-post history: postId={}, actorId={}",
                        postId, actorId);
                throw new AccessDeniedException("Only the post author or an admin can view history");
            }
        }
        int clampedLimit = Math.min(Math.max(limit, 1), HISTORY_PAGE_CAP);
        return historyRepository
                .findByPostIdOrderByEditedAtDesc(postId, PageRequest.of(0, clampedLimit))
                .stream()
                .map(h -> new FeedPostEditEntry(
                        h.getId(), h.getEditorId(), h.getPreviousBody(),
                        h.getPreviousHashtags(), h.getEditedAt()))
                .toList();
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

    /**
     * Resolves an optional list of attachment ids into managed entities for
     * the create / update paths, applying size + duplicate + provenance +
     * content-type checks in that order. Returns an empty list for the
     * {@code null} / empty case so callers can pass the result to
     * {@code addAll} unconditionally.
     *
     * <p>"Already attached to another post" is intentionally NOT pre-checked
     * here — the DB UNIQUE on {@code feed_post_attachments(attachment_id)}
     * is the single source of truth and surfaces as
     * {@code DataIntegrityViolationException} → 409 via the global handler.
     * A service-level pre-check would race with concurrent PATCHes and add
     * an extra SELECT per id for no integrity gain.
     *
     * @throws IllegalArgumentException   list exceeds the max, contains
     *                                    duplicates, or an attachment has a
     *                                    non-image content type (400)
     * @throws ResourceNotFoundException  an id does not resolve to an
     *                                    attachment row (404)
     * @throws AccessDeniedException      an attachment was uploaded by
     *                                    someone other than the author (403)
     */
    private List<Attachment> resolveAttachments(List<UUID> attachmentIds, Long authorId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_POST) {
            throw new IllegalArgumentException(
                    "A post can carry at most " + MAX_ATTACHMENTS_PER_POST + " attachments");
        }
        if (new HashSet<>(attachmentIds).size() != attachmentIds.size()) {
            throw new IllegalArgumentException("Duplicate attachment ids in request");
        }
        List<Attachment> resolved = new ArrayList<>(attachmentIds.size());
        for (UUID id : attachmentIds) {
            resolved.add(loadAndAuthorizeFeedAttachment(id, authorId));
        }
        return resolved;
    }

    /**
     * Resolves and authorises a single attachment id at post create / update
     * time. Mirrors the {@code MessageService} provenance gate ("only the
     * uploader can attach") so the same invariant holds across chat and
     * feed, and adds the feed-only image content-type allow-list.
     */
    private Attachment loadAndAuthorizeFeedAttachment(UUID attachmentId, Long authorId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        Long uploaderId = attachment.getUploader() != null ? attachment.getUploader().getId() : null;
        if (!Objects.equals(uploaderId, authorId)) {
            log.warn("Non-uploader attempted to attach to feed post: attachmentId={}, requesterId={}, uploaderId={}",
                    attachmentId, authorId, uploaderId);
            throw new AccessDeniedException("You may only attach files you uploaded yourself");
        }
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(attachment.getContentType())) {
            throw new IllegalArgumentException(
                    "Feed attachments must be one of image/jpeg, image/png, image/gif, image/webp "
                            + "(got: " + attachment.getContentType() + ")");
        }
        return attachment;
    }
}
