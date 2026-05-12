package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentee;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.FeedInteractionService.PostCounts;
import com.group7.backend.service.ranking.FeedRanker;
import com.group7.backend.service.ranking.FeedScoreResult;
import com.group7.backend.service.ranking.feed.ForYouScoringPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedReadService}. Mocks the repositories, the
 * ranker, the interaction service (for batch counts), and the mapper.
 *
 * <p>The two interesting branches in {@link FeedReadService#forYouFeed}
 * are the empty-candidate short-circuit and the legacy Schwartzian
 * transform path. The advanced {@link ForYouScoringPipeline} path is
 * covered separately by {@code AdvancedForYouFeedIntegrationTest}, which
 * stands up a real ranker; mocking the pipeline through here adds little
 * over what those integration tests already pin.
 *
 * <p>Search has three branches worth pinning: both filters missing (400),
 * a hashtag that fails normalisation (empty page rather than error), and
 * the happy keyword + hashtag path that routes through the repository
 * with the normalised values.
 */
@ExtendWith(MockitoExtension.class)
class FeedReadServiceTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;
    @Mock private HashtagNormalizer hashtagNormalizer;
    @Mock private FeedRanker feedRanker;
    @Mock private FeedInteractionService feedInteractionService;
    @Mock private FeedPostMapper feedPostMapper;

    private FeedReadService service;

    @BeforeEach
    void setUp() {
        // Pipeline absent → service uses the legacy Schwartzian path.
        // AdvancedForYouFeedIntegrationTest stands up the pipeline against
        // real beans, so we don't duplicate that coverage here.
        service = new FeedReadService(
                feedPostRepository,
                userRepository,
                followRepository,
                hashtagNormalizer,
                feedRanker,
                feedInteractionService,
                Optional.empty(),
                feedPostMapper,
                200);
    }

    // ── forYouFeed ─────────────────────────────────────────────────────────

    @Test
    void forYouFeed_emptyCandidates_returnsEmptyPageWithoutRanking() {
        when(feedPostRepository.findForYouCandidates(1L, 200)).thenReturn(List.of());

        Page<FeedPostListItem> page = service.forYouFeed(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        // No ranker call when the candidate set is empty — the cost of
        // building the FeedRankingContext is also avoided.
        verify(feedRanker, never()).score(any(), any());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void forYouFeed_rankedByScore_descending() {
        FeedPost p1 = freshPost(101L, 99L);
        FeedPost p2 = freshPost(102L, 98L);
        FeedPost p3 = freshPost(103L, 97L);
        when(feedPostRepository.findForYouCandidates(1L, 200)).thenReturn(List.of(p1, p2, p3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer(1L)));
        when(followRepository.findFolloweeIdsByFollowerId(1L)).thenReturn(Set.of());
        // Mentee's getInterests() is null on a fresh fixture, so
        // extractInterestHashtags short-circuits before reaching the
        // normaliser — no stub needed for hashtagNormalizer here.
        // Scores descend p2 > p1 > p3, so the mapper should see them in
        // that order even though the repo returned the original [p1,p2,p3].
        when(feedRanker.score(eq(p1), any())).thenReturn(new FeedScoreResult(50, List.of()));
        when(feedRanker.score(eq(p2), any())).thenReturn(new FeedScoreResult(80, List.of()));
        when(feedRanker.score(eq(p3), any())).thenReturn(new FeedScoreResult(30, List.of()));
        when(feedInteractionService.batchCounts(any())).thenReturn(Map.of());
        when(feedPostMapper.toListItems(any(), eq(1L), any(), any())).thenAnswer(inv -> {
            List<FeedPost> posts = inv.getArgument(0);
            return posts.stream()
                    .map(p -> listItem(p.getId(), p.getAuthorId()))
                    .toList();
        });

        Page<FeedPostListItem> page = service.forYouFeed(1L, PageRequest.of(0, 10));

        // Schwartzian transform: scored once, sorted by score descending.
        assertThat(page.getContent()).extracting(FeedPostListItem::id)
                .containsExactly(102L, 101L, 103L);
    }

    // ── followingFeed ──────────────────────────────────────────────────────

    @Test
    void followingFeed_emptyPage_shortCircuits() {
        when(feedPostRepository.findFollowingFeed(eq(1L), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        Page<FeedPostListItem> page = service.followingFeed(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        // Empty page short-circuits the batch-counts + mapper invocations.
        verify(feedInteractionService, never()).batchCounts(any());
        verify(feedPostMapper, never()).toListItems(any(), any(), any());
    }

    @Test
    void followingFeed_populatesItemsThroughMapper() {
        FeedPost post = freshPost(101L, 99L);
        Page<FeedPost> repoPage = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1L);
        when(feedPostRepository.findFollowingFeed(eq(1L), any())).thenReturn(repoPage);
        when(feedInteractionService.batchCounts(List.of(101L))).thenReturn(
                Map.of(101L, new PostCounts(0L, 0L)));
        when(feedPostMapper.toListItems(eq(List.of(post)), eq(1L), any()))
                .thenReturn(List.of(listItem(101L, 99L)));

        Page<FeedPostListItem> page = service.followingFeed(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent()).hasSize(1);
        verify(feedInteractionService).batchCounts(List.of(101L));
    }

    // ── postsByAuthor ──────────────────────────────────────────────────────

    @Test
    void postsByAuthor_delegatesToRepoAndMapper() {
        FeedPost post = freshPost(101L, 99L);
        Page<FeedPost> repoPage = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1L);
        when(feedPostRepository.findByAuthorIdForFeed(eq(99L), any())).thenReturn(repoPage);
        when(feedInteractionService.batchCounts(List.of(101L))).thenReturn(
                Map.of(101L, new PostCounts(0L, 0L)));
        when(feedPostMapper.toListItems(eq(List.of(post)), eq(null), any()))
                .thenReturn(List.of(listItem(101L, 99L)));

        Page<FeedPostListItem> page = service.postsByAuthor(99L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    // ── search ─────────────────────────────────────────────────────────────

    @Test
    void search_throws400_whenBothFiltersAreNull() {
        assertThatThrownBy(() -> service.search(null, null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");

        verify(feedPostRepository, never()).searchPosts(any(), any(), any());
    }

    @Test
    void search_throws400_whenBothFiltersAreBlank() {
        assertThatThrownBy(() -> service.search("   ", "  ", PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(feedPostRepository, never()).searchPosts(any(), any(), any());
    }

    @Test
    void search_returnsEmpty_whenHashtagFailsNormalisation() {
        // Normaliser returns an empty set for inputs that fail the hashtag
        // regex (e.g., spaces, punctuation). The service surfaces that as
        // an empty page rather than a noisy error.
        when(hashtagNormalizer.normalize(List.of("not a valid hashtag")))
                .thenReturn(Set.of());

        Page<FeedPostListItem> page = service.search(null, "not a valid hashtag", PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        verify(feedPostRepository, never()).searchPosts(any(), any(), any());
    }

    @Test
    void search_lowercasesAndEscapesKeyword_beforeRepoCall() {
        // %, _, and \ are LIKE metacharacters; escaping prevents a malicious
        // q=% from matching every post. Lowercasing aligns with the
        // pg_trgm GIN index on LOWER(body).
        when(feedPostRepository.searchPosts(eq("ja\\%va"), eq(null), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search("Ja%va", null, PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq("ja\\%va"), eq(null), any());
    }

    @Test
    void search_passesNormalisedHashtagToRepository() {
        when(hashtagNormalizer.normalize(List.of("#DataScience")))
                .thenReturn(Set.of("datascience"));
        when(feedPostRepository.searchPosts(eq(null), eq("datascience"), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search(null, "#DataScience", PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq(null), eq("datascience"), any());
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static Mentee viewer(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("U" + id);
        return m;
    }

    private static FeedPost freshPost(Long id, Long authorId) {
        FeedPost p = new FeedPost(authorId, "body-" + id);
        p.setId(id);
        OffsetDateTime now = OffsetDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }

    private static FeedPostListItem listItem(Long id, Long authorId) {
        return new FeedPostListItem(
                id, authorId, "U" + authorId, "body", List.of(),
                OffsetDateTime.now(), 0L, 0L, List.of(), List.of());
    }
}
