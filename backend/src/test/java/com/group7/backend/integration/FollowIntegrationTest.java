package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the follow / unfollow primitive (#343) against
 * a real Postgres. Covers the documented contract surface (idempotent
 * POST returning 201/200, idempotent DELETE returning 204, self-follow
 * 400, missing-followee 404, list-shape, profile counts) plus two
 * concurrency scenarios (Scenario A: distinct followers; Scenario B:
 * same pair under contention) so the {@code ON CONFLICT DO NOTHING}
 * race-safety isn't trivially asserted.
 *
 * <p>Cascade-on-user-delete is verified via raw {@link JdbcTemplate}
 * SQL — {@code userRepository.delete(...)} would invoke Hibernate's
 * cascade through {@code @OneToMany} collection scanning, and the
 * thin-entity design has no such collection. The DB-level
 * {@code ON DELETE CASCADE} on the FK is what we want to verify, and
 * that path only fires through a non-Hibernate delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FollowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        followRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── Happy-path lifecycle ───────────────────────────────────────────────

    @Test
    void followLifecycle_201_then_200_then_204_thenListsReflectState() throws Exception {
        Pair p = registerTwo("life_a@test.com", "life_b@test.com");

        // Fresh follow → 201, body carries the directional edge.
        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerId").value(p.idA))
                .andExpect(jsonPath("$.followeeId").value(p.idB));

        // Idempotent re-follow → 200, identical body.
        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerId").value(p.idA))
                .andExpect(jsonPath("$.followeeId").value(p.idB));

        // Followers / following list endpoints surface the edge.
        mockMvc.perform(get("/api/users/" + p.idB + "/followers")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(p.idA));

        mockMvc.perform(get("/api/users/" + p.idA + "/following")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(p.idB));

        // Unfollow → 204, list returns to empty.
        mockMvc.perform(delete("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isNoContent());

        // Idempotent re-delete → still 204.
        mockMvc.perform(delete("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + p.idB + "/followers")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ── X-Total-Count project-wide smoke (#489) ───────────────────────────

    @Test
    void followersList_carriesXTotalCountHeader() throws Exception {
        // Proves PageTotalCountHeaderAdvice fires on non-feed paged
        // endpoints — guards against a regression that scopes the advice
        // to feed routes silently.
        Pair p = registerTwo("xtc_a@test.com", "xtc_b@test.com");
        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users/" + p.idB + "/followers")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    // ── Self-follow: project-standard {error, message} body ───────────────

    @Test
    void selfFollow_returns400_withProjectStandardErrorBody() throws Exception {
        String token = registerAndLogin("self_a@test.com");
        Long aId = userRepository.findByEmail("self_a@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/users/" + aId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                // No `code` field — handler emits {error, message} only.
                .andExpect(jsonPath("$.code").doesNotExist());

        // Service-layer guard fires before the upsert — DB has no row.
        assertThat(followRepository.count()).isZero();
    }

    // ── Non-existent followee: 404, not 500 from a FK violation ───────────

    @Test
    void followNonExistentUser_returns404_notFkViolation500() throws Exception {
        String token = registerAndLogin("ghost_a@test.com");

        mockMvc.perform(post("/api/users/9999999/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(followRepository.count()).isZero();
    }

    // ── Profile counts reflect state across follow / unfollow ─────────────

    @Test
    void profileDetail_followerAndFollowingCountsReflectGraph() throws Exception {
        Pair p = registerTwo("counts_a@test.com", "counts_b@test.com");

        // Baseline: zero on both sides.
        assertCounts(p.tokenA, p.idA, 0L, 0L);
        assertCounts(p.tokenA, p.idB, 0L, 0L);

        // A follows B → A.following=1, B.followers=1.
        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated());
        assertCounts(p.tokenA, p.idA, 0L, 1L);
        assertCounts(p.tokenA, p.idB, 1L, 0L);

        // Unfollow returns counts to zero.
        mockMvc.perform(delete("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isNoContent());
        assertCounts(p.tokenA, p.idA, 0L, 0L);
        assertCounts(p.tokenA, p.idB, 0L, 0L);
    }

    @Test
    void ownProfile_meEndpoint_carriesCounts() throws Exception {
        Pair p = registerTwo("me_a@test.com", "me_b@test.com");

        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated());

        // /me endpoint goes through the same wrapper.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.idA))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(1));
    }

    @Test
    void isFollowingFlag_falseBeforeFollow_trueAfter_falseAfterUnfollow() throws Exception {
        Pair p = registerTwo("isf_a@test.com", "isf_b@test.com");

        // Before any follow edge exists, the viewer's read of B's profile
        // reports isFollowing=false.
        assertIsFollowing(p.tokenA, p.idB, false);

        // After A follows B, the same read flips to true.
        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated());
        assertIsFollowing(p.tokenA, p.idB, true);

        // After unfollow, back to false.
        mockMvc.perform(delete("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isNoContent());
        assertIsFollowing(p.tokenA, p.idB, false);
    }

    @Test
    void isFollowingFlag_isAlwaysFalseOnOwnProfile_evenWithLargeFollowingList() throws Exception {
        Pair p = registerTwo("isfself_a@test.com", "isfself_b@test.com");

        // Establish a follow edge in the OTHER direction so the viewer is in
        // somebody's "followers" set — this would trip a naive implementation
        // that consults the wrong column.
        mockMvc.perform(post("/api/users/" + p.idA + "/follow")
                        .header("Authorization", "Bearer " + p.tokenB))
                .andExpect(status().isCreated());

        // Self-read via numeric id and via /me both report isFollowing=false.
        assertIsFollowing(p.tokenA, p.idA, false);
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFollowing").value(false));
    }

    private void assertIsFollowing(String token, Long targetId, boolean expected) throws Exception {
        mockMvc.perform(get("/api/users/" + targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFollowing").value(expected));
    }

    // ── DB-level cascade via raw JDBC delete (not userRepository.delete) ───

    @Test
    void userDelete_cascadesBothDirections_viaRawJdbc() throws Exception {
        // Three users: A follows B, C follows A. Deleting A must reap both
        // edges via the FK ON DELETE CASCADE on either side.
        Pair p = registerTwo("casc_a@test.com", "casc_b@test.com");
        String tokenC = registerAndLogin("casc_c@test.com");
        Long idC = userRepository.findByEmail("casc_c@test.com").orElseThrow().getId();

        mockMvc.perform(post("/api/users/" + p.idB + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users/" + p.idA + "/follow")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isCreated());

        assertThat(followRepository.count()).isEqualTo(2L);

        // Raw JDBC delete bypasses Hibernate; the only thing that reaps the
        // follows rows now is the DB-level ON DELETE CASCADE.
        // Mentor uses JOINED inheritance — delete the subtype row first so
        // the FK from mentors(id) → users(id) doesn't block the parent row.
        jdbcTemplate.update("DELETE FROM mentors WHERE id = ?", p.idA);
        int deleted = jdbcTemplate.update("DELETE FROM users WHERE id = ?", p.idA);
        assertThat(deleted).isEqualTo(1);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followee_id = ?",
                Integer.class, p.idA, p.idA);
        assertThat(remaining).isZero();
        // C and B are untouched.
        assertThat(userRepository.existsById(p.idB)).isTrue();
        assertThat(userRepository.existsById(idC)).isTrue();
    }

    // ── Concurrency Scenario A: 50 distinct followers all 201 ─────────────

    @Test
    void concurrency_50DistinctFollowers_allReturn201() throws Exception {
        // Pre-create 50 followers + 1 target. Each follower hits POST /follow
        // against the same target concurrently. Every request is a fresh
        // insert (different (follower, target) pairs), so all should be 201.
        String targetToken = registerAndLogin("conc_target@test.com");
        Long targetId = userRepository.findByEmail("conc_target@test.com").orElseThrow().getId();

        int N = 50;
        List<String> tokens = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            tokens.add(registerAndLogin("conc_follower_" + i + "@test.com"));
        }

        FollowRaceOutcome outcome = runFollowRace(tokens, targetId);

        assertThat(outcome.created()).as("each distinct follower should land a fresh row").isEqualTo(N);
        assertThat(outcome.duplicate()).as("no duplicate path should fire").isZero();
        assertThat(outcome.failed()).as("no 5xx").isZero();
        assertThat(followRepository.countByIdFolloweeId(targetId)).isEqualTo(N);
    }

    // ── Concurrency Scenario B: same pair under contention ────────────────

    @Test
    void concurrency_samePairContention_allReturn200_exactlyOneRow() throws Exception {
        // Pre-warm one (A, B) edge; submit 50 concurrent POSTs from A. Every
        // request must hit the ON CONFLICT path, so all 50 responses are 200
        // and the table still contains exactly one row. This is the strict
        // test that the upsert's race-safety actually fires under contention.
        Pair p = registerTwo("race_a@test.com", "race_b@test.com");

        // Pre-warm directly via raw JDBC so the DB has the row before the
        // race. Going through the repository's upsertFollow here would
        // require an enclosing transaction (the @Modifying query has
        // flushAutomatically=true), which the test code outside of a
        // service call does not have.
        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)",
                p.idA, p.idB);
        assertThat(followRepository.count()).isEqualTo(1L);

        int N = 50;
        List<String> tokens = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            tokens.add(p.tokenA);
        }

        FollowRaceOutcome outcome = runFollowRace(tokens, p.idB);

        assertThat(outcome.created()).as("no fresh inserts — row pre-existed").isZero();
        assertThat(outcome.duplicate()).as("every request should hit the conflict path").isEqualTo(N);
        assertThat(outcome.failed()).as("no 5xx — ON CONFLICT must not throw").isZero();
        assertThat(followRepository.count()).as("still exactly one row").isEqualTo(1L);
    }

    // ── Privacy gate: mentee → other-mentee follow graph is blocked ────────

    @Test
    void menteeViewingAnotherMenteesFollowers_returns403() throws Exception {
        // Mirror of UserService.getProfileById line 244: a mentee cannot
        // enumerate another mentee's identity through any surface, including
        // the follow graph. Without this gate the follow lists would be a
        // backdoor around the existing search/profile rules (review
        // finding #1).
        String tokenA = registerAndLoginAsMentee("priv_mentee_a@test.com");
        registerAndLoginAsMentee("priv_mentee_b@test.com");
        Long idB = userRepository.findByEmail("priv_mentee_b@test.com").orElseThrow().getId();

        mockMvc.perform(get("/api/users/" + idB + "/followers")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/" + idB + "/following")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void menteeViewingOwnFollowers_isAllowed() throws Exception {
        // The gate's self-carve-out (matches getProfileById's
        // targetId.equals(requesterId) condition) lets a mentee see their
        // own follower list — the count UI on /me would otherwise break.
        String token = registerAndLoginAsMentee("priv_self@test.com");
        Long id = userRepository.findByEmail("priv_self@test.com").orElseThrow().getId();

        mockMvc.perform(get("/api/users/" + id + "/followers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + id + "/following")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    // ── Engagement notifications ───────────────────────────────────────────

    @Test
    void follow_publishesNewFollowerNotification_andSkipsOnIdempotentReFollow() throws Exception {
        Pair p = registerTwo("notif_follow_a@test.com", "notif_follow_b@test.com");

        // First follow → NEW_FOLLOWER row appears on user B.
        mockMvc.perform(post("/api/users/" + p.idB() + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA()))
                .andExpect(status().is2xxSuccessful());
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    List<Notification> rows = notificationRepository.findForUser(p.idB(), false).stream()
                            .filter(n -> n.getType() == NotificationType.NEW_FOLLOWER)
                            .toList();
                    assertThat(rows).hasSize(1);
                    assertThat(rows.get(0).getBody()).contains("started following you.");
                });

        // Re-follow (idempotent) → no second row.
        mockMvc.perform(post("/api/users/" + p.idB() + "/follow")
                        .header("Authorization", "Bearer " + p.tokenA()))
                .andExpect(status().is2xxSuccessful());
        Thread.sleep(300);
        List<Notification> after2 = notificationRepository.findForUser(p.idB(), false).stream()
                .filter(n -> n.getType() == NotificationType.NEW_FOLLOWER)
                .toList();
        assertThat(after2).hasSize(1);
    }

    private record Pair(String tokenA, String tokenB, Long idA, Long idB) {
    }

    private record FollowRaceOutcome(int created, int duplicate, int failed) {
    }

    private Pair registerTwo(String emailA, String emailB) throws Exception {
        String tokenA = registerAndLogin(emailA);
        String tokenB = registerAndLogin(emailB);
        Long idA = userRepository.findByEmail(emailA).orElseThrow().getId();
        Long idB = userRepository.findByEmail(emailB).orElseThrow().getId();
        return new Pair(tokenA, tokenB, idA, idB);
    }

    private String registerAndLogin(String email) throws Exception {
        // Default: register as mentor so the profile-detail count assertions
        // don't trip the mentee→mentee privacy gate at
        // UserService.getProfileById. Most scenarios in this test exercise
        // the role-agnostic graph mechanics; the privacy-gate scenario
        // explicitly opts into mentees via {@link #registerAndLoginAsMentee}.
        return registerAndLoginAs(email, true);
    }

    private String registerAndLoginAsMentee(String email) throws Exception {
        return registerAndLoginAs(email, false);
    }

    private String registerAndLoginAs(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Follow");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private void assertCounts(String token, Long targetId, long expectedFollowers, long expectedFollowing)
            throws Exception {
        mockMvc.perform(get("/api/users/" + targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(expectedFollowers))
                .andExpect(jsonPath("$.followingCount").value(expectedFollowing));
    }

    /**
     * Concurrency harness: each token in {@code tokens} fires one POST
     * /follow against {@code targetId}. All threads block on a
     * {@link CountDownLatch} until released so the actual hits land in a
     * tight burst. Status codes are bucketed: 201 → created, 200 →
     * duplicate (conflict path), anything else → failed.
     */
    private FollowRaceOutcome runFollowRace(List<String> tokens, Long targetId) throws Exception {
        int N = tokens.size();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        try {
            List<Future<Integer>> futures = new ArrayList<>(N);
            for (int i = 0; i < N; i++) {
                String token = tokens.get(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return mockMvc.perform(post("/api/users/" + targetId + "/follow")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        return 500;
                    }
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                int status = f.get(30, TimeUnit.SECONDS);
                if (status == 201) {
                    created.incrementAndGet();
                } else if (status == 200) {
                    duplicate.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        return new FollowRaceOutcome(created.get(), duplicate.get(), failed.get());
    }
}
