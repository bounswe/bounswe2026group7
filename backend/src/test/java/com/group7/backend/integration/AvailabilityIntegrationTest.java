package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.repository.*;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        availabilitySlotRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Test");
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

    private Long getMentorId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void mentorCanAddAndRetrieveSlots() throws Exception {
        String mentorToken = registerAndLogin("av_mentor1@test.com", true);
        Long mentorId = getMentorId("av_mentor1@test.com");

        String slotBody = objectMapper.writeValueAsString(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00", "recurring", true));

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.endTime").value("12:00:00"));

        MvcResult getResult = mockMvc.perform(get("/api/availability/" + mentorId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        var slots = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(slots.size()).isEqualTo(1);
        assertThat(slots.get(0).get("dayOfWeek").asText()).isEqualTo("MONDAY");
    }

    @Test
    void bulkUpdateReplacesExisting() throws Exception {
        String mentorToken = registerAndLogin("av_mentor2@test.com", true);

        // Add a slot first
        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());

        // Bulk update with different slots
        String bulkBody = objectMapper.writeValueAsString(Map.of("slots", List.of(
                Map.of("dayOfWeek", "TUESDAY", "startTime", "14:00", "endTime", "16:00"),
                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "10:00", "endTime", "13:00"))));

        MvcResult result = mockMvc.perform(put("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody))
                .andExpect(status().isOk())
                .andReturn();

        var slots = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(slots.size()).isEqualTo(2);
        // Monday slot should be gone
        assertThat(slots.get(0).get("dayOfWeek").asText()).isEqualTo("TUESDAY");
    }

    @Test
    void overlappingSlotRejected() throws Exception {
        String mentorToken = registerAndLogin("av_mentor3@test.com", true);

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "11:00", "endTime", "14:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void adjacentSlotsAllowed() throws Exception {
        String mentorToken = registerAndLogin("av_mentor4@test.com", true);

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "12:00", "endTime", "15:00"))))
                .andExpect(status().isCreated());
    }

    @Test
    void mentorCanDeleteOwnSlot() throws Exception {
        String mentorToken = registerAndLogin("av_mentor5@test.com", true);

        MvcResult createResult = mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "FRIDAY", "startTime", "14:00", "endTime", "17:00"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long slotId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(delete("/api/availability/" + slotId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isNoContent());

        Long mentorId = getMentorId("av_mentor5@test.com");
        mockMvc.perform(get("/api/availability/" + mentorId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void menteeCanViewMentorAvailability() throws Exception {
        String mentorToken = registerAndLogin("av_mentor6@test.com", true);
        Long mentorId = getMentorId("av_mentor6@test.com");

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "10:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());

        String menteeToken = registerAndLogin("av_mentee6@test.com", false);

        mockMvc.perform(get("/api/availability/" + mentorId)
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("WEDNESDAY"));
    }

    @Test
    void menteeCannotModifyAvailability() throws Exception {
        String menteeToken = registerAndLogin("av_mentee7@test.com", false);

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mentorNotFoundReturns404() throws Exception {
        String menteeToken = registerAndLogin("av_mentee8@test.com", false);

        mockMvc.perform(get("/api/availability/99999")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isNotFound());
    }
}
