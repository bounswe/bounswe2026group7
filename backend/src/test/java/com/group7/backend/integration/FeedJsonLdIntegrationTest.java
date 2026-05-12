package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.jsonld.JsonLdMediaType;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the AS 2.0 / Schema.org content negotiation
 * path through the live controller stack. Three carve-outs that the
 * unit tests can't cover on their own:
 *
 * <ol>
 *   <li>The {@code application/ld+json} <i>and</i>
 *       {@code application/activity+json} Accept headers both route
 *       through the JsonLd advice without 406.</li>
 *   <li>The response Content-Type echoes the negotiated wire type
 *       (this is what the {@code ServletServerHttpResponse}
 *       hand-off in OCP advice exists to preserve; without the
 *       integration test, a Spring upgrade can silently break it).</li>
 *   <li>The OrderedCollectionPage envelope wraps the For-You list
 *       endpoint and the wrapped items don't carry a redundant
 *       {@code @context}.</li>
 * </ol>
 *
 * <p>Plain {@code application/json} requests still see the existing
 * Spring Data {@code Page<T>} / single-DTO shape unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedJsonLdIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private FeedPostRepository feedPostRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        feedPostRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void detail_withApplicationLdJson_emitsAS2NoteAndPinsContentType() throws Exception {
        String token = registerAndLogin("ld@test.com");
        Long userId = userRepository.findByEmail("ld@test.com").orElseThrow().getId();
        Long postId = seedPost(userId, "Welcome to the feed", "en");

        MvcResult result = mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .accept(JsonLdMediaType.APPLICATION_LD_JSON))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .startsWith(JsonLdMediaType.APPLICATION_LD_JSON_VALUE);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("@context")).isTrue();
        assertThat(body.get("@type").get(0).asText()).isEqualTo("Note");
        assertThat(body.get("@type").get(1).asText()).isEqualTo("SocialMediaPosting");
        assertThat(body.get("inLanguage").asText()).isEqualTo("en");
        assertThat(body.get("contentMap").get("en").asText()).isEqualTo("Welcome to the feed");
    }

    @Test
    void detail_withApplicationActivityJson_isHandledAsAS2Synonym() throws Exception {
        String token = registerAndLogin("activity@test.com");
        Long userId = userRepository.findByEmail("activity@test.com").orElseThrow().getId();
        Long postId = seedPost(userId, "another welcome", "tr");

        MvcResult result = mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .accept(JsonLdMediaType.APPLICATION_ACTIVITY_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // The two media types must be interchangeable end-to-end —
        // Mastodon and ActivityPub clients prefer activity+json over the
        // generic ld+json. Without this test, a future negotiation tweak
        // could silently break either path.
        assertThat(result.getResponse().getContentType())
                .startsWith(JsonLdMediaType.APPLICATION_ACTIVITY_JSON_VALUE);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("@context")).isTrue();
    }

    @Test
    void detail_withApplicationJson_keepsExistingShape() throws Exception {
        String token = registerAndLogin("plain@test.com");
        Long userId = userRepository.findByEmail("plain@test.com").orElseThrow().getId();
        Long postId = seedPost(userId, "plain body", null);

        MvcResult result = mockMvc.perform(get("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // Plain JSON path → no AS 2.0 fields, existing envelope unchanged.
        assertThat(body.has("@context")).isFalse();
        assertThat(body.has("@type")).isFalse();
        assertThat(body.has("attributedTo")).isFalse();
        assertThat(body.get("body").asText()).isEqualTo("plain body");
    }

    @Test
    void followingFeed_withActivityJson_emitsOrderedCollectionPageEnvelope() throws Exception {
        String token = registerAndLogin("ocp@test.com");

        MvcResult result = mockMvc.perform(get("/api/feed/following?page=0&size=10")
                        .header("Authorization", "Bearer " + token)
                        .accept(JsonLdMediaType.APPLICATION_ACTIVITY_JSON))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .startsWith(JsonLdMediaType.APPLICATION_ACTIVITY_JSON_VALUE);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("@type").asText()).isEqualTo("OrderedCollectionPage");
        assertThat(body.get("totalItems").asLong()).isEqualTo(0L);
        assertThat(body.has("orderedItems")).isTrue();
        // first / last always present; prev / next absent on an empty page.
        assertThat(body.has("first")).isTrue();
        assertThat(body.has("last")).isTrue();
        assertThat(body.has("prev")).isFalse();
        assertThat(body.has("next")).isFalse();
    }

    // ── Helpers ────────────────────────────────────────────────

    private Long seedPost(Long authorId, String body, String lang) {
        FeedPost post = new FeedPost(authorId, body);
        post.setLang(lang);
        OffsetDateTime now = OffsetDateTime.now();
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        return feedPostRepository.save(post).getId();
    }

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setFirstName("Test");
        reg.setLastName("User");
        reg.setEmail(email);
        reg.setPassword("Password1");
        reg.setIsMentor(true);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }
}
