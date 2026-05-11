package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.ranking.FeedRanker;
import com.group7.backend.service.ranking.FeedScoreResult;
import com.group7.backend.service.ranking.feed.ForYouScoringPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read service for the social-feed surfaces in #350 — For-You,
 * Following, and search. All three return paginated
 * {@link FeedPostListItem} so the UI can switch tabs without reshaping
 * the response.
 *
 * <p><b>For-You:</b> oversamples up to {@code app.feed.forYou.candidate-window}
 * recent candidates, ranks them in memory via the injected
 * {@link FeedRanker}, then slices the requested page from the ranking.
 * Pages beyond the candidate window return empty content. The
 * oversample-then-rank pattern mirrors {@code MatchingService}'s mentor
 * ranking; both pay one DB round trip + an in-memory sort to swap rank
 * algorithms behind {@code @Primary} without touching the read path.
 *
 * <p><b>Following:</b> chronological by {@code created_at DESC} via the
 * native repository query. No ranking; users explicitly opted into
 * these authors so the feed honors that without further filtering.
 *
 * <p><b>Search:</b> keyword + hashtag filter via the
 * {@code idx_feed_posts_body_trgm} GIN index (keyword) and the
 * {@code feed_post_hashtags} join (hashtag). Either filter can be null;
 * non-null filters AND together. The keyword is lowercased here so the
 * functional GIN index on {@code LOWER(body)} hits.
 *
 * <p>All three endpoints rely on {@link FeedPostMapper#toResponses} for
 * the batch author-name lookup (one {@code findAllById} per page, not
 * per-row) — the design move that keeps every list endpoint
 * N+1-free.
 */
@Service
@Transactional(readOnly = true)
public class FeedReadService {

    private static final Logger log = LoggerFactory.getLogger(FeedReadService.class);

    private final FeedPostRepository feedPostRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final HashtagNormalizer hashtagNormalizer;
    private final FeedRanker feedRanker;
    private final Optional<ForYouScoringPipeline> forYouPipeline;
    private final int candidateWindow;

    public FeedReadService(FeedPostRepository feedPostRepository,
                           UserRepository userRepository,
                           FollowRepository followRepository,
                           HashtagNormalizer hashtagNormalizer,
                           FeedRanker feedRanker,
                           Optional<ForYouScoringPipeline> forYouPipeline,
                           @Value("${app.feed.forYou.candidate-window:200}") int candidateWindow) {
        this.feedPostRepository = feedPostRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.hashtagNormalizer = hashtagNormalizer;
        this.feedRanker = feedRanker;
        this.forYouPipeline = forYouPipeline;
        this.candidateWindow = candidateWindow;
    }

    /**
     * For-You feed (#350, requirement 1.1.7.3). Oversample → rank → slice.
     * Returns the N highest-scoring posts in the candidate window for the
     * requested page. The total-element count reflects the candidate
     * window size, not the global post count, since pages beyond the
     * window deliberately return empty.
     */
    public Page<FeedPostListItem> forYouFeed(Long viewerId, Pageable pageable) {
        List<FeedPost> candidates =
                feedPostRepository.findForYouCandidates(viewerId, candidateWindow);
        if (candidates.isEmpty()) {
            return Page.empty(pageable);
        }

        // When the advanced ranker is wired in, route through the full
        // ForYouScoringPipeline (precompute → score → MMR → diversity
        // floor → bandit slots on page 0). Otherwise fall back to the
        // legacy Schwartzian-transform path on the single-shot
        // InterestOverlapFeedRanker; both produce the same Page<FeedPostListItem>
        // shape, so downstream callers are unaffected.
        if (forYouPipeline.isPresent()) {
            return slicePageFromPipeline(candidates, viewerId, pageable);
        }

        FeedRanker.FeedRankingContext context = buildRankingContext(viewerId);
        // Score once, sort once, slice once. Comparator.comparingInt re-runs
        // its key extractor on every compare(a, b), so a naive
        // .sorted(comparingInt(p -> ranker.score(p, ctx).score())) calls the
        // ranker O(N log N) times instead of N. Schwartzian transform fixes
        // that: materialise (FeedScoreResult, post) tuples once, sort by the
        // cached score, then unwrap. Cheap enough for our 200-candidate
        // window; if the window grows past a few thousand a partial-selection
        // (k-largest) is the next move.
        List<Scored> ranked = candidates.stream()
                .map(p -> new Scored(feedRanker.score(p, context), p))
                .sorted(Comparator.comparingInt((Scored s) -> s.result().score()).reversed())
                .toList();
        return slicePage(ranked, pageable);
    }

    /**
     * Advanced-path slice. Asks the pipeline for the already-paginated
     * ranked list (the pipeline handles MMR + diversity floor + bandit
     * internally) and maps each entry through {@code toListItem} with
     * its accumulated factor list.
     *
     * <p>Note: pipeline owns page slicing because the floor and bandit
     * are page-0 contracts — slicing before the floor would lose the
     * outsider candidate to draw from.
     */
    private Page<FeedPostListItem> slicePageFromPipeline(List<FeedPost> candidates,
                                                        Long viewerId,
                                                        Pageable pageable) {
        FeedRanker.FeedRankingContext context = buildRankingContext(viewerId);
        List<ForYouScoringPipeline.RankedFeedPost> ranked = forYouPipeline.get().rank(
                candidates,
                context.viewerId(),
                context.viewerInterestHashtags(),
                context.viewerFollowedAuthorIds(),
                context.now(),
                pageable.getPageSize(),
                pageable.getPageNumber());

        if (ranked.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, candidates.size());
        }
        Map<Long, String> authorNames = resolveAuthorNames(
                ranked.stream().map(ForYouScoringPipeline.RankedFeedPost::post).toList());
        List<FeedPostListItem> items = ranked.stream()
                .map(r -> toListItemFromRanked(r, authorNames))
                .toList();
        return new PageImpl<>(items, pageable, candidates.size());
    }

    private static FeedPostListItem toListItemFromRanked(ForYouScoringPipeline.RankedFeedPost ranked,
                                                        Map<Long, String> authorNames) {
        FeedPost post = ranked.post();
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
                0L,
                ranked.factors()
        );
    }

    /**
     * Following feed (#350, requirement 1.1.7.4). Chronological over
     * posts authored by users the viewer follows.
     */
    public Page<FeedPostListItem> followingFeed(Long viewerId, Pageable pageable) {
        Page<FeedPost> page = feedPostRepository.findFollowingFeed(viewerId, pageable);
        return mapPage(page);
    }

    /**
     * Author profile posts feed (#471). Chronological over posts
     * authored by the given user id.
     */
    public Page<FeedPostListItem> postsByAuthor(Long authorId, Pageable pageable) {
        Page<FeedPost> page = feedPostRepository.findByAuthorIdForFeed(authorId, pageable);
        return mapPage(page);
    }

    /**
     * Search (#350, requirement 1.1.7.7). Combined keyword + hashtag
     * filter. Either filter may be blank/null; both being blank returns
     * the full visible feed (paginated by recency).
     *
     * <p>Keyword is lowercased before the LIKE comparison so the
     * {@code idx_feed_posts_body_trgm} functional GIN index hits.
     * Hashtag is run through {@link HashtagNormalizer#normalize} so the
     * lookup matches stored values (lowercased, leading-{@code #}
     * stripped, regex-validated). A hashtag that fails normalisation
     * (e.g., contains spaces) returns no results — the search input is
     * silently treated as not matching anything rather than error.
     */
    public Page<FeedPostListItem> search(String keyword, String hashtag, Pageable pageable) {
        String normalisedKeyword = (keyword == null || keyword.isBlank())
                ? null
                : escapeLikePattern(keyword.trim().toLowerCase(Locale.ROOT));
        String normalisedHashtag = normaliseSingleHashtag(hashtag);
        if (hashtag != null && !hashtag.isBlank() && normalisedHashtag == null) {
            // The user supplied a hashtag that fails normalisation — return
            // empty rather than a noisy error. The UI can validate-locally
            // for a richer message.
            return Page.empty(pageable);
        }
        // Reject empty-filter search: returning the full visible feed via the
        // search endpoint is bug-magnet behaviour (clients fall back to /search
        // for "show me everything," which masks pagination cost growth as the
        // post count scales). The For-You and Following endpoints are the
        // correct surfaces for "no specific filter" reads.
        if (normalisedKeyword == null && normalisedHashtag == null) {
            throw new IllegalArgumentException(
                    "Search requires at least one of 'q' or 'hashtag' — use /api/feed/for-you or /api/feed/following for the full feed");
        }
        Page<FeedPost> page = feedPostRepository.searchPosts(normalisedKeyword, normalisedHashtag, pageable);
        return mapPage(page);
    }

    /**
     * Escapes the three LIKE metacharacters ({@code %}, {@code _},
     * {@code \}) so a user-supplied keyword can never act as a wildcard.
     * Without this, {@code q=%} would match every post and {@code q=_X}
     * would match every two-character body ending in X. Pattern injection
     * is not SQL injection (the parameter is still bound), but it does
     * give callers a way to read more than they should — escape at the
     * service boundary, paired with {@code ESCAPE '\\'} on the LIKE.
     *
     * <p>Order matters: {@code \\} must be replaced first so the
     * subsequent {@code %} → {@code \%} and {@code _} → {@code \_}
     * substitutions don't double-escape their own backslashes.
     */
    private static String escapeLikePattern(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** Single-hashtag normalisation that reuses the list normaliser. */
    private String normaliseSingleHashtag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> normalised = hashtagNormalizer.normalize(List.of(raw));
        return normalised.isEmpty() ? null : normalised.iterator().next();
    }

    private FeedRanker.FeedRankingContext buildRankingContext(Long viewerId) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + viewerId));

        Set<String> interestHashtags = extractInterestHashtags(viewer);

        // Unbounded followee-id fetch — one query, no pagination, no
        // ordering, no DTO. Earlier this used a paged Follow lookup with
        // PageRequest.of(0, 1000), which silently dropped the boost
        // signal for users following more than 1000 people.
        Set<Long> followedAuthorIds = followRepository.findFolloweeIdsByFollowerId(viewerId);

        return new FeedRanker.FeedRankingContext(
                viewerId, interestHashtags, followedAuthorIds, OffsetDateTime.now());
    }

    /**
     * Extracts the viewer's interest labels and runs them through the
     * hashtag normaliser so the comparison against stored post hashtags
     * is byte-for-byte. Interest labels may carry capitalisation and
     * spaces; normalisation drops anything that fails the hashtag regex
     * (e.g., multi-word interests like "Data Science"). Single-word
     * interests survive and align with hashtag conventions.
     */
    private Set<String> extractInterestHashtags(User viewer) {
        List<String> labels;
        if (viewer instanceof Mentee mentee) {
            labels = mentee.getInterests();
        } else if (viewer instanceof Mentor mentor) {
            labels = mentor.getInterests();
        } else {
            labels = null;
        }
        if (labels == null || labels.isEmpty()) {
            return Set.of();
        }
        return hashtagNormalizer.normalize(labels);
    }

    private Page<FeedPostListItem> mapPage(Page<FeedPost> page) {
        if (page.isEmpty()) {
            return Page.empty(page.getPageable());
        }
        Map<Long, String> authorNames = resolveAuthorNames(page.getContent());
        return page.map(p -> toListItem(p, authorNames, List.of()));
    }

    private Page<FeedPostListItem> slicePage(List<Scored> ranked, Pageable pageable) {
        int total = ranked.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        List<Scored> slice = ranked.subList(from, to);
        if (slice.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        List<FeedPost> posts = slice.stream().map(Scored::post).toList();
        Map<Long, String> authorNames = resolveAuthorNames(posts);
        List<FeedPostListItem> items = slice.stream()
                .map(s -> toListItem(s.post(), authorNames, s.result().factors()))
                .toList();
        return new PageImpl<>(items, pageable, total);
    }

    private Map<Long, String> resolveAuthorNames(List<FeedPost> posts) {
        Set<Long> ids = posts.stream().map(FeedPost::getAuthorId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> names.put(u.getId(), u.getFirstName()));
        return names;
    }

    private static FeedPostListItem toListItem(FeedPost post,
                                               Map<Long, String> authorNames,
                                               List<String> factors) {
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
                0L,
                factors
        );
    }

    /** Holds a feed post alongside its scoring result so the sort key is
     *  materialised exactly once per post (Schwartzian transform) and the
     *  factor list flows from scoring to the response without a second
     *  ranker pass. */
    private record Scored(FeedScoreResult result, FeedPost post) {}
}
