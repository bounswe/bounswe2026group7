package com.group7.backend.service;

import com.group7.backend.dto.response.FeedPostListItem;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.FollowingFeedRow;
import com.group7.backend.service.FeedInteractionService.PostCounts;
import com.group7.backend.service.ranking.FeedRanker;
import com.group7.backend.service.ranking.FeedScoreResult;
import com.group7.backend.service.ranking.feed.ForYouScoringPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
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
 * ranker, the interaction service (for batch counts), the mapper, and the
 * keyword-mute service that {@code mapPage} consults on every read path.
 *
 * <p>The two interesting branches in {@link FeedReadService#forYouFeed}
 * are the empty-candidate short-circuit and the legacy Schwartzian
 * transform path. The advanced {@link ForYouScoringPipeline} path is
 * covered separately by {@code AdvancedForYouFeedIntegrationTest}, which
 * stands up a real ranker; mocking the pipeline through here adds little
 * over what those integration tests already pin.
 *
 * <p>Search branches worth pinning at the unit level: every-filter-missing
 * (400), {@code since}/{@code until} window validation (400 when reversed,
 * 400 when outside the 10y past / 1d future window), {@code lang} pass-
 * through, hashtag-fails-normalisation empty page, and the keyword escape
 * + lowercase contract that protects against LIKE-pattern injection. The
 * date and lang filters arrived with #527 and weren't pinned by the
 * original PR #533 fixture; the missing coverage is filled in here.
 *
 * <p>Strictness is dialled down to LENIENT for one reason only: the
 * {@code keywordMuteService} stub in {@link #setUp} feeds the no-op
 * pass-through that every read path consults via {@code mapPage} →
 * {@code keywordMuteService.filter}. Forcing every test to re-stub it
 * just to keep STRICT_STUBS quiet would be noise — the pass-through is a
 * test-harness artefact, not behaviour under test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedReadServiceTest {

    @Mock private FeedPostRepository feedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;
    @Mock private HashtagNormalizer hashtagNormalizer;
    @Mock private FeedRanker feedRanker;
    @Mock private FeedInteractionService feedInteractionService;
    @Mock private FeedPostMapper feedPostMapper;
    @Mock private UserKeywordMuteService keywordMuteService;

    private FeedReadService service;

    @BeforeEach
    void setUp() {
        // No-op pass-through for the mute filter so happy-path tests don't
        // each need to redeclare it. The two muted-content paths are
        // exercised end-to-end by FeedReadIntegrationTest against real
        // Postgres + a real UserKeywordMuteService.
        when(keywordMuteService.filter(anyLong(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

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
                keywordMuteService,
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
        Page<FollowingFeedRow> empty = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(feedPostRepository.findFollowingFeed(eq(1L), any())).thenReturn(empty);

        Page<FeedPostListItem> page = service.followingFeed(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        // Empty page short-circuits the batch fetches the production path
        // would otherwise issue — keeps the cold cache cheap.
        verify(feedInteractionService, never()).batchCounts(any());
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void followingFeed_populatesFromProjection_originalPostBranch() {
        // One row from the original-post branch (sharedById null). The
        // service should batch-fetch the FeedPost (for hashtags), the
        // author user, and counts, then assemble the FeedPostListItem.
        FollowingFeedRow row = mock(FollowingFeedRow.class);
        when(row.getId()).thenReturn(101L);
        when(row.getAuthorId()).thenReturn(99L);
        when(row.getBody()).thenReturn("post body");
        Instant createdAt = Instant.parse("2026-05-10T10:00:00Z");
        when(row.getCreatedAt()).thenReturn(createdAt);
        when(row.getSharedById()).thenReturn(null);
        when(row.getShareCommentary()).thenReturn(null);
        when(row.getSharedAt()).thenReturn(null);

        Page<FollowingFeedRow> repoPage =
                new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1L);
        when(feedPostRepository.findFollowingFeed(eq(1L), any())).thenReturn(repoPage);

        FeedPost post = freshPost(101L, 99L);
        when(feedPostRepository.findAllById(any())).thenReturn(List.of(post));

        Mentor author = mentor(99L, "Ada");
        when(userRepository.findAllById(any())).thenReturn(List.of(author));

        when(feedInteractionService.batchCounts(any()))
                .thenReturn(Map.of(101L, new PostCounts(2L, 1L)));
        when(feedPostMapper.toSummaries(any())).thenReturn(List.of());

        Page<FeedPostListItem> page = service.followingFeed(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        FeedPostListItem item = page.getContent().get(0);
        assertThat(item.id()).isEqualTo(101L);
        assertThat(item.authorFirstName()).isEqualTo("Ada");
        assertThat(item.sharedById()).isNull();
        assertThat(item.likeCount()).isEqualTo(2L);
        assertThat(item.commentCount()).isEqualTo(1L);
    }

    @Test
    void followingFeed_repostBranch_threadsShareMetadataThroughDto() {
        // Same shape, but a repost row — sharedById, shareCommentary, and
        // sharedAt are populated. Verifies the share metadata survives the
        // projection → DTO mapping intact.
        FollowingFeedRow row = mock(FollowingFeedRow.class);
        when(row.getId()).thenReturn(101L);
        when(row.getAuthorId()).thenReturn(99L);
        when(row.getBody()).thenReturn("reposted body");
        when(row.getCreatedAt()).thenReturn(Instant.parse("2026-05-10T10:00:00Z"));
        when(row.getSharedById()).thenReturn(42L);
        when(row.getShareCommentary()).thenReturn("great take");
        when(row.getSharedAt()).thenReturn(Instant.parse("2026-05-11T12:00:00Z"));

        Page<FollowingFeedRow> repoPage =
                new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1L);
        when(feedPostRepository.findFollowingFeed(eq(1L), any())).thenReturn(repoPage);

        when(feedPostRepository.findAllById(any())).thenReturn(List.of(freshPost(101L, 99L)));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                mentor(99L, "Ada"), mentor(42L, "Bob")));
        when(feedInteractionService.batchCounts(any()))
                .thenReturn(Map.of(101L, new PostCounts(0L, 0L)));
        when(feedPostMapper.toSummaries(any())).thenReturn(List.of());

        Page<FeedPostListItem> page = service.followingFeed(1L, PageRequest.of(0, 10));

        FeedPostListItem item = page.getContent().get(0);
        assertThat(item.sharedById()).isEqualTo(42L);
        assertThat(item.sharedByFirstName()).isEqualTo("Bob");
        assertThat(item.shareCommentary()).isEqualTo("great take");
        assertThat(item.sharedAt()).isNotNull();
    }

    // ── postsByAuthor ──────────────────────────────────────────────────────

    @Test
    void postsByAuthor_delegatesToRepoAndMapper() {
        FeedPost post = freshPost(101L, 99L);
        Page<FeedPost> repoPage = new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1L);
        when(feedPostRepository.findByAuthorIdForFeed(eq(99L), any())).thenReturn(repoPage);
        when(feedInteractionService.batchCounts(List.of(101L))).thenReturn(
                Map.of(101L, new PostCounts(0L, 0L)));
        when(feedPostMapper.toListItems(eq(List.of(post)), eq(1L), any()))
                .thenReturn(List.of(listItem(101L, 99L)));

        Page<FeedPostListItem> page = service.postsByAuthor(99L, 1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(feedInteractionService).batchCounts(List.of(101L));
    }

    // ── search ─────────────────────────────────────────────────────────────

    @Test
    void search_throws400_whenEveryFilterIsNull() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");

        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_throws400_whenStringFiltersBlank_andTemporalAndLangNull() {
        // Blank strings are normalised to null inside the service; combined
        // with null since/until/lang, this reduces to the every-filter-null
        // case — same 400 path.
        assertThatThrownBy(() -> service.search("   ", "  ", null, null, null, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_returnsEmpty_whenHashtagFailsNormalisation() {
        // Normaliser returns an empty set for inputs that fail the hashtag
        // regex (e.g., spaces, punctuation). The service surfaces that as
        // an empty page rather than a noisy error.
        when(hashtagNormalizer.normalize(List.of("not a valid hashtag")))
                .thenReturn(Set.of());

        Page<FeedPostListItem> page = service.search(
                null, "not a valid hashtag", null, null, null, 1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_lowercasesAndEscapesKeyword_beforeRepoCall() {
        // %, _, and \ are LIKE metacharacters; escaping prevents a malicious
        // q=% from matching every post. Lowercasing aligns with the
        // pg_trgm GIN index on LOWER(body).
        when(feedPostRepository.searchPosts(eq("ja\\%va"), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search("Ja%va", null, null, null, null, 1L, PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq("ja\\%va"), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    void search_passesNormalisedHashtagToRepository() {
        when(hashtagNormalizer.normalize(List.of("#DataScience")))
                .thenReturn(Set.of("datascience"));
        when(feedPostRepository.searchPosts(eq(null), eq("datascience"), eq(null), eq(null), eq(null), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search(null, "#DataScience", null, null, null, 1L, PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq(null), eq("datascience"), eq(null), eq(null), eq(null), any());
    }

    @Test
    void search_passesLangFilterToRepository() {
        // lang on its own is enough to escape the every-filter-missing 400
        // guard, so this also pins the contract that lang is a first-class
        // search axis and not just a tie-breaker behind q/hashtag.
        when(feedPostRepository.searchPosts(eq(null), eq(null), eq(null), eq(null), eq("en"), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search(null, null, null, null, "en", 1L, PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq(null), eq(null), eq(null), eq(null), eq("en"), any());
    }

    @Test
    void search_throws400_whenSinceNotStrictlyBeforeUntil() {
        // Reversed window — service rejects rather than passing a
        // logically-empty range to the DB.
        OffsetDateTime later = OffsetDateTime.now().plusHours(1);
        OffsetDateTime earlier = OffsetDateTime.now().minusHours(1);

        assertThatThrownBy(() -> service.search(
                "x", null, later, earlier, null, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly before");

        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_throws400_whenSinceFurtherThan10YearsInPast() {
        OffsetDateTime tooOld = OffsetDateTime.now().minusYears(15);

        assertThatThrownBy(() -> service.search(
                "x", null, tooOld, null, null, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'since'");

        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_throws400_whenUntilFurtherThan1DayInFuture() {
        OffsetDateTime tooFar = OffsetDateTime.now().plusDays(30);

        assertThatThrownBy(() -> service.search(
                "x", null, null, tooFar, null, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'until'");

        verify(feedPostRepository, never()).searchPosts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void search_passesValidWindowToRepository() {
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);
        OffsetDateTime until = OffsetDateTime.now().plusHours(1);
        when(feedPostRepository.searchPosts(eq(null), eq(null), eq(since), eq(until), eq(null), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        service.search(null, null, since, until, null, 1L, PageRequest.of(0, 10));

        verify(feedPostRepository).searchPosts(eq(null), eq(null), eq(since), eq(until), eq(null), any());
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static Mentee viewer(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("U" + id);
        return m;
    }

    private static Mentor mentor(Long id, String firstName) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(firstName);
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

    /**
     * 14-arg FeedPostListItem — the four share-metadata fields default to
     * null because every test fixture here represents the non-repost
     * surface. Specific tests that need to assert repost-attribution
     * fields stub the projection directly rather than going through this
     * helper.
     */
    private static FeedPostListItem listItem(Long id, Long authorId) {
        return new FeedPostListItem(
                id, authorId, "U" + authorId, "body", List.of(),
                OffsetDateTime.now(), 0L, 0L, List.of(), List.of(),
                null, null, null, null);
    }
}
