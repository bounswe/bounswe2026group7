package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the social-feed read surfaces (#350) against
 * real Postgres: For-You, Following, and search. Exercises the actual
 * ranking signals (interest overlap, time decay, follow boost) and the
 * search predicates against a seeded data set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedReadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        jdbcTemplate.update("DELETE FROM follows");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── Following feed ──────────────────────────────────────────────────────

    @Test
    void followingFeed_onlyShowsFollowedAuthors_chronologically() throws Exception {
        String tokenA = registerAndLogin("a@test.com", true);
        String tokenB = registerAndLogin("b@test.com", true);
        String tokenC = registerAndLogin("c@test.com", true);
        Long idB = userRepository.findByEmail("b@test.com").orElseThrow().getId();
        Long idC = userRepository.findByEmail("c@test.com").orElseThrow().getId();

        // A follows B (but not C)
        mockMvc.perform(post("/api/users/" + idB + "/follow")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());

        // B and C both post; B's post should appear in A's Following feed,
        // C's should not.
        long pB1 = createPost(tokenB, "B post 1", List.of());
        Thread.sleep(20);
        long pC1 = createPost(tokenC, "C post 1", List.of());
        Thread.sleep(20);
        long pB2 = createPost(tokenB, "B post 2", List.of());

        MvcResult result = mockMvc.perform(get("/api/feed/following")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        // Newest first: pB2 then pB1; pC1 excluded (not followed)
        assertThat(ids).containsExactly(pB2, pB1);
    }

    @Test
    void followingFeed_whenViewerFollowsNobody_returnsEmpty() throws Exception {
        String token = registerAndLogin("solo@test.com", true);
        String tokenOther = registerAndLogin("other@test.com", true);
        createPost(tokenOther, "post by another", List.of());

        mockMvc.perform(get("/api/feed/following")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void authorPostsFeed_returnsOnlyThatAuthorsPosts_chronologically() throws Exception {
        String viewerToken = registerAndLogin("author_viewer@test.com", true);
        String tokenA = registerAndLogin("author_a@test.com", true);
        String tokenB = registerAndLogin("author_b@test.com", true);
        Long authorAId = userRepository.findByEmail("author_a@test.com").orElseThrow().getId();

        long a1 = createPost(tokenA, "A post 1", List.of());
        Thread.sleep(20);
        long b1 = createPost(tokenB, "B post 1", List.of());
        Thread.sleep(20);
        long a2 = createPost(tokenA, "A post 2", List.of());

        MvcResult result = mockMvc.perform(get("/api/feed/users/" + authorAId + "/posts")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(a2, a1);
        assertThat(ids).doesNotContain(b1);
    }

    @Test
    void authorPostsFeed_excludesSoftDeletedPosts() throws Exception {
        String viewerToken = registerAndLogin("author_del_viewer@test.com", true);
        String authorToken = registerAndLogin("author_del@test.com", true);
        Long authorId = userRepository.findByEmail("author_del@test.com").orElseThrow().getId();

        long live = createPost(authorToken, "live", List.of());
        long deleted = createPost(authorToken, "deleted", List.of());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feed/posts/" + deleted)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/api/feed/users/" + authorId + "/posts")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(live);
    }

    @Test
    void authorPostsFeed_whenAuthorHasNoPosts_returnsEmpty() throws Exception {
        String viewerToken = registerAndLogin("author_empty_viewer@test.com", true);
        registerAndLogin("author_empty@test.com", true);
        Long authorId = userRepository.findByEmail("author_empty@test.com").orElseThrow().getId();

        mockMvc.perform(get("/api/feed/users/" + authorId + "/posts")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ── Search ──────────────────────────────────────────────────────────────

    @Test
    void search_byKeyword_returnsMatchingBodies() throws Exception {
        String tokenA = registerAndLogin("search_kw@test.com", true);
        long pid1 = createPost(tokenA, "Excited to share thoughts on data science", List.of());
        long pid2 = createPost(tokenA, "Just published a new ML tutorial", List.of());
        long pid3 = createPost(tokenA, "Lunch was great today", List.of());

        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("q", "data science")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(pid1);
    }

    @Test
    void search_byHashtag_returnsTaggedPosts() throws Exception {
        String tokenA = registerAndLogin("search_tag@test.com", true);
        long pid1 = createPost(tokenA, "First", List.of("ai", "ml"));
        long pid2 = createPost(tokenA, "Second", List.of("ai"));
        long pid3 = createPost(tokenA, "Third", List.of("biology"));

        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("hashtag", "#AI")  // user-side input — server normalises
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Set<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(ids).containsExactlyInAnyOrder(pid1, pid2);
    }

    @Test
    void search_combinedKeywordAndHashtag_returnsIntersection() throws Exception {
        String tokenA = registerAndLogin("search_combo@test.com", true);
        long pid1 = createPost(tokenA, "Machine learning with Python", List.of("ml", "python"));
        long pid2 = createPost(tokenA, "Machine learning theory", List.of("ml"));
        long pid3 = createPost(tokenA, "Python tricks", List.of("python"));

        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("q", "machine")
                        .param("hashtag", "python")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(pid1);
    }

    @Test
    void search_excludesSoftDeletedPosts() throws Exception {
        String tokenA = registerAndLogin("search_del@test.com", true);
        long pid1 = createPost(tokenA, "live post about data", List.of());
        long pid2 = createPost(tokenA, "deleted post about data", List.of());
        // Soft-delete pid2 via the API
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/feed/posts/" + pid2)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("q", "data")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(pid1);
    }

    @Test
    void search_keywordWithLikeMetacharacters_doesNotActAsWildcard() throws Exception {
        // Pre-fix bug: a user searching for `q=%` would match every post
        // because `%` is a LIKE wildcard. Service-side escape + ESCAPE '\'
        // on the LIKE clause turns user `%` into a literal-percent match.
        String token = registerAndLogin("search_inj@test.com", true);
        long matchPid = createPost(token, "100% completion", List.of());      // contains literal %
        long otherPid1 = createPost(token, "no special chars here", List.of()); // no %
        long otherPid2 = createPost(token, "underscore_in_body", List.of());    // contains _

        // q=% must match ONLY the post with literal %, not all posts.
        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("q", "%")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<Long> ids = java.util.stream.StreamSupport
                .stream(objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).containsExactly(matchPid);

        // q=_ similarly matches only the post with literal underscore.
        MvcResult result2 = mockMvc.perform(get("/api/feed/search")
                        .param("q", "_")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<Long> ids2 = java.util.stream.StreamSupport
                .stream(objectMapper.readTree(result2.getResponse().getContentAsString())
                        .get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids2).containsExactly(otherPid2);
    }

    @Test
    void search_emptyFilters_returns400() throws Exception {
        // /search is a filtered surface — empty filters would return the
        // whole feed and mask pagination cost as the system grows. Clients
        // that want everything should call /for-you or /following.
        String token = registerAndLogin("search_empty@test.com", true);
        createPost(token, "post body", List.of("ai"));

        mockMvc.perform(get("/api/feed/search")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Whitespace-only filters also count as empty.
        mockMvc.perform(get("/api/feed/search")
                        .param("q", "   ")
                        .param("hashtag", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_invalidHashtag_returnsEmpty() throws Exception {
        String tokenA = registerAndLogin("search_bad@test.com", true);
        createPost(tokenA, "post body", List.of("ai"));

        // "data science" has a space — fails the hashtag regex; search
        // returns empty rather than erroring.
        mockMvc.perform(get("/api/feed/search")
                        .param("hashtag", "data science")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── For-You feed ────────────────────────────────────────────────────────

    @Test
    void forYouFeed_excludesViewerOwnPosts() throws Exception {
        String tokenA = registerAndLogin("fy_own@test.com", true);
        String tokenB = registerAndLogin("fy_other@test.com", true);

        long pidA = createPost(tokenA, "by viewer A", List.of());
        long pidB = createPost(tokenB, "by other", List.of());

        MvcResult result = mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        assertThat(ids).doesNotContain(pidA);
        assertThat(ids).contains(pidB);
    }

    @Test
    void forYouFeed_rankingFavoursInterestOverlap() throws Exception {
        // Viewer is a mentor with "ai" as an interest. Two posts from a
        // different mentor: one tagged #ai (overlap), one tagged
        // #unrelated. The overlap post should rank above the unrelated.
        String viewerToken = registerAndLogin("fy_rank@test.com", true);
        Long viewerId = userRepository.findByEmail("fy_rank@test.com").orElseThrow().getId();
        // Add interest "ai" via PATCH /api/users/me/mentor
        MentorProfileRequest profile = new MentorProfileRequest();
        profile.setInterests(List.of("ai"));
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());

        String authorToken = registerAndLogin("fy_author@test.com", true);
        long matching = createPost(authorToken, "Match", List.of("ai"));
        Thread.sleep(20);
        long unrelated = createPost(authorToken, "Unrelated", List.of("biology"));

        MvcResult result = mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = StreamSupport.stream(body.get("content").spliterator(), false)
                .map(n -> n.get("id").asLong())
                .toList();
        // Both posts present; matching ranks above unrelated due to
        // interest-overlap signal even though unrelated is newer.
        assertThat(ids).containsSubsequence(matching, unrelated);
    }

    @Test
    void forYouFeed_whenNoCandidates_returnsEmpty() throws Exception {
        String token = registerAndLogin("fy_empty@test.com", true);
        // No other posts in the system.

        mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── Auth gate ───────────────────────────────────────────────────────────

    @Test
    void allReadEndpoints_unauthenticated_return403() throws Exception {
        mockMvc.perform(get("/api/feed/for-you")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/following")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/search")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/feed/users/1/posts")).andExpect(status().isForbidden());
    }

    // ── Page-size clamp ─────────────────────────────────────────────────────

    @Test
    void search_pageSizeClamp_limitsToMax100() throws Exception {
        String token = registerAndLogin("clamp@test.com", true);
        for (int i = 0; i < 5; i++) {
            createPost(token, "post " + i + " keyword", List.of());
        }

        MvcResult result = mockMvc.perform(get("/api/feed/search")
                        .param("q", "keyword")
                        .param("size", "100000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Returned content might be small (only 5 posts), but the page
        // size must have been clamped to 100. Validate via the size
        // field on the page response.
        assertThat(body.get("size").asInt()).isLessThanOrEqualTo(100);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Feed");
        req.setLastName("Reader");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
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

    private long createPost(String token, String body, List<String> hashtags) throws Exception {
        Map<String, Object> req = Map.of("body", body, "hashtags", hashtags);
        MvcResult res = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
}
