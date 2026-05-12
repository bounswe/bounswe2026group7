package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.request.RegisterRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Follower-aware visibility coverage for the feed read paths
 * (issue-524 follow-up — addressed the entity/DTO half; this PR covers
 * the read-side privacy gap).
 *
 * <p>The flag {@code app.feed.respect-profile-visibility} ships
 * disabled by default to keep the public-by-default behaviour bit-for-bit
 * during the staged rollout. This test class boots the same context with
 * the flag flipped on via {@link SpringBootTest#properties()} so the
 * filter actually runs.
 *
 * <p>Cases pinned here:
 * <ul>
 *   <li>Hidden mentee + non-follower viewer → post 404 / search empty /
 *       author timeline empty / For-You suppresses the post.</li>
 *   <li>Hidden mentee + follower viewer → post visible everywhere
 *       (predicate's follower branch).</li>
 *   <li>Hidden mentee + self → post visible everywhere
 *       (predicate's author-id branch).</li>
 *   <li>Hidden author who is a mentor (no {@code profileVisibility}
 *       column on Mentor at all) → never filtered (predicate's
 *       {@code NOT EXISTS} branch).</li>
 * </ul>
 *
 * <p>The flag-off default behaviour is already pinned implicitly by
 * every other {@code Feed*IntegrationTest} that runs without the flag
 * override — those tests would fail if the predicate suppressed rows
 * with the flag off.
 */
@SpringBootTest(properties = "app.feed.respect-profile-visibility=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedProfileVisibilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM feed_post_comments");
        jdbcTemplate.update("DELETE FROM feed_post_shares");
        jdbcTemplate.update("DELETE FROM feed_post_bookmarks");
        jdbcTemplate.update("DELETE FROM feed_post_likes");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        jdbcTemplate.update("DELETE FROM follows");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void hiddenMentee_nonFollower_suppressedAcrossReadPaths() throws Exception {
        String hiddenMenteeToken = registerAndLogin("hidden@test.com", false);
        String strangerToken = registerAndLogin("stranger@test.com", false);
        Long hiddenId = userRepository.findByEmail("hidden@test.com").orElseThrow().getId();

        long postId = createPost(hiddenMenteeToken, "secret thoughts about ai", List.of("topic"));
        hideMenteeProfile(hiddenMenteeToken);

        // single-post GET → 404 for non-follower
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        // author timeline → empty page
        mockMvc.perform(get("/api/feed/users/" + hiddenId + "/posts")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // search → empty
        mockMvc.perform(get("/api/feed/search").param("q", "ai")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // For-You candidates → post should be filtered out; the page is
        // empty (no other authors posted in this test)
        mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // interaction guard: like attempt → 404
        mockMvc.perform(post("/api/feed/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void hiddenMentee_followerStillSees() throws Exception {
        String hiddenMenteeToken = registerAndLogin("hidden_f@test.com", false);
        String followerToken = registerAndLogin("follower_f@test.com", false);
        Long hiddenId = userRepository.findByEmail("hidden_f@test.com").orElseThrow().getId();

        // Follower follows the hidden mentee BEFORE they go hidden (the
        // realistic flow: existing followers don't lose access).
        mockMvc.perform(post("/api/users/" + hiddenId + "/follow")
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isCreated());

        long postId = createPost(hiddenMenteeToken, "thought about graphs", List.of("graph"));
        hideMenteeProfile(hiddenMenteeToken);

        // Follower can still see the post on every read path
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/users/" + hiddenId + "/posts")
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));

        mockMvc.perform(get("/api/feed/search").param("q", "graph")
                        .header("Authorization", "Bearer " + followerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));
    }

    @Test
    void hiddenMentee_selfViewUnchanged() throws Exception {
        String hiddenMenteeToken = registerAndLogin("hidden_s@test.com", false);
        Long hiddenId = userRepository.findByEmail("hidden_s@test.com").orElseThrow().getId();

        long postId = createPost(hiddenMenteeToken, "my private musings", List.of("solo"));
        hideMenteeProfile(hiddenMenteeToken);

        // Self can still see their own post on every read path
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + hiddenMenteeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/users/" + hiddenId + "/posts")
                        .header("Authorization", "Bearer " + hiddenMenteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));

        // Search by hashtag (body doesn't contain "solo" — that's the tag).
        mockMvc.perform(get("/api/feed/search").param("hashtag", "solo")
                        .header("Authorization", "Bearer " + hiddenMenteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));
    }

    @Test
    void mentorAuthor_neverHidden_evenWithFlagOn() throws Exception {
        // Mentor has no profileVisibility column at all — the predicate's
        // NOT EXISTS branch (no mentees row matches the author_id) must
        // let mentor-authored posts through unconditionally.
        String mentorToken = registerAndLogin("mentor_author@test.com", true);
        String strangerToken = registerAndLogin("stranger_mentor@test.com", false);
        Long mentorId = userRepository.findByEmail("mentor_author@test.com").orElseThrow().getId();

        long postId = createPost(mentorToken, "from a mentor", List.of("mtopic"));

        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/feed/users/" + mentorId + "/posts")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));

        mockMvc.perform(get("/api/feed/search").param("q", "mentor")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void hideMenteeProfile(String menteeToken) throws Exception {
        MenteeProfileRequest req = new MenteeProfileRequest();
        req.setProfileVisibility(false);
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVisibility").value(false));
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

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Vis");
        req.setLastName("Tester");
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
}
