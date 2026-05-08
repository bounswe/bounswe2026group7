package com.group7.backend.service;

import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.ranking.RuleBasedFollowRanker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-level orchestration tests for {@link FollowRecommendationService}.
 * Uses a real {@link RuleBasedFollowRanker} (mirroring
 * {@code MatchingServiceTest}'s shape) so the orchestration assertions
 * observe actual scoring outputs without mocking the ranker.
 */
@ExtendWith(MockitoExtension.class)
class FollowRecommendationServiceTest {

    private static final int INTEREST_WEIGHT = 3;
    private static final int FOLLOW_GRAPH_WEIGHT = 2;
    private static final int RANKING_WINDOW = 200;
    private static final Long VIEWER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private FollowRepository followRepository;

    private FollowRecommendationService service;
    private Pageable firstPage;

    @BeforeEach
    void setUp() {
        service = new FollowRecommendationService(
                userRepository, followRepository,
                new RuleBasedFollowRanker(INTEREST_WEIGHT, FOLLOW_GRAPH_WEIGHT),
                RANKING_WINDOW);
        firstPage = PageRequest.of(0, 20);

        // Sane defaults for the empty-graph viewer; individual tests
        // override the followee page when they want a non-empty graph.
        lenient().when(followRepository
                        .findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(followRepository.findByIdFollowerIdIn(anyCollection()))
                .thenReturn(List.of());
    }

    // ── Errors ──────────────────────────────────────────────────────────────

    @Test
    void viewerMissing_throws404() {
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend(VIEWER_ID, firstPage))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void noCandidates_returnsEmptyPage() {
        Mentee viewer = mentee(VIEWER_ID, List.of("ai"));
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        // No second-hop query when the candidate query returned nothing —
        // but the service still made the followee fetch (cheap, single
        // index hit) before checking the candidate set.
        verify(followRepository, never()).findByIdFollowerIdIn(anyCollection());
    }

    // ── Scoring & ordering ─────────────────────────────────────────────────

    @Test
    void candidatesAreSortedByScoreDescThenIdDesc() {
        Mentee viewer = mentee(VIEWER_ID, List.of("AI", "Java"));
        Mentor c10 = mentor(10L, List.of("AI"));            // 1 overlap × 3 = 3
        Mentor c20 = mentor(20L, List.of("AI", "Java"));    // 2 overlaps × 3 = 6
        Mentor c30 = mentor(30L, List.of());                // score 0
        Mentor c40 = mentor(40L, List.of("Java"));          // score 3 — same as c10

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c10, c20, c30, c40));

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        // Expected order: c20 (6) → c40 (3, id=40) → c10 (3, id=10) → c30 (0)
        assertThat(result.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactly(20L, 40L, 10L, 30L);
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void emptyInterests_stillReturnsCandidates_orderedByIdDesc() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        Mentor c10 = mentor(10L, List.of("AI"));
        Mentor c20 = mentor(20L, List.of("Java"));

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c10, c20));

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        // Both score 0, tied → id-DESC tiebreaker → c20 before c10
        assertThat(result.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactly(20L, 10L);
        assertThat(result.getContent())
                .allSatisfy(r -> assertThat(r.getScore()).isZero());
    }

    @Test
    void interestsCaseInsensitive_lowercasedBeforeRanking() {
        // Viewer's labels are "AI" and "Databases"; the ranker receives
        // them lowercased, candidate's labels are unchanged in factor
        // strings.
        Mentee viewer = mentee(VIEWER_ID, List.of("AI", "Databases"));
        Mentor candidate = mentor(10L, List.of("ai", "DATABASES"));

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        FollowRecommendationResponse only = result.getContent().get(0);
        assertThat(only.getScore()).isEqualTo(2 * INTEREST_WEIGHT);
        assertThat(only.getFactors())
                .containsExactly("shared-interest:ai", "shared-interest:DATABASES");
    }

    // ── Follow-graph (second-hop) ──────────────────────────────────────────

    @Test
    void secondHopCount_creditsCandidatesFollowedByMyFollows() {
        // viewer follows 100, 101
        // 100 follows 200; 101 follows 200, 300
        // → second-hop: 200 → 2, 300 → 1
        Mentee viewer = mentee(VIEWER_ID, List.of());
        Mentor c200 = mentor(200L, List.of());
        Mentor c300 = mentor(300L, List.of());

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        followEdge(VIEWER_ID, 100L),
                        followEdge(VIEWER_ID, 101L))));
        when(followRepository.findByIdFollowerIdIn(anyCollection()))
                .thenReturn(List.of(
                        followEdge(100L, 200L),
                        followEdge(101L, 200L),
                        followEdge(101L, 300L)));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c200, c300));

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        // c200 = 2 × 2 = 4, c300 = 1 × 2 = 2
        assertThat(result.getContent().get(0).getId()).isEqualTo(200L);
        assertThat(result.getContent().get(0).getScore()).isEqualTo(2 * FOLLOW_GRAPH_WEIGHT);
        assertThat(result.getContent().get(0).getFactors())
                .contains("followed-by-2-of-your-follows");
        assertThat(result.getContent().get(1).getId()).isEqualTo(300L);
        assertThat(result.getContent().get(1).getScore()).isEqualTo(FOLLOW_GRAPH_WEIGHT);
    }

    @Test
    void secondHopExcludesAlreadyFollowedAndViewerSelf() {
        // viewer follows 100; 100 follows 100 (no-op self-loop guard
        // not relevant here), follows the viewer (1) and follows 200.
        // Already-followed (100) and self (1) must not appear in
        // secondHopCount, so the candidate query result for 200 only
        // gets the 200 edge counted.
        Mentee viewer = mentee(VIEWER_ID, List.of());
        Mentor c200 = mentor(200L, List.of());

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(followEdge(VIEWER_ID, 100L))));
        when(followRepository.findByIdFollowerIdIn(anyCollection()))
                .thenReturn(List.of(
                        followEdge(100L, VIEWER_ID),  // would credit the viewer
                        followEdge(100L, 100L),       // would credit already-followed
                        followEdge(100L, 200L)));     // legitimate second-hop
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c200));

        Page<FollowRecommendationResponse> result = service.recommend(VIEWER_ID, firstPage);

        FollowRecommendationResponse only = result.getContent().get(0);
        assertThat(only.getScore()).isEqualTo(FOLLOW_GRAPH_WEIGHT);
        assertThat(only.getFactors()).containsExactly("followed-by-1-of-your-follows");
    }

    @Test
    void emptyFolloweeSet_skipsSecondHopQuery() {
        Mentee viewer = mentee(VIEWER_ID, List.of("ai"));
        Mentor c10 = mentor(10L, List.of("ai"));

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c10));

        service.recommend(VIEWER_ID, firstPage);

        verify(followRepository, never()).findByIdFollowerIdIn(anyCollection());
    }

    // ── Pagination & windowing ─────────────────────────────────────────────

    @Test
    void candidateQueryUsesRankingWindow() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        service.recommend(VIEWER_ID, firstPage);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findFollowRecommendationCandidates(eq(VIEWER_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(RANKING_WINDOW);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void pageSlicingReturnsRequestedSlice() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        // 5 zero-score candidates → id-DESC ordering: 50, 40, 30, 20, 10
        List<User> candidates = new ArrayList<>();
        for (long id = 10L; id <= 50L; id += 10L) {
            candidates.add(mentor(id, List.of()));
        }
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(candidates);

        Page<FollowRecommendationResponse> page1 = service.recommend(VIEWER_ID, PageRequest.of(0, 2));
        Page<FollowRecommendationResponse> page2 = service.recommend(VIEWER_ID, PageRequest.of(1, 2));
        Page<FollowRecommendationResponse> page3 = service.recommend(VIEWER_ID, PageRequest.of(2, 2));

        assertThat(page1.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactly(50L, 40L);
        assertThat(page2.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactly(30L, 20L);
        assertThat(page3.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactly(10L);
        assertThat(page1.getTotalElements()).isEqualTo(5);
    }

    @Test
    void pageBeyondTotalReturnsEmpty() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(mentor(1L, List.of())));

        Page<FollowRecommendationResponse> result =
                service.recommend(VIEWER_ID, PageRequest.of(5, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void responseShapeMirrorsUserSummary() {
        Mentee viewer = mentee(VIEWER_ID, List.of("AI"));
        Mentor c = mentor(42L, List.of("AI"));
        c.setLastName("Surname");
        c.setProfilePhoto("https://cdn/x.png");

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(c));

        FollowRecommendationResponse only = service.recommend(VIEWER_ID, firstPage)
                .getContent().get(0);

        assertThat(only.getId()).isEqualTo(42L);
        assertThat(only.getFirstName()).isEqualTo("M42");
        assertThat(only.getLastName()).isEqualTo("Surname");
        assertThat(only.getProfilePhoto()).isEqualTo("https://cdn/x.png");
        assertThat(only.getRole()).isEqualTo("MENTOR");
        assertThat(only.getScore()).isEqualTo(INTEREST_WEIGHT);
        assertThat(only.getFactors()).containsExactly("shared-interest:AI");
    }

    @Test
    void menteeViewerRoleDiscriminator_returnsMENTEE() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        Mentee candidate = mentee(99L, List.of());

        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        FollowRecommendationResponse only = service.recommend(VIEWER_ID, firstPage)
                .getContent().get(0);

        assertThat(only.getRole()).isEqualTo("MENTEE");
    }

    // ── Followee-set cap ───────────────────────────────────────────────────

    @Test
    void followeeFetchUsesMaxViewerFolloweesPageSize() {
        Mentee viewer = mentee(VIEWER_ID, List.of());
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));
        when(userRepository.findFollowRecommendationCandidates(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        service.recommend(VIEWER_ID, firstPage);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(followRepository).findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                eq(VIEWER_ID), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(FollowRecommendationService.MAX_VIEWER_FOLLOWEES);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static Mentor mentor(Long id, List<String> interests) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("M" + id);
        m.setLastName("L");
        m.setEmail("m" + id + "@ex.com");
        if (interests != null) {
            m.setInterests(interests);
        }
        return m;
    }

    private static Mentee mentee(Long id, List<String> interests) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("Me" + id);
        m.setLastName("L");
        m.setEmail("me" + id + "@ex.com");
        if (interests != null) {
            m.setInterests(interests);
        }
        return m;
    }

    private static Follow followEdge(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }
}
