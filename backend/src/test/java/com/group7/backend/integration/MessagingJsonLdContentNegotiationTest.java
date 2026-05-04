package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.*;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code Accept: application/ld+json} on a message returns a
 * W3C Activity Streams 2.0 {@code Create} activity wrapping a {@code Note},
 * matching the pattern shown in the project wiki's "Use of Standards" page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessagingJsonLdContentNegotiationTest {

    private static final String LD_JSON = "application/ld+json";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ConversationParticipantRepository conversationParticipantRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
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
    }

    @Test
    void sendingAMessageReturnsActivityStreamsCreateOnLdJsonAccept() throws Exception {
        Fixture fix = setupActiveMentorship("ld_m@test.com", "ld_e@test.com");

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("hello in JSON-LD");
        MvcResult result = mockMvc.perform(post("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fix.mentorToken)
                        .header(HttpHeaders.ACCEPT, LD_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(LD_JSON);
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());

        // Outer wrapper is an Activity Streams 2.0 Create activity.
        assertThat(payload.get("@context").asText())
                .isEqualTo("https://www.w3.org/ns/activitystreams");
        assertThat(payload.get("@type").asText()).isEqualTo("Create");
        assertThat(payload.get("@id").asText()).contains("/messages/").endsWith("#create");
        assertThat(payload.get("published").isMissingNode()).isFalse();

        // Actor is a Person identified by user IRI.
        JsonNode actor = payload.get("actor");
        assertThat(actor.get("@type").asText()).isEqualTo("Person");
        assertThat(actor.get("@id").asText()).contains("/api/users/");
        assertThat(actor.get("name").asText()).isEqualTo("Mira Mentor");

        // Object is the Note carrying the message content.
        JsonNode note = payload.get("object");
        assertThat(note.get("@type").asText()).isEqualTo("Note");
        assertThat(note.get("@id").asText())
                .contains("/api/conversations/")
                .contains("/messages/");
        assertThat(note.get("content").asText()).isEqualTo("hello in JSON-LD");
        assertThat(note.get("attributedTo").asText()).contains("/api/users/" + fix.mentorId);
        assertThat(note.get("published").isMissingNode()).isFalse();
    }

    @Test
    void plainJsonAcceptStillReturnsFlatMessageResponse() throws Exception {
        Fixture fix = setupActiveMentorship("ld_m2@test.com", "ld_e2@test.com");

        SendMessageRequest body = new SendMessageRequest();
        body.setContent("hello plain");
        MvcResult result = mockMvc.perform(post("/api/mentorships/" + fix.mentorshipId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fix.mentorToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        // No @context, no Create wrapper — backward-compatible plain JSON.
        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(payload.get("@context")).isNull();
        assertThat(payload.get("content").asText()).isEqualTo("hello plain");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Fixture(
            String mentorToken,
            Long mentorId,
            Long mentorshipId) {
    }

    private Fixture setupActiveMentorship(String mentorEmail, String menteeEmail) throws Exception {
        String mentorToken = registerAndLogin(mentorEmail, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(mentorEmail))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentor.setFirstName("Mira");
        mentor.setLastName("Mentor");
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin(menteeEmail, false);

        String createBody = objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()));
        MvcResult requestResult = mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk())
                .andReturn();
        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();

        return new Fixture(mentorToken, mentor.getId(), mentorshipId);
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
}
