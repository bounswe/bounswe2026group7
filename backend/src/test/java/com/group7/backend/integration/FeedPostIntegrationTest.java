package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.group7.backend.dto.feed.FeedPostLimits;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the feed-posts surface (#348) against real
 * Postgres. Walks the full create → read → edit → delete lifecycle plus
 * the cross-cutting paths surfaced in the plan: hashtag normalisation
 * (Turkish characters, mixed case, leading hash, multi-word drop),
 * two-level cascade on author delete, mentee + mentor both can post,
 * admin cannot post, soft-deleted post is invisible to author and
 * non-author both, validation rejection at the DTO boundary, and
 * JSON-LD content-negotiation silent downgrade.
 *
 * <p>The test profile disables rate-limiting (see
 * {@code application-test.properties}), so the smoke test is not coupled
 * to bucket capacities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedPostIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        // Children first by FK, then parents. Cascade would handle this,
        // but explicit ordering is more debuggable when something goes wrong.
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── Full lifecycle ──────────────────────────────────────────────────────

    @Test
    void fullLifecycle_create_read_update_delete() throws Exception {
        Pair authors = registerTwo("life_a@test.com", "life_b@test.com");

        // 1. Create a post (mentor A)
        MvcResult create = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + authors.tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "body": "Hello world", "hashtags": ["DataScience", "AI"] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Hello world"))
                .andExpect(jsonPath("$.hashtags[0]").value("ai"))   // alphabetical, lowercased
                .andExpect(jsonPath("$.hashtags[1]").value("datascience"))
                .andExpect(jsonPath("$.isAuthor").value(true))
                .andExpect(jsonPath("$.isEdited").value(false))
                .andReturn();
        long postId = objectMapper.readTree(create.getResponse().getContentAsString())
                .get("id").asLong();

        // 2. Read as a different user — isAuthor flips
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authors.tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAuthor").value(false));

        // 3. Edit (author updates only the body) — hashtags untouched
        Thread.sleep(20);  // ensure measurable updatedAt > createdAt
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authors.tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "body": "Updated body" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated body"))
                .andExpect(jsonPath("$.hashtags[0]").value("ai"))   // hashtags untouched
                .andExpect(jsonPath("$.isEdited").value(true));

        // 4. Soft-delete
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authors.tokenA))
                .andExpect(status().isNoContent());

        // 5. GET → 404 even for the author
        mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authors.tokenA))
                .andExpect(status().isNotFound());

        // 6. DELETE again → 204 idempotent
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + authors.tokenA))
                .andExpect(status().isNoContent());
    }

    // ── Hashtag normalisation (Turkish + mixed case + special) ──────────────

    @Test
    void hashtagNormalisation_turkishMixedCase_AndMultiWordDropped() throws Exception {
        String token = registerAndLogin("turkish@test.com", true);
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "body": "TR test",
                                  "hashtags": ["#YapayZeka", "yapayzeka", "#Mühendislik",
                                               "data science", "##nlp", "  ai  "] }
                                """))
                .andExpect(status().isCreated())
                // After normalisation: yapayzeka (deduped), mühendislik, nlp, ai
                // — in alphabetical order: ai, mühendislik, nlp, yapayzeka.
                .andExpect(jsonPath("$.hashtags.length()").value(4))
                .andExpect(jsonPath("$.hashtags[0]").value("ai"))
                .andExpect(jsonPath("$.hashtags[1]").value("mühendislik"))
                .andExpect(jsonPath("$.hashtags[2]").value("nlp"))
                .andExpect(jsonPath("$.hashtags[3]").value("yapayzeka"));
    }

    // ── Authorisation: mentor + mentee can post; admin cannot ───────────────

    @Test
    void mentorAndMentee_canBothPost() throws Exception {
        String mentorToken = registerAndLogin("any_mentor@test.com", true);
        String menteeToken = registerAndLogin("any_mentee@test.com", false);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"by mentor\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"by mentee\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void admin_cannotPost_returns403() throws Exception {
        // Build an admin row directly (no admin-registration endpoint exists).
        Admin admin = new Admin();
        admin.setEmail("admin_post@test.com");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("Password1"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setIsEmailVerified(true);
        userRepository.save(admin);

        LoginRequest login = new LoginRequest();
        login.setEmail("admin_post@test.com");
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"admin tries to post\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Author authorisation on PATCH / DELETE ──────────────────────────────

    @Test
    void nonAuthor_cannotEdit_or_delete() throws Exception {
        Pair p = registerTwo("auth_a@test.com", "auth_b@test.com");

        long postId = createPost(p.tokenA, "owned by A", List.of());

        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + p.tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hostile edit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + p.tokenB))
                .andExpect(status().isForbidden());
    }

    // ── Validation at the DTO boundary ──────────────────────────────────────

    @Test
    void blankBody_returns400() throws Exception {
        String token = registerAndLogin("val_blank@test.com", true);
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizeBody_returns400() throws Exception {
        String token = registerAndLogin("val_oversize@test.com", true);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("body", "x".repeat(FeedPostLimits.MAX_BODY_LENGTH + 1));
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overHashtagCap_returns400() throws Exception {
        String token = registerAndLogin("val_tagcap@test.com", true);
        StringBuilder tags = new StringBuilder("[");
        for (int i = 0; i <= FeedPostLimits.MAX_HASHTAGS; i++) {
            if (i > 0) tags.append(",");
            tags.append("\"tag").append(i).append("\"");
        }
        tags.append("]");
        String requestJson = "{\"body\": \"too many tags\", \"hashtags\": " + tags + "}";
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    // ── Two-level cascade on author delete ──────────────────────────────────

    @Test
    void authorDelete_cascadesPosts_andHashtags() throws Exception {
        String token = registerAndLogin("csc@test.com", true);
        Long authorId = userRepository.findByEmail("csc@test.com").orElseThrow().getId();

        long pid = createPost(token, "post 1", List.of("a", "b", "c"));
        long pid2 = createPost(token, "post 2", List.of("d"));
        Integer postsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_posts WHERE author_id = ?", Integer.class, authorId);
        Integer tagsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_hashtags WHERE post_id IN (?, ?)",
                Integer.class, pid, pid2);
        assertThat(postsBefore).isEqualTo(2);
        assertThat(tagsBefore).isEqualTo(4);

        // Mentor uses JOINED inheritance — delete subtype row first
        // so the FK from mentors(id) → users(id) doesn't block the parent.
        jdbcTemplate.update("DELETE FROM mentors WHERE id = ?", authorId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);

        Integer postsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_posts WHERE author_id = ?", Integer.class, authorId);
        Integer tagsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_hashtags WHERE post_id IN (?, ?)",
                Integer.class, pid, pid2);
        assertThat(postsAfter).isZero();   // FK cascade users → feed_posts
        assertThat(tagsAfter).isZero();    // cascade-of-cascade feed_posts → feed_post_hashtags
    }

    // ── JSON-LD silent downgrade ────────────────────────────────────────────

    @Test
    void getById_withLdJsonAccept_silentlyDowngradesToApplicationJson() throws Exception {
        String token = registerAndLogin("ld@test.com", true);
        long pid = createPost(token, "ld test", List.of("ld"));

        mockMvc.perform(get("/api/feed/posts/" + pid)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.parseMediaType("application/ld+json")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(pid));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private record Pair(String tokenA, String tokenB, Long idA, Long idB) {
    }

    private Pair registerTwo(String emailA, String emailB) throws Exception {
        String tokenA = registerAndLogin(emailA, true);
        String tokenB = registerAndLogin(emailB, true);
        Long idA = userRepository.findByEmail(emailA).orElseThrow().getId();
        Long idB = userRepository.findByEmail(emailB).orElseThrow().getId();
        return new Pair(tokenA, tokenB, idA, idB);
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Feed");
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
