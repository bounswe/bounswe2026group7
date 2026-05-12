package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.FeedPostPushPayload;
import com.group7.backend.dto.response.FeedSharePushPayload;
import com.group7.backend.dto.response.FeedUnreadCountResponse;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.LastFeedReadAtRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the social-feed real-time push surface (#349).
 * Mirrors {@link MessagingWebSocketIntegrationTest} setup exactly:
 * RANDOM_PORT + WebSocketStompClient + MappingJackson2MessageConverter
 * (reusing the app ObjectMapper) + LinkedBlockingDeque + 5s poll +
 * SilentSessionHandler.
 *
 * <p>Three scenarios:
 * <ol>
 *   <li>Follower subscribes to own /topic/feed.{userId} and receives a
 *       FeedPostPushPayload after the followee creates a post (under
 *       the 5s NFR budget).</li>
 *   <li>Subscribing to another user's feed topic is rejected with a
 *       STOMP ERROR frame.</li>
 *   <li>mark-read + unread-count end-to-end shape against the live REST
 *       surface.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedRealtimeIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private FeedPostRepository feedPostRepository;
    @Autowired private LastFeedReadAtRepository lastFeedReadAtRepository;
    @MockitoBean private EmailService emailService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        // feedPostRepository.deleteAll() cascades to feed_post_hashtags via
        // the @OneToMany REMOVE cascade on FeedPost.hashtags, so no separate
        // hashtag-repo cleanup is needed.
        feedPostRepository.deleteAll();
        followRepository.deleteAll();
        lastFeedReadAtRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    void followerReceivesFeedPostPushPayload_withinNfrBudget() throws Exception {
        Pair authorAndFollower = setupAuthorAndFollower();
        Long followerId = authorAndFollower.followerId();
        String authorToken = authorAndFollower.authorToken();
        String followerToken = authorAndFollower.followerToken();

        StompSession session = connect(followerToken);

        LinkedBlockingDeque<FeedPostPushPayload> received = new LinkedBlockingDeque<>();
        session.subscribe("/topic/feed." + followerId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return FeedPostPushPayload.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof FeedPostPushPayload p) {
                    received.add(p);
                }
            }
        });

        // Give Spring a moment to register the SUBSCRIBE before the
        // author's create commits and the AFTER_COMMIT listener fires.
        Thread.sleep(2000);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts",
                HttpMethod.POST,
                authedJson(authorToken, Map.of(
                        "body", "Hello from a real STOMP test",
                        "hashtags", java.util.List.of())),
                String.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);

        // 5s budget = the NFR. In practice on localhost the frame arrives
        // in under 100ms, but assert against the contract not the latency.
        FeedPostPushPayload delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        // Slim push: postId / authorId / authorFirstName / createdAt only.
        // Clients fetch the body via GET /api/feed/posts/{id}.
        assertThat(delivered.postId()).isNotNull();
        assertThat(delivered.authorId()).isEqualTo(authorAndFollower.authorId());
        assertThat(delivered.authorFirstName()).isEqualTo("Author");
        assertThat(delivered.createdAt()).isNotNull();

        session.disconnect();
    }

    @Test
    void subscribingToAnotherUsersFeedTopicIsRejected() throws Exception {
        Pair pair = setupAuthorAndFollower();

        // SUBSCRIBE to the AUTHOR's feed topic (we are the follower) — ACL
        // must reject. Spring's StompSession.subscribe is async; the
        // rejection arrives as an ERROR frame which the
        // RecordingSessionHandler signals.
        RecordingSessionHandler handler = new RecordingSessionHandler();
        StompSession session = stompClient
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(),
                        bearerHeaders(pair.followerToken()), handler)
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/feed." + pair.authorId(), new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return FeedPostPushPayload.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                // Should never fire — ACL rejects the subscription.
            }
        });

        boolean rejected = handler.rejection.await(5, TimeUnit.SECONDS);
        assertThat(rejected).isTrue();
    }

    @Test
    void followerReceivesFeedSharePushPayload_onBareRepost() throws Exception {
        // Author posts; follower follows the sharer (a third party); sharer
        // reposts the post. Follower receives FeedSharePushPayload on
        // /topic/feed.{followerId}.
        Pair pair = setupAuthorAndFollower();
        // Re-purpose: sharer = follower here (they are a follower of the
        // author and ALSO the sharer of the resulting repost). The setup
        // suffices.
        String authorToken = pair.authorToken();
        String sharerToken = pair.followerToken();
        Long sharerId = pair.followerId();

        // Register a third party who follows the sharer so the fanout fires.
        String observerToken = registerAndLogin("ws_repost_observer@test.com", false, "Observer");
        Long observerId = userRepository.findByEmail("ws_repost_observer@test.com").orElseThrow().getId();
        ResponseEntity<String> followResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/users/" + sharerId + "/follow",
                HttpMethod.POST, authedJson(observerToken, null), String.class);
        assertThat(followResponse.getStatusCode().is2xxSuccessful()).isTrue();

        StompSession session = connect(observerToken);
        LinkedBlockingDeque<FeedSharePushPayload> received = new LinkedBlockingDeque<>();
        session.subscribe("/topic/feed." + observerId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return FeedSharePushPayload.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof FeedSharePushPayload p) {
                    received.add(p);
                }
            }
        });
        Thread.sleep(2000);

        // Author posts.
        ResponseEntity<String> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts",
                HttpMethod.POST,
                authedJson(authorToken, Map.of(
                        "body", "Reposted via STOMP",
                        "hashtags", java.util.List.of())),
                String.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        Long postId = objectMapper.readTree(createResponse.getBody()).get("id").asLong();

        // Sharer reposts.
        ResponseEntity<String> repostResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts/" + postId + "/reposts",
                HttpMethod.POST, authedJson(sharerToken, java.util.Map.of()), String.class);
        assertThat(repostResponse.getStatusCode().value()).isEqualTo(200);

        FeedSharePushPayload delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.postId()).isEqualTo(postId);
        assertThat(delivered.sharerId()).isEqualTo(sharerId);
        assertThat(delivered.sharerFirstName()).isEqualTo("Follower");  // setupAuthorAndFollower names the follower "Follower"
        assertThat(delivered.commentary()).isNull();   // bare repost
        assertThat(delivered.sharedAt()).isNotNull();

        session.disconnect();
    }

    @Test
    void followerReceivesFeedSharePushPayload_onQuoteShareWithCommentary() throws Exception {
        Pair pair = setupAuthorAndFollower();
        String authorToken = pair.authorToken();
        String sharerToken = pair.followerToken();
        Long sharerId = pair.followerId();

        String observerToken = registerAndLogin("ws_quote_observer@test.com", false, "Observer");
        Long observerId = userRepository.findByEmail("ws_quote_observer@test.com").orElseThrow().getId();
        restTemplate.exchange(
                "http://localhost:" + port + "/api/users/" + sharerId + "/follow",
                HttpMethod.POST, authedJson(observerToken, null), String.class);

        StompSession session = connect(observerToken);
        LinkedBlockingDeque<FeedSharePushPayload> received = new LinkedBlockingDeque<>();
        session.subscribe("/topic/feed." + observerId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return FeedSharePushPayload.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof FeedSharePushPayload p) {
                    received.add(p);
                }
            }
        });
        Thread.sleep(2000);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts",
                HttpMethod.POST,
                authedJson(authorToken, Map.of("body", "to be quoted", "hashtags", java.util.List.of())),
                String.class);
        Long postId = objectMapper.readTree(createResponse.getBody()).get("id").asLong();

        restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts/" + postId + "/reposts",
                HttpMethod.POST, authedJson(sharerToken, Map.of("body", "my added commentary")),
                String.class);

        FeedSharePushPayload delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.commentary()).isEqualTo("my added commentary");

        session.disconnect();
    }

    @Test
    void markReadAndUnreadCount_endToEndShape() throws Exception {
        Pair pair = setupAuthorAndFollower();
        // Initial state: no cursor row yet → unread-count counts everything.
        // No posts exist yet, so the count is still 0.
        ResponseEntity<FeedUnreadCountResponse> first = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/unread-count",
                HttpMethod.GET, authedJson(pair.followerToken(), null),
                FeedUnreadCountResponse.class);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getBody().count()).isZero();
        assertThat(first.getBody().cappedAtMax()).isFalse();

        // Author posts.
        ResponseEntity<String> createResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/posts",
                HttpMethod.POST,
                authedJson(pair.authorToken(), Map.of(
                        "body", "First post",
                        "hashtags", java.util.List.of())),
                String.class);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);

        // Follower's unread-count is now 1.
        ResponseEntity<FeedUnreadCountResponse> second = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/unread-count",
                HttpMethod.GET, authedJson(pair.followerToken(), null),
                FeedUnreadCountResponse.class);
        assertThat(second.getBody().count()).isEqualTo(1L);
        assertThat(second.getBody().cappedAtMax()).isFalse();

        // mark-read.
        ResponseEntity<Void> mark = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/mark-read",
                HttpMethod.POST, authedJson(pair.followerToken(), null),
                Void.class);
        assertThat(mark.getStatusCode().value()).isEqualTo(204);

        // unread-count drops to 0.
        ResponseEntity<FeedUnreadCountResponse> third = restTemplate.exchange(
                "http://localhost:" + port + "/api/feed/unread-count",
                HttpMethod.GET, authedJson(pair.followerToken(), null),
                FeedUnreadCountResponse.class);
        assertThat(third.getBody().count()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Pair(String authorToken, String followerToken,
                         Long authorId, Long followerId) {
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws/chat";
    }

    private StompSession connect(String jwt) throws Exception {
        return stompClient
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(),
                        bearerHeaders(jwt), new SilentSessionHandler())
                .get(5, TimeUnit.SECONDS);
    }

    private static StompHeaders bearerHeaders(String jwt) {
        StompHeaders h = new StompHeaders();
        h.add("Authorization", "Bearer " + jwt);
        return h;
    }

    private HttpEntity<String> authedJson(String jwt, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(body == null ? null : objectMapper.writeValueAsString(body), headers);
    }

    private Pair setupAuthorAndFollower() throws Exception {
        // Author = mentor, follower = mentee — admin is rejected by
        // FeedPostService.create and not relevant here.
        String authorToken = registerAndLogin("ws_feed_author@test.com", true, "Author");
        String followerToken = registerAndLogin("ws_feed_follower@test.com", false, "Follower");
        Long authorId = userRepository.findByEmail("ws_feed_author@test.com").orElseThrow().getId();
        Long followerId = userRepository.findByEmail("ws_feed_follower@test.com").orElseThrow().getId();

        // Use the follow REST surface so the call runs inside its own
        // transaction (the test method has no surrounding @Transactional).
        // Rate limits are disabled in the test profile, so this is safe.
        ResponseEntity<String> followResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/users/" + authorId + "/follow",
                HttpMethod.POST, authedJson(followerToken, null), String.class);
        assertThat(followResponse.getStatusCode().is2xxSuccessful()).isTrue();

        return new Pair(authorToken, followerToken, authorId, followerId);
    }

    private String registerAndLogin(String email, boolean isMentor, String firstName) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(firstName);
        req.setLastName("User");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
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

    private static class SilentSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            // Allow tests to observe via session state.
        }
    }

    private static class RecordingSessionHandler extends StompSessionHandlerAdapter {
        final CountDownLatch rejection = new CountDownLatch(1);

        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            rejection.countDown();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            rejection.countDown();
        }
    }
}
