package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.*;
import com.group7.backend.service.ConversationService;
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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end WebSocket test using a real {@link WebSocketStompClient} against
 * the live Tomcat (RANDOM_PORT). Verifies CONNECT auth, SUBSCRIBE participant
 * ACL, and broadcast delivery after a REST send.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagingWebSocketIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ConversationParticipantRepository conversationParticipantRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationService conversationService;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @MockitoBean private EmailService emailService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
        notificationRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Reuse the application's ObjectMapper so the JSR-310 module is registered
        // and the same date format is used end-to-end.
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    void participantSubscribesAndReceivesBroadcastFromRestSend() throws Exception {
        Fixture fix = setupActiveMentorship("ws_m_a@test.com", "ws_e_a@test.com");

        StompSession session = connect(fix.menteeToken);

        LinkedBlockingDeque<MessageResponse> received = new LinkedBlockingDeque<>();
        session.subscribe("/topic/conversation/" + fix.conversationId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return MessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof MessageResponse mr) {
                    received.add(mr);
                }
            }
        });

        // Give Spring a generous moment to register the subscription before sending.
        Thread.sleep(2000);

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("Hello over websocket");
        // Use TestRestTemplate so the request goes through the live Tomcat
        // bound to the same Spring context as the WebSocket subscriber. This
        // ensures the broadcast reaches the live server's session registry.
        HttpHeaders sendHeaders = new HttpHeaders();
        sendHeaders.setContentType(MediaType.APPLICATION_JSON);
        sendHeaders.setBearerAuth(fix.mentorToken);
        ResponseEntity<String> sendResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/mentorships/" + fix.mentorshipId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), sendHeaders),
                String.class);
        assertThat(sendResponse.getStatusCode().value()).isEqualTo(201);

        MessageResponse delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getContent()).isEqualTo("Hello over websocket");
        assertThat(delivered.getConversationId()).isEqualTo(fix.conversationId);
        assertThat(delivered.getMentorshipId()).isEqualTo(fix.mentorshipId);
        assertThat(delivered.getSenderId()).isEqualTo(fix.mentorId);

        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        StompHeaders stompHeaders = new StompHeaders();
        // No Authorization header — server-side interceptor throws on CONNECT
        // and the broker closes the WebSocket without sending CONNECTED.
        assertThatThrownBy(() -> stompClient
                        .connectAsync(wsUrl(), new WebSocketHttpHeaders(), stompHeaders, new SilentSessionHandler())
                        .get(5, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    @Test
    void nonParticipantSubscriptionDoesNotReceiveBroadcast() throws Exception {
        Fixture fix = setupActiveMentorship("ws_m_b@test.com", "ws_e_b@test.com");
        String outsiderToken = registerAndLogin("ws_outsider@test.com", true);

        // CONNECT succeeds (the outsider has a valid JWT) but the SUBSCRIBE
        // frame is rejected by the interceptor; the server then closes the
        // session. From the client's perspective the subscription handler is
        // simply never invoked, even after a real broadcast happens.
        StompSession session = connect(outsiderToken);

        LinkedBlockingDeque<MessageResponse> received = new LinkedBlockingDeque<>();
        try {
            session.subscribe("/topic/conversation/" + fix.conversationId, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return MessageResponse.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof MessageResponse mr) {
                        received.add(mr);
                    }
                }
            });
        } catch (Exception ignored) {
            // Some Spring versions surface the rejection as an exception here;
            // others close the session asynchronously. Either way, no frame
            // should be delivered.
        }
        Thread.sleep(500);

        // Have the mentor send a real message via REST and confirm the outsider
        // never receives it.
        SendMessageRequest body = new SendMessageRequest();
        body.setContent("private");
        HttpHeaders sendHeaders = new HttpHeaders();
        sendHeaders.setContentType(MediaType.APPLICATION_JSON);
        sendHeaders.setBearerAuth(fix.mentorToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/mentorships/" + fix.mentorshipId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), sendHeaders),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);

        MessageResponse delivered = received.poll(2, TimeUnit.SECONDS);
        assertThat(delivered).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Fixture(
            String mentorToken,
            String menteeToken,
            Long mentorId,
            Long menteeId,
            Long mentorshipId,
            Long conversationId) {
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws/chat";
    }

    private StompSession connect(String jwt) throws Exception {
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.add("Authorization", "Bearer " + jwt);
        return stompClient
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(), stompHeaders, new SilentSessionHandler())
                .get(5, TimeUnit.SECONDS);
    }

    private Fixture setupActiveMentorship(String mentorEmail, String menteeEmail) throws Exception {
        String mentorToken = registerAndLogin(mentorEmail, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(mentorEmail))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);
        String menteeToken = registerAndLogin(menteeEmail, false);
        Long menteeId = userRepository.findByEmail(menteeEmail).orElseThrow().getId();

        String createBody = objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()));
        MvcResult requestResult = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk())
                .andReturn();
        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Bootstrap the conversation up-front so the WS subscriber can ACL-pass.
        // In the production flow, the first REST/STOMP send creates it via
        // ConversationService.findOrCreateForMentorship.
        Long conversationId = conversationService
                .findOrCreateForMentorship(mentorshipId, mentor.getId())
                .getId();

        return new Fixture(mentorToken, menteeToken, mentor.getId(), menteeId,
                mentorshipId, conversationId);
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName(isMentor ? "Mira" : "Eli");
        req.setLastName(isMentor ? "Mentor" : "Mentee");
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
            // Allow the test to observe failures via session state instead of
            // letting them spam stderr.
        }
    }
}
