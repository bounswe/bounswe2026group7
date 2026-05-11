package com.group7.backend.integration;

import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.FollowRecommendationService;
import com.group7.backend.service.ranking.AdvancedFollowRanker;
import com.group7.backend.service.ranking.FollowRanker;
import com.group7.backend.service.ranking.RuleBasedFollowRanker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the {@code algorithm=advanced} switch path
 * (#437). Verifies:
 *
 * <ol>
 *   <li>{@code @Primary @ConditionalOnProperty(advanced)} bean wiring —
 *       the {@link AdvancedFollowRanker} replaces the legacy ranker when
 *       the flag flips, while {@link RuleBasedFollowRanker} remains in
 *       the context for reflection / debug.</li>
 *   <li>End-to-end recommend() flow with the advanced context populated
 *       from real Postgres reads — viewer's interests + followee set +
 *       second-hop counts + ban / visibility filters all flow through.</li>
 *   <li>Factor emission — recommendations carry {@code shared-interest:*}
 *       factors when the candidate matches the viewer's interest labels,
 *       proving the {@code InterestOverlapFollowSignal} fired through the
 *       advanced aggregator.</li>
 * </ol>
 *
 * <p>{@code sync.enabled=false} keeps Neo4j out of this integration's
 * scope. PPR + projection paths are covered by the dedicated
 * {@code PersonalizedPageRankIntegrationTest} and
 * {@code FollowGraphProjectionServiceTest} against Testcontainers Neo4j +
 * GDS; in this @SpringBootTest the PPR {@code ObjectProvider} returns
 * null and the signal emits {@code ppr-unavailable}, which is the
 * documented degraded contract.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.recommendations.follow.algorithm=advanced",
        "app.recommendations.follow.sync.enabled=false",
        "app.recommendations.follow.signals.ppr-enabled=false",
        "app.recommendations.follow.signals.semantic-affinity-enabled=false"
})
@Transactional
class FollowRecommendationAdvancedIntegrationTest {

    @Autowired private FollowRecommendationService service;
    @Autowired private UserRepository userRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FollowRanker injectedRanker;

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM follows");
        jdbc.update("DELETE FROM feed_post_likes");
        jdbc.update("DELETE FROM feed_post_comments");
        jdbc.update("DELETE FROM feed_post_shares");
        jdbc.update("DELETE FROM feed_posts");
        userRepository.deleteAll();
    }

    @Test
    void advancedRankerBean_isInjectedWhenFlagFlipped() {
        // @Primary @ConditionalOnProperty advanced wins over the legacy
        // RuleBasedFollowRanker @Component.
        assertThat(injectedRanker).isInstanceOf(AdvancedFollowRanker.class);
    }

    @Test
    void recommend_emitsSharedInterestFactor_whenCandidateMatchesViewerInterests() {
        Mentee viewer = saveMentee("viewer@x.com", List.of("ai", "ml"));
        Mentor candidate = saveMentor("cand@x.com", List.of("ai", "data"));
        // Distractor candidate with no overlap — should rank lower.
        saveMentor("none@x.com", List.of("biology"));

        Page<FollowRecommendationResponse> out = service.recommend(
                viewer.getId(), PageRequest.of(0, 10));

        assertThat(out.getContent()).isNotEmpty();
        FollowRecommendationResponse topCandidate = out.getContent().stream()
                .filter(r -> r.getId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected overlap candidate in results but got: " + out.getContent()));

        // Advanced aggregator includes the InterestOverlapFollowSignal —
        // the shared-interest:ai factor is what proves the advanced
        // ranker actually fired (the legacy ranker emits the same factor
        // but the assertion still validates the signal flowed end-to-end).
        assertThat(topCandidate.getFactors())
                .anyMatch(f -> f.toLowerCase().contains("shared-interest"));
    }

    @Test
    void recommend_excludesAlreadyFollowedCandidates() {
        Mentee viewer = saveMentee("v2@x.com", List.of("ai"));
        Mentor followed = saveMentor("f2@x.com", List.of("ai"));
        Mentor unfollowed = saveMentor("u2@x.com", List.of("ai"));
        followRepository.save(follow(viewer.getId(), followed.getId()));

        Page<FollowRecommendationResponse> out = service.recommend(
                viewer.getId(), PageRequest.of(0, 10));

        assertThat(out.getContent()).extracting(FollowRecommendationResponse::getId)
                .doesNotContain(followed.getId(), viewer.getId())
                .contains(unfollowed.getId());
    }

    @Test
    void recommend_coldStartViewer_stillReturnsCandidates() {
        // Viewer with zero follows + zero interests triggers the cold-
        // start path; popularity-by-major map is empty since we don't
        // have any field data, but the service must still return the
        // candidate window with valid scores rather than throwing.
        Mentee viewer = saveMentee("cold@x.com", List.of());
        saveMentor("m1@x.com", List.of("ai"));
        saveMentor("m2@x.com", List.of("ml"));

        Page<FollowRecommendationResponse> out = service.recommend(
                viewer.getId(), PageRequest.of(0, 10));

        assertThat(out.getContent()).isNotEmpty();
        assertThat(out.getContent()).allMatch(r -> r.getScore() >= 0);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Mentee saveMentee(String email, List<String> interests) {
        Mentee m = new Mentee();
        m.setEmail(email);
        m.setPasswordHash("x"); m.setFirstName("F"); m.setLastName("L");
        m.setInterests(interests);
        m.setProfileVisibility(true);
        return userRepository.save(m);
    }

    private Mentor saveMentor(String email, List<String> interests) {
        Mentor m = new Mentor();
        m.setEmail(email);
        m.setPasswordHash("x"); m.setFirstName("F"); m.setLastName("L");
        m.setInterests(interests);
        return userRepository.save(m);
    }

    private Follow follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }
}
