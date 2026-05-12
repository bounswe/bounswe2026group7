package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.FeedTrendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the trending-hashtag surface (#487) against
 * real Postgres. The trending materialized view must be populated by
 * an explicit REFRESH before it returns rows; the test seeds posts
 * + likes + comments via the API, calls {@code refreshTrendingView}
 * directly, then asserts the ranking.
 *
 * <p>{@code @TestPropertySource} flips the trending-refresh scheduler
 * back on (the bulk test profile sets it off so unrelated tests are
 * not coupled to a stray cron firing).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.feed.trending.enabled=true")
class FeedTrendingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeedTrendingService trendingService;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_edit_history");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_post_likes");
        jdbcTemplate.update("DELETE FROM feed_post_comments");
        jdbcTemplate.update("DELETE FROM feed_posts");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        // Refresh once on an empty source so the materialized view
        // matches the seeded state (otherwise it carries rows from
        // any prior in-context test).
        trendingService.refreshTrendingView();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void trendingList_returnsTagsRankedByScore_filteringSingleAuthorTagsOut() throws Exception {
        // Seed three authors so the HAVING ≥ 2 distinct-posts filter
        // can demote a single-author "rust" tag while keeping "java"
        // (5 posts) and "python" (3 posts) on the chart.
        String tokenA = registerAndLogin("trend_a@test.com");
        String tokenB = registerAndLogin("trend_b@test.com");
        String tokenC = registerAndLogin("trend_c@test.com");

        // 5 java posts spread across the three authors (≥ 2 distinct)
        createPost(tokenA, "j1", List.of("java"));
        createPost(tokenA, "j2", List.of("java"));
        createPost(tokenB, "j3", List.of("java"));
        createPost(tokenB, "j4", List.of("java"));
        createPost(tokenC, "j5", List.of("java"));

        // 3 python posts (also ≥ 2 distinct authors)
        createPost(tokenA, "p1", List.of("python"));
        createPost(tokenB, "p2", List.of("python"));
        createPost(tokenC, "p3", List.of("python"));

        // 1 rust post — only one author, dropped by HAVING.
        createPost(tokenA, "r1", List.of("rust"));

        trendingService.refreshTrendingView();

        mockMvc.perform(get("/api/feed/trending/hashtags?limit=10")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tag").value("java"))
                .andExpect(jsonPath("$[0].postCount").value(5))
                .andExpect(jsonPath("$[1].tag").value("python"))
                .andExpect(jsonPath("$[1].postCount").value(3));
    }

    @Test
    void trendingList_emptyWhenNoTagHasTwoDistinctPosts() throws Exception {
        String token = registerAndLogin("trend_empty@test.com");
        // Only one post — HAVING ≥ 2 filters it out entirely.
        createPost(token, "lonely", List.of("solo"));

        trendingService.refreshTrendingView();

        mockMvc.perform(get("/api/feed/trending/hashtags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void trendingList_clampsLimitAbove50_returns400() throws Exception {
        String token = registerAndLogin("trend_lim@test.com");

        mockMvc.perform(get("/api/feed/trending/hashtags?limit=100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendingList_limitZero_returns400() throws Exception {
        String token = registerAndLogin("trend_lim0@test.com");

        mockMvc.perform(get("/api/feed/trending/hashtags?limit=0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendingList_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/feed/trending/hashtags"))
                .andExpect(status().isForbidden());
    }

    @Test
    void v45MaterializedViewAndIndexes_existAfterMigration() {
        // Sanity check that V45 created the matview + the unique index
        // that REFRESH CONCURRENTLY depends on, plus the score
        // expression index for the LIMIT-ordered read.
        Integer matview = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_matviews WHERE matviewname = 'feed_trending_24h'",
                Integer.class);
        Integer uniqueIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_feed_trending_24h_tag'",
                Integer.class);
        Integer scoreIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_feed_trending_24h_score'",
                Integer.class);

        assertThat(matview).isEqualTo(1);
        assertThat(uniqueIndex).isEqualTo(1);
        assertThat(scoreIndex).isEqualTo(1);
    }

    @Test
    void refreshConcurrently_succeedsRepeatedly_withoutLockingReaders() {
        // Stress-light test: refresh several times in succession to
        // catch any "REFRESH CONCURRENTLY rejected" regression. The
        // unique index on (tag) is what makes CONCURRENTLY legal;
        // without V45's idx_feed_trending_24h_tag the second call
        // would throw "cannot refresh materialized view ...
        // concurrently".
        for (int i = 0; i < 3; i++) {
            trendingService.refreshTrendingView();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Trend");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(true);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private void createPost(String token, String body, List<String> hashtags) throws Exception {
        Map<String, Object> req = Map.of("body", body, "hashtags", hashtags);
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
