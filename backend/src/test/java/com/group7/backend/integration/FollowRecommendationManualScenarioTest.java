package com.group7.backend.integration;

import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.FollowRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual realistic-scenario test for the advanced ranker. Seeds a
 * deliberate fixture with known properties so we can hand-verify the
 * ranking output is "meaningful" — not just that the code runs.
 *
 * <p>Scenario: viewer Alice (mentee, CS major, interests {ai, ml, nlp}).
 * <ul>
 *   <li>BOB     — mentor, perfect interest overlap {ai,ml,nlp},
 *                 active poster (5 posts + 10 comments in last week),
 *                 viewer liked one of his posts last week → should
 *                 dominate the ranking</li>
 *   <li>CAROL   — mentor, 1-interest overlap {ai},
 *                 inactive (no posts in window),
 *                 no prior interaction → mid-tier</li>
 *   <li>DAVE    — mentor, ZERO interest overlap {biology},
 *                 highly active (8 posts, all biology) → low score
 *                 unless engagement weight dominates</li>
 *   <li>EVE     — mentee with profile_visibility=true, shares ai/ml
 *                 → should appear (profile visibility check)</li>
 *   <li>FRANK   — mentee with profile_visibility=FALSE → MUST NOT
 *                 appear (private mentee filter)</li>
 *   <li>GRACE   — Alice already follows her → MUST NOT appear
 *                 (already-followed filter)</li>
 * </ul>
 *
 * <p>The test prints the actual ranking and factors so a reviewer can
 * eyeball whether the output is sensible. Hard assertions enforce the
 * non-negotiables (filters work, ordering makes sense, factors emit).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "app.recommendations.follow.algorithm=advanced",
        "app.recommendations.follow.sync.enabled=false",
        "app.recommendations.follow.signals.ppr-enabled=false",
        "app.recommendations.follow.signals.semantic-affinity-enabled=false"
})
@Transactional
class FollowRecommendationManualScenarioTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private FollowRecommendationService service;
    @Autowired private UserRepository userRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final OffsetDateTime NOW = OffsetDateTime.now();

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
    void richScenario_topCandidateMatchesExpectations_andFactorsAreReadable() {
        // ── viewer ────────────────────────────────────────────────────
        Mentee alice = saveMentee("alice@x.com", "CS", true, List.of("ai", "ml", "nlp"));

        // ── candidates ────────────────────────────────────────────────
        Mentor bob = saveMentor("bob@x.com", "CS", List.of("ai", "ml", "nlp"));
        // BOB: prolific poster + viewer engaged with his content
        seedPosts(bob.getId(), 5, NOW.minusHours(6));
        seedComments(bob.getId(), 10, NOW.minusHours(12));
        Long bobPost1 = seedPost(bob.getId(), NOW.minusDays(2));
        jdbc.update(
                "INSERT INTO feed_post_likes(post_id, user_id, created_at) VALUES (?, ?, ?)",
                bobPost1, alice.getId(), Timestamp.from(NOW.minusDays(1).toInstant()));

        Mentor carol = saveMentor("carol@x.com", "CS", List.of("ai"));
        // CAROL: no posts → dormant, only interest match

        Mentor dave = saveMentor("dave@x.com", "Biology", List.of("biology"));
        // DAVE: very active but in a different field, zero interest overlap
        seedPosts(dave.getId(), 8, NOW.minusHours(3));

        Mentee eve = saveMentee("eve@x.com", "CS", true, List.of("ai", "ml"));

        // ── must-not-appear users ─────────────────────────────────────
        Mentee frank = saveMentee("frank@x.com", "CS", false, List.of("ai")); // private
        Mentor grace = saveMentor("grace@x.com", "CS", List.of("ai"));
        followRepository.save(follow(alice.getId(), grace.getId())); // already followed

        // ── exercise ──────────────────────────────────────────────────
        Page<FollowRecommendationResponse> page = service.recommend(
                alice.getId(), PageRequest.of(0, 20));

        // ── print full ranking so a human can eyeball ─────────────────
        System.out.println("\n──── ADVANCED FOLLOW RANKING ────");
        System.out.printf("Viewer: alice (CS, interests=%s)%n", List.of("ai", "ml", "nlp"));
        System.out.printf("Result count: %d%n%n", page.getContent().size());
        for (FollowRecommendationResponse r : page.getContent()) {
            String name = email(r.getId(), alice.getId(), bob.getId(), carol.getId(),
                    dave.getId(), eve.getId(), frank.getId(), grace.getId());
            System.out.printf("  [%3d] %-10s (id=%d)  factors=%s%n",
                    r.getScore(), name, r.getId(), r.getFactors());
        }
        System.out.println("──── END RANKING ────\n");

        // ── hard assertions on the non-negotiables ────────────────────

        // 1. Filters: private mentee + self + already-followed are excluded.
        assertThat(page.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .doesNotContain(alice.getId(), frank.getId(), grace.getId());

        // 2. All four other candidates appear.
        assertThat(page.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .contains(bob.getId(), carol.getId(), dave.getId(), eve.getId());

        // 3. BOB should be the top result: maxed on interest + engagement
        //    + direct-interaction. If he isn't, the ranking is broken.
        Long topId = page.getContent().get(0).getId();
        assertThat(topId)
                .as("Expected BOB to top the ranking (interest+engagement+interaction); "
                        + "actual top was id=%d. Full ranking above.", topId)
                .isEqualTo(bob.getId());

        // 4. BOB's factors should mention BOTH shared-interest AND
        //    you've-engaged-before AND active-this-week.
        FollowRecommendationResponse bobResp = page.getContent().get(0);
        assertThat(bobResp.getFactors()).anyMatch(f -> f.contains("shared-interest"));
        assertThat(bobResp.getFactors()).anyMatch(f -> f.contains("you've-engaged-before"));
        assertThat(bobResp.getFactors()).anyMatch(f -> f.contains("active-this-week"));

        // 5. DAVE has zero interest overlap and Alice has 2 followees (no
        //    cold-start). His score should be strictly less than BOB's.
        FollowRecommendationResponse daveResp = page.getContent().stream()
                .filter(r -> r.getId().equals(dave.getId())).findFirst().orElseThrow();
        assertThat(daveResp.getScore())
                .as("DAVE has zero interest match and no follow-graph signal; "
                        + "score (%d) must be strictly below BOB's (%d)",
                        daveResp.getScore(), bobResp.getScore())
                .isLessThan(bobResp.getScore());

        // 6. No score should be negative or above 100.
        assertThat(page.getContent()).allSatisfy(r ->
                assertThat(r.getScore()).isBetween(0, 100));

        // 7. Factors should not contain raw empty strings or nulls.
        assertThat(page.getContent()).allSatisfy(r ->
                assertThat(r.getFactors()).allSatisfy(f ->
                        assertThat(f).isNotBlank()));
    }

    @Test
    void mmr_doesNotDropItems_whenCandidatesExceedOutputK() {
        // MMR defaults: topK=20, outputK=10. With 15 candidates we have
        // mmrInput=15, MMR picks 10. The previous bug dropped head[10..14]
        // entirely so the returned page only had 10 items instead of 15.
        Mentee viewer = saveMentee("viewer@x.com", "CS", true, List.of("ai"));
        // Give the viewer a follow so we're NOT in cold-start (interest
        // overlap signal needs to fire so MMR has a non-trivial label index).
        Mentor anchor = saveMentor("anchor@x.com", "CS", List.of("ai"));
        followRepository.save(follow(viewer.getId(), anchor.getId()));

        // 15 candidates, all sharing the 'ai' interest so all get a
        // shared-interest:ai factor and MMR diversity has something to chew on.
        java.util.List<Long> candidateIds = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Mentor m = saveMentor("mmr" + i + "@x.com", "CS", List.of("ai"));
            candidateIds.add(m.getId());
        }

        Page<FollowRecommendationResponse> page = service.recommend(
                viewer.getId(), PageRequest.of(0, 50));

        System.out.println("\n──── MMR DATA-LOSS CHECK ────");
        System.out.printf("Expected: 15 candidates in response; actual: %d%n",
                page.getContent().size());
        for (FollowRecommendationResponse r : page.getContent()) {
            System.out.printf("  [%3d] id=%d  factors=%s%n",
                    r.getScore(), r.getId(), r.getFactors());
        }
        System.out.println("──── END MMR CHECK ────\n");

        // All 15 must appear — the dropped slice [outputK..topK-1] was
        // the bug we just fixed.
        assertThat(page.getContent())
                .extracting(FollowRecommendationResponse::getId)
                .containsExactlyInAnyOrderElementsOf(candidateIds);
    }

    @Test
    void coldStartScenario_zeroFollowees_returnsMeaningfulRanking() {
        // Cold-start viewer: never followed anyone, has interests.
        Mentee newby = saveMentee("newby@x.com", "CS", true, List.of("ai", "ml"));

        // Three candidates with varying overlap.
        Mentor highMatch = saveMentor("hm@x.com", "CS", List.of("ai", "ml"));
        Mentor lowMatch  = saveMentor("lm@x.com", "CS", List.of("ai", "design"));
        Mentor noMatch   = saveMentor("nm@x.com", "Biology", List.of("zoology"));

        Page<FollowRecommendationResponse> page = service.recommend(
                newby.getId(), PageRequest.of(0, 20));

        System.out.println("\n──── COLD-START RANKING ────");
        for (FollowRecommendationResponse r : page.getContent()) {
            String tag = r.getId().equals(highMatch.getId()) ? "HIGH-MATCH"
                       : r.getId().equals(lowMatch.getId())  ? "LOW-MATCH"
                       : r.getId().equals(noMatch.getId())   ? "NO-MATCH"
                       : "OTHER";
            System.out.printf("  [%3d] %-10s (id=%d)  factors=%s%n",
                    r.getScore(), tag, r.getId(), r.getFactors());
        }
        System.out.println("──── END COLD-START ────\n");

        // Cold-start should still rank high-match > no-match.
        FollowRecommendationResponse high = page.getContent().stream()
                .filter(r -> r.getId().equals(highMatch.getId())).findFirst().orElseThrow();
        FollowRecommendationResponse no = page.getContent().stream()
                .filter(r -> r.getId().equals(noMatch.getId())).findFirst().orElseThrow();
        assertThat(high.getScore())
                .as("Cold-start high-match (%d) should outscore no-match (%d)",
                        high.getScore(), no.getScore())
                .isGreaterThan(no.getScore());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Mentee saveMentee(String email, String major, boolean visible, List<String> interests) {
        Mentee m = new Mentee();
        m.setEmail(email);
        m.setPasswordHash("x"); m.setFirstName("F"); m.setLastName("L");
        m.setMajor(major);
        m.setProfileVisibility(visible);
        m.setInterests(interests);
        return userRepository.save(m);
    }

    private Mentor saveMentor(String email, String field, List<String> interests) {
        Mentor m = new Mentor();
        m.setEmail(email);
        m.setPasswordHash("x"); m.setFirstName("F"); m.setLastName("L");
        m.setField(field);
        m.setInterests(interests);
        return userRepository.save(m);
    }

    private Follow follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }

    private Long seedPost(Long authorId, OffsetDateTime when) {
        return jdbc.queryForObject(
                "INSERT INTO feed_posts(author_id, body, created_at, updated_at, version) "
                + "VALUES (?, 'body', ?, ?, 0) RETURNING id",
                Long.class, authorId, Timestamp.from(when.toInstant()),
                Timestamp.from(when.toInstant()));
    }

    private void seedPosts(Long authorId, int count, OffsetDateTime when) {
        for (int i = 0; i < count; i++) {
            seedPost(authorId, when.minusMinutes(i));
        }
    }

    private void seedComments(Long authorId, int count, OffsetDateTime when) {
        Long postId = seedPost(authorId, when.minusDays(3));
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO feed_post_comments"
                    + "(post_id, author_id, body, created_at, updated_at, version) "
                    + "VALUES (?, ?, 'c', ?, ?, 0)",
                    postId, authorId,
                    Timestamp.from(when.minusMinutes(i).toInstant()),
                    Timestamp.from(when.minusMinutes(i).toInstant()));
        }
    }

    private static String email(Long id, Long alice, Long bob, Long carol,
                                 Long dave, Long eve, Long frank, Long grace) {
        if (id.equals(alice)) return "alice";
        if (id.equals(bob)) return "BOB";
        if (id.equals(carol)) return "carol";
        if (id.equals(dave)) return "dave";
        if (id.equals(eve)) return "eve";
        if (id.equals(frank)) return "frank";
        if (id.equals(grace)) return "grace";
        return "??";
    }
}
