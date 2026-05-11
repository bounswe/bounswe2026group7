package com.group7.backend.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.testsupport.builders.AvailabilityBuilder;
import com.group7.backend.testsupport.builders.MeetingBuilder;
import com.group7.backend.testsupport.builders.MentorshipRequestBuilder;
import com.group7.backend.testsupport.builders.TaskBuilder;
import com.group7.backend.testsupport.builders.UserBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin facade that owns MockMvc + ObjectMapper and exposes a fluent,
 * builder-driven entry point for E2E scenarios. One {@code E2EClient}
 * instance per test method is created by {@link AbstractE2ETest}.
 *
 * <p>Two flavours of API live here:
 * <ul>
 *   <li><b>Builders</b> — for actions with multiple optional fields
 *       (register a user, schedule a meeting, assign a task). Each builder
 *       is returned by a no-arg factory method like {@link #users()} and
 *       gathers state fluently before its terminal method drives MockMvc.</li>
 *   <li><b>One-shots</b> — for actions whose payload is a single id or
 *       a tiny Map (accept request, confirm meeting, set shared goal,
 *       fetch notifications). These are direct methods on the facade so
 *       scenarios stay readable.</li>
 * </ul>
 *
 * <p>All methods throw {@link Exception} from MockMvc; tests propagate it.
 */
public class E2EClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;

    public E2EClient(MockMvc mockMvc,
                     ObjectMapper objectMapper,
                     UserRepository userRepository,
                     VerificationTokenRepository verificationTokenRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    public UserBuilder users() {
        return new UserBuilder(this);
    }

    public MentorshipRequestBuilder requests() {
        return new MentorshipRequestBuilder(this);
    }

    public MeetingBuilder meetings() {
        return new MeetingBuilder(this);
    }

    public TaskBuilder tasks() {
        return new TaskBuilder(this);
    }

    public AvailabilityBuilder availability() {
        return new AvailabilityBuilder(this);
    }

    // ── Auth-flow primitives (used by UserBuilder.registerVerifyAndLogin) ───

    public Long register(RegisterRequest body) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(body.getEmail()).orElseThrow().getId();
    }

    public void verifyEmail(Long userId) throws Exception {
        String token = verificationTokenRepository
                .findByUserIdAndUsedFalse(userId)
                .get(0)
                .getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk());
    }

    public String login(String email, String password) throws Exception {
        LoginRequest body = new LoginRequest();
        body.setEmail(email);
        body.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    // ── One-shot mentorship-request actions ─────────────────────────────────

    public Long acceptRequest(UserHandle mentor, Long requestId, int durationMonths) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", durationMonths))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    public void rejectRequest(UserHandle mentor, Long requestId) throws Exception {
        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + mentor.token()))
                .andExpect(status().isOk());
    }

    public void cancelRequest(UserHandle mentee, Long requestId) throws Exception {
        mockMvc.perform(delete("/api/mentorship-requests/" + requestId)
                        .header("Authorization", "Bearer " + mentee.token()))
                .andExpect(status().isNoContent());
    }

    // ── One-shot mentorship lifecycle helpers ───────────────────────────────

    public void setSharedGoal(UserHandle user, Long mentorshipId, String goal) throws Exception {
        mockMvc.perform(put("/api/mentorships/" + mentorshipId + "/goal")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", goal))))
                .andExpect(status().isOk());
    }

    /** Mentor ends an ACTIVE mentorship → status transitions to COMPLETED (#237). */
    public void endMentorship(UserHandle mentor, Long mentorshipId, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (reason != null) body.put("reason", reason);
        mockMvc.perform(patch("/api/mentorships/" + mentorshipId + "/end")
                        .header("Authorization", "Bearer " + mentor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /** Mentee submits a 1–5 score rating on a COMPLETED/CANCELLED mentorship (#237). */
    public Long rateMentor(UserHandle mentee, Long mentorshipId, int score, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("score", score);
        if (comment != null) body.put("comment", comment);
        MvcResult result = mockMvc.perform(post("/api/mentorships/" + mentorshipId + "/rating")
                        .header("Authorization", "Bearer " + mentee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ── One-shot meeting actions ────────────────────────────────────────────

    public void confirmMeeting(UserHandle user, Long meetingId) throws Exception {
        mockMvc.perform(post("/api/meetings/" + meetingId + "/confirm")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());
    }

    // ── One-shot task actions ───────────────────────────────────────────────

    public void submitTask(UserHandle mentee, Long taskId, String text) throws Exception {
        mockMvc.perform(post("/api/tasks/" + taskId + "/submission")
                        .header("Authorization", "Bearer " + mentee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("submissionText", text))))
                .andExpect(status().isCreated());
    }

    public void reviewTask(UserHandle mentor, Long taskId, TaskStatus status, String feedback) throws Exception {
        mockMvc.perform(patch("/api/tasks/" + taskId + "/feedback")
                        .header("Authorization", "Bearer " + mentor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", status.name(),
                                "feedback", feedback))))
                .andExpect(status().isOk());
    }

    // ── Notifications & matching ────────────────────────────────────────────

    /** Returns the raw JSON array of notifications for the given user. */
    public JsonNode getNotifications(UserHandle user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    public JsonNode listMentorMatches(UserHandle mentee) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + mentee.token()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ── Plumbing accessors used by builder classes ──────────────────────────

    public MockMvc mockMvc() { return mockMvc; }
    public ObjectMapper objectMapper() { return objectMapper; }
}
