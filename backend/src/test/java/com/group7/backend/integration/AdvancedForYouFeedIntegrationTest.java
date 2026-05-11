package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot-context coverage for the advanced For-You feed ranker (#438).
 * Flips the master flag on, mocks the OpenAI {@link EmbeddingModel} so
 * the embedder degrades gracefully to the hashtag-Jaccard fallback,
 * seeds a small candidate set, and asserts that the response carries
 * the new {@code factors} field (proves the pipeline wiring →
 * {@code FeedReadService} splice → {@code FeedPostListItem.factors}
 * round-trip).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.recommendations.feed.advanced.enabled=true",
        "app.recommendations.feed.bandit.enabled=false"
})
class AdvancedForYouFeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;
    @MockitoBean private EmbeddingModel embeddingModel;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM viewer_hashtag_engagement");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        jdbcTemplate.update("DELETE FROM follows");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());

        // Wire a minimal embedding response so SemanticSimilarityService.embed
        // returns a non-empty vector for whatever text we throw at it during
        // the pipeline's precompute. The test isn't asserting on cosine
        // ranking — it just needs the embedder path to succeed so the
        // pipeline runs to completion.
        EmbeddingResponse response = new EmbeddingResponse(List.of(
                new Embedding(new float[] {1.0f, 0.0f}, 0)));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);
    }

    @Test
    void advancedRankerEnabled_responseCarriesFactorsField() throws Exception {
        String viewerToken = registerAndLogin("viewer@test.com");
        String authorToken = registerAndLogin("author@test.com");

        createPost(authorToken, "Hello ML world", List.of("ai", "ml"));
        createPost(authorToken, "Another data post", List.of("data"));

        MvcResult result = mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = body.get("content");

        // The advanced pipeline wired through. The response shape must
        // include the new factors field on every item (even if empty for
        // a low-signal post — what we're proving is that the wiring works
        // end-to-end and the schema change is honoured).
        assertThat(content.isArray()).isTrue();
        for (JsonNode item : content) {
            assertThat(item.has("factors")).isTrue();
            assertThat(item.get("factors").isArray()).isTrue();
        }
    }

    @Test
    void advancedRankerEnabled_singleCandidate_doesNotErrorOnPipeline() throws Exception {
        // Smoke check: single post in the window must still walk through
        // precompute → score → sort → MMR → floor → bandit-off → slice
        // without throwing on the trivial cases (empty hashtag aggregation,
        // single-tag MMR window, etc.).
        String viewerToken = registerAndLogin("solo-viewer@test.com");
        String authorToken = registerAndLogin("solo-author@test.com");
        createPost(authorToken, "Solo post", List.of("ai"));

        mockMvc.perform(get("/api/feed/for-you")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(email);
        reg.setPassword("Password123!");
        reg.setFirstName("Test");
        reg.setLastName("User");
        reg.setRole("MENTEE");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Auto-verify so login succeeds.
        userRepository.findByEmail(email).ifPresent(u -> {
            jdbcTemplate.update("UPDATE users SET is_email_verified = true WHERE id = ?", u.getId());
        });

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    private long createPost(String token, String body, List<String> hashtags) throws Exception {
        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("body", body, "hashtags", hashtags));
        MvcResult result = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
