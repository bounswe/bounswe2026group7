package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString());
    }

    // ── Helpers ─────────────────────────────────────────────

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        return registerAndLogin("Test", "User", email, isMentor);
    }

    private String registerAndLogin(String firstName, String lastName, String email, boolean isMentor) throws Exception {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setFirstName(firstName);
        regRequest.setLastName(lastName);
        regRequest.setEmail(email);
        regRequest.setPassword("Password1");
        regRequest.setIsMentor(isMentor);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Verify email (required after email verification feature)
        String verificationToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verificationToken))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("Password1");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private Long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ═══════════════════════════════════════════════════════
    //  GET /api/users/me
    // ═══════════════════════════════════════════════════════

    // ── Mentor ──────────────────────────────────────────────

    @Test
    void getOwnProfile_mentor_returnsAllMentorFields() throws Exception {
        String token = registerAndLogin("Ayse", "Demir", "mentor@test.com", true);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andExpect(jsonPath("$.firstName").value("Ayse"))
                .andExpect(jsonPath("$.lastName").value("Demir"))
                .andExpect(jsonPath("$.email").value("mentor@test.com"))
                .andExpect(jsonPath("$.isEmailVerified").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.maxMenteeCapacity").value(3))
                .andExpect(jsonPath("$.currentMenteeCount").value(0))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void getOwnProfile_mentor_doesNotExposeMenteeFields() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("profileVisibility"), "Mentor should not have profileVisibility");
        assertNull(body.get("cancelCount"), "Mentor should not have cancelCount");
        assertNull(body.get("meetingFreqPref"), "Mentor should not have meetingFreqPref");
    }

    // ── Mentee ──────────────────────────────────────────────

    @Test
    void getOwnProfile_mentee_returnsAllMenteeFields() throws Exception {
        String token = registerAndLogin("Ali", "Yilmaz", "mentee@test.com", false);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yilmaz"))
                .andExpect(jsonPath("$.email").value("mentee@test.com"))
                .andExpect(jsonPath("$.isEmailVerified").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.profileVisibility").value(true))
                .andExpect(jsonPath("$.cancelCount").value(0))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void getOwnProfile_mentee_doesNotExposeMentorFields() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("bio"), "Mentee should not have bio");
        assertNull(body.get("expertise"), "Mentee should not have expertise");
        assertNull(body.get("maxMenteeCapacity"), "Mentee should not have maxMenteeCapacity");
        assertNull(body.get("currentMenteeCount"), "Mentee should not have currentMenteeCount");
    }

    // ── Security ────────────────────────────────────────────

    @Test
    void getOwnProfile_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOwnProfile_responseDoesNotIncludePasswordHash() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("passwordHash"), "passwordHash should not be in the response");
    }

    @Test
    void profileResponse_includesCorrectRole() throws Exception {
        String mentorToken = registerAndLogin("mentor@test.com", true);
        String menteeToken = registerAndLogin("mentee@test.com", false);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(jsonPath("$.role").value("MENTOR"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(jsonPath("$.role").value("MENTEE"));
    }

    // ═══════════════════════════════════════════════════════
    //  PATCH /api/users/me
    // ═══════════════════════════════════════════════════════

    // ── Mentor field updates ────────────────────────────────

    @Test
    void updateMentorProfile_allMentorFields_updatesCorrectly() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setBio("Senior engineer with 10 years experience");
        update.setField("Computer Science");
        update.setExpertise("Backend Development");
        update.setAffiliation("Bogazici University");
        update.setInterests(List.of("AI", "Systems", "Cloud"));
        update.setMaxMenteeCapacity(5);
        update.setPreferredMenteeSkills(List.of("Java", "Spring"));
        update.setPreferredMenteeMajor("Computer Engineering");
        update.setMentoringGoals("Guide students through career transitions");
        update.setMentorshipDuration(6);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Senior engineer with 10 years experience"))
                .andExpect(jsonPath("$.field").value("Computer Science"))
                .andExpect(jsonPath("$.expertise").value("Backend Development"))
                .andExpect(jsonPath("$.affiliation").value("Bogazici University"))
                .andExpect(jsonPath("$.interests.length()").value(3))
                .andExpect(jsonPath("$.maxMenteeCapacity").value(5))
                .andExpect(jsonPath("$.preferredMenteeSkills.length()").value(2))
                .andExpect(jsonPath("$.preferredMenteeMajor").value("Computer Engineering"))
                .andExpect(jsonPath("$.mentoringGoals").value("Guide students through career transitions"))
                .andExpect(jsonPath("$.mentorshipDuration").value(6));

        // Verify persisted via GET
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.bio").value("Senior engineer with 10 years experience"))
                .andExpect(jsonPath("$.interests.length()").value(3));
    }

    @Test
    void updateMentorProfile_setCapacityToZero_works() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setMaxMenteeCapacity(0);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxMenteeCapacity").value(0));
    }

    // ── Mentee field updates ────────────────────────────────

    @Test
    void updateMenteeProfile_allMenteeFields_updatesCorrectly() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setGoals("Master distributed systems");
        update.setMajor("Computer Engineering");
        update.setCareerInterest("Site Reliability Engineering");
        update.setInterests(List.of("Distributed Systems", "Containers"));
        update.setSkills(List.of("Python", "Go", "Docker", "Kubernetes"));
        update.setMeetingFreqPref("Bi-weekly");
        update.setBackgroundInfo("3rd year student with internship at a cloud company");
        update.setProfileVisibility(false);

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value("Master distributed systems"))
                .andExpect(jsonPath("$.major").value("Computer Engineering"))
                .andExpect(jsonPath("$.careerInterest").value("Site Reliability Engineering"))
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.skills.length()").value(4))
                .andExpect(jsonPath("$.meetingFreqPref").value("Bi-weekly"))
                .andExpect(jsonPath("$.backgroundInfo").value("3rd year student with internship at a cloud company"))
                .andExpect(jsonPath("$.profileVisibility").value(false));

        // Verify persisted via GET
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.skills.length()").value(4))
                .andExpect(jsonPath("$.profileVisibility").value(false));
    }

    // ── Common field updates ────────────────────────────────

    @Test
    void updateProfile_commonFields_updatesCorrectly() throws Exception {
        String token = registerAndLogin("Old", "Name", "user@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setFirstName("New");
        update.setLastName("FullName");

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("FullName"));
    }

    // ── Partial update behavior ─────────────────────────────

    @Test
    void partialUpdate_preservesExistingFields() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        // First: set multiple fields
        MentorProfileRequest update1 = new MentorProfileRequest();
        update1.setBio("Original bio");
        update1.setExpertise("Backend");
        update1.setField("CS");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk());

        // Second: only update expertise — bio and field should remain
        MentorProfileRequest update2 = new MentorProfileRequest();
        update2.setExpertise("Full Stack");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Original bio"))
                .andExpect(jsonPath("$.field").value("CS"))
                .andExpect(jsonPath("$.expertise").value("Full Stack"));
    }

    @Test
    void partialUpdate_commonAndRoleFields_together() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setFirstName("UpdatedFirst");
        update.setBio("Also updating bio");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.bio").value("Also updating bio"))
                .andExpect(jsonPath("$.lastName").value("User"));
    }

    @Test
    void partialUpdate_emptyBody_doesNotChangeAnything() throws Exception {
        String token = registerAndLogin("Ayse", "Demir", "mentor@test.com", true);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ayse"))
                .andExpect(jsonPath("$.lastName").value("Demir"));
    }

    // ── Cross-role field ignorance ──────────────────────────

    @Test
    void updateMentor_menteeFieldsIgnored() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        // Send JSON with both mentor and mentee fields — mentee fields should be ignored for a mentor
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"My bio\", \"goals\": \"This should be ignored\", \"major\": \"This too\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("My bio"));

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("goals"), "Mentor response should not contain mentee 'goals' field");
        assertNull(body.get("major"), "Mentor response should not contain mentee 'major' field");
    }

    @Test
    void updateMentee_mentorFieldsIgnored() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);

        // Send JSON with both mentee and mentor fields — mentor fields should be ignored for a mentee
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goals\": \"My goals\", \"bio\": \"This should be ignored\", \"expertise\": \"This too\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value("My goals"));

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("bio"), "Mentee response should not contain mentor 'bio' field");
        assertNull(body.get("expertise"), "Mentee response should not contain mentor 'expertise' field");
    }

    // ── Sequential updates ──────────────────────────────────

    @Test
    void multipleSequentialUpdates_eachPersistsCorrectly() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest u1 = new MentorProfileRequest();
        u1.setBio("Version 1");
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(u1)))
                .andExpect(status().isOk());

        MentorProfileRequest u2 = new MentorProfileRequest();
        u2.setExpertise("Backend");
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(u2)))
                .andExpect(status().isOk());

        MentorProfileRequest u3 = new MentorProfileRequest();
        u3.setBio("Version 2");
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(u3)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.bio").value("Version 2"))
                .andExpect(jsonPath("$.expertise").value("Backend"));
    }

    // ═══════════════════════════════════════════════════════
    //  GET /api/users/{id}
    // ═══════════════════════════════════════════════════════

    @Test
    void getProfileById_menteeViewsMentor_returns200() throws Exception {
        String mentorToken = registerAndLogin("mentor@test.com", true);
        String menteeToken = registerAndLogin("mentee@test.com", false);
        Long mentorId = getUserId(mentorToken);

        mockMvc.perform(get("/api/users/" + mentorId)
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andExpect(jsonPath("$.firstName").value("Test"));
    }

    @Test
    void getProfileById_mentorViewsMentee_returns200() throws Exception {
        String mentorToken = registerAndLogin("mentor@test.com", true);
        String menteeToken = registerAndLogin("mentee@test.com", false);
        Long menteeId = getUserId(menteeToken);

        mockMvc.perform(get("/api/users/" + menteeId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTEE"));
    }

    @Test
    void getProfileById_mentorViewsMentor_returns200() throws Exception {
        String mentor1Token = registerAndLogin("mentor1@test.com", true);
        String mentor2Token = registerAndLogin("mentor2@test.com", true);
        Long mentor1Id = getUserId(mentor1Token);

        mockMvc.perform(get("/api/users/" + mentor1Id)
                        .header("Authorization", "Bearer " + mentor2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"));
    }

    @Test
    void getProfileById_menteeViewsMentee_returns403() throws Exception {
        String mentee1Token = registerAndLogin("mentee1@test.com", false);
        String mentee2Token = registerAndLogin("mentee2@test.com", false);
        Long mentee1Id = getUserId(mentee1Token);

        mockMvc.perform(get("/api/users/" + mentee1Id)
                        .header("Authorization", "Bearer " + mentee2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Mentees cannot view other mentee profiles"));
    }

    @Test
    void getProfileById_menteeViewsOwnProfile_returns200() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);
        Long userId = getUserId(token);

        mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void getProfileById_viewsOwnProfile_returns200() throws Exception {
        String token = registerAndLogin("user@test.com", false);
        Long userId = getUserId(token);

        mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void getProfileById_nonexistentUser_returns404() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        mockMvc.perform(get("/api/users/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getProfileById_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfileById_responseDoesNotIncludePasswordHash() throws Exception {
        String mentorToken = registerAndLogin("mentor@test.com", true);
        String menteeToken = registerAndLogin("mentee@test.com", false);
        Long menteeId = getUserId(menteeToken);

        MvcResult result = mockMvc.perform(get("/api/users/" + menteeId)
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNull(body.get("passwordHash"), "passwordHash should not be in the response");
    }

    @Test
    void getProfileById_returnsUpdatedData() throws Exception {
        String mentorToken = registerAndLogin("mentor@test.com", true);
        String menteeToken = registerAndLogin("mentee@test.com", false);
        Long mentorId = getUserId(mentorToken);

        // Mentor updates their profile
        MentorProfileRequest update = new MentorProfileRequest();
        update.setBio("Updated via PATCH");
        update.setExpertise("Full Stack");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // Mentee views mentor's updated profile via /{id}
        mockMvc.perform(get("/api/users/" + mentorId)
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated via PATCH"))
                .andExpect(jsonPath("$.expertise").value("Full Stack"));
    }

    // ═══════════════════════════════════════════════════════
    //  Validation
    // ═══════════════════════════════════════════════════════

    @Test
    void updateProfile_blankFirstName_returns400() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setFirstName("");

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages.firstName").exists());
    }

    @Test
    void updateProfile_blankLastName_returns400() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setLastName("");

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.lastName").exists());
    }

    @Test
    void updateProfile_firstNameTooLong_returns400() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setFirstName("A".repeat(101));

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.firstName").exists());
    }

    @Test
    void updateProfile_bioTooLong_returns400() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setBio("A".repeat(1001));

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.bio").exists());
    }

    @Test
    void updateProfile_negativeMenteeCapacity_returns400() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setMaxMenteeCapacity(-1);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.maxMenteeCapacity").exists());
    }

    @Test
    void updateProfile_zeroMentorshipDuration_returns400() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setMentorshipDuration(0);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.mentorshipDuration").exists());
    }

    @Test
    void updateProfile_multipleValidationErrors_returnsAll() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setFirstName("");
        update.setLastName("");
        update.setMaxMenteeCapacity(-1);

        MvcResult result = mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andReturn();

        JsonNode messages = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("messages");
        assertTrue(messages.has("firstName"), "Should have firstName error");
        assertTrue(messages.has("lastName"), "Should have lastName error");
        assertTrue(messages.has("maxMenteeCapacity"), "Should have maxMenteeCapacity error");
    }

    // ═══════════════════════════════════════════════════════
    //  Collection persistence
    // ═══════════════════════════════════════════════════════

    @Test
    void updateProfile_interestsList_persistsAndReturns() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setInterests(List.of("AI", "Systems", "Databases"));

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(3))
                .andExpect(jsonPath("$.interests[0]").value("AI"))
                .andExpect(jsonPath("$.interests[1]").value("Systems"))
                .andExpect(jsonPath("$.interests[2]").value("Databases"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.interests.length()").value(3));
    }

    @Test
    void updateProfile_skillsList_persistsForMentee() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setSkills(List.of("Python", "Java", "SQL", "Docker"));

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(4));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.skills.length()").value(4));
    }

    @Test
    void updateProfile_preferredMenteeSkills_persistsForMentor() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setPreferredMenteeSkills(List.of("Java", "Spring", "PostgreSQL"));

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredMenteeSkills.length()").value(3));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.preferredMenteeSkills.length()").value(3));
    }

    @Test
    void updateProfile_replaceInterestsList_replacesCompletely() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MentorProfileRequest update1 = new MentorProfileRequest();
        update1.setInterests(List.of("AI", "Systems", "Databases"));
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk());

        MentorProfileRequest update2 = new MentorProfileRequest();
        update2.setInterests(List.of("Security", "Networking"));
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.interests[0]").value("Security"))
                .andExpect(jsonPath("$.interests[1]").value("Networking"));
    }

    @Test
    void updateProfile_emptyInterestsList_clearsList() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        // Set initial interests
        MentorProfileRequest update1 = new MentorProfileRequest();
        update1.setInterests(List.of("AI", "Systems"));
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(2));

        // Clear with empty list
        MentorProfileRequest update2 = new MentorProfileRequest();
        update2.setInterests(List.of());
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(0));

        // Verify persisted
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.interests.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════
    //  Boundary validation
    // ═══════════════════════════════════════════════════════

    @Test
    void updateProfile_firstNameExactly1Char_passes() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setFirstName("A");

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("A"));
    }

    @Test
    void updateProfile_firstNameExactly100Chars_passes() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        String name100 = "A".repeat(100);
        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setFirstName(name100);

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(name100));
    }

    @Test
    void updateProfile_bioExactly1000Chars_passes() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        String bio1000 = "A".repeat(1000);
        MentorProfileRequest update = new MentorProfileRequest();
        update.setBio(bio1000);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(bio1000));
    }

    // ═══════════════════════════════════════════════════════
    //  Unicode / special characters
    // ═══════════════════════════════════════════════════════

    @Test
    void updateProfile_unicodeCharacters_persistCorrectly() throws Exception {
        String token = registerAndLogin("user@test.com", true);

        MentorProfileRequest update = new MentorProfileRequest();
        update.setFirstName("Övgü");
        update.setLastName("Sarıoğlu");
        update.setBio("Türkçe karakterler: ş, ç, ğ, ı, ö, ü");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Övgü"))
                .andExpect(jsonPath("$.lastName").value("Sarıoğlu"))
                .andExpect(jsonPath("$.bio").value("Türkçe karakterler: ş, ç, ğ, ı, ö, ü"));

        // Verify persisted
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.firstName").value("Övgü"))
                .andExpect(jsonPath("$.lastName").value("Sarıoğlu"));
    }

    // ═══════════════════════════════════════════════════════
    //  Unknown fields ignored (security)
    // ═══════════════════════════════════════════════════════

    @Test
    void updateProfile_unknownFieldsIgnored_emailNotChanged() throws Exception {
        String token = registerAndLogin("original@test.com", false);

        // Try to change email and passwordHash through PATCH
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"hacked@evil.com\", \"passwordHash\": \"stolen\", \"goals\": \"legit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value("legit"));

        // Verify email was NOT changed
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.email").value("original@test.com"));
    }

    @Test
    void updateProfile_cannotSetCancelCount() throws Exception {
        String token = registerAndLogin("mentee@test.com", false);

        // Try to set cancelCount through PATCH (field not in DTO)
        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelCount\": 999, \"goals\": \"legit\"}"))
                .andExpect(status().isOk());

        // Verify cancelCount was NOT changed
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.cancelCount").value(0));
    }

    @Test
    void updateProfile_cannotSetCurrentMenteeCount() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        // Try to set currentMenteeCount through PATCH (field not in DTO)
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentMenteeCount\": 99, \"bio\": \"legit\"}"))
                .andExpect(status().isOk());

        // Verify currentMenteeCount was NOT changed
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentMenteeCount").value(0));
    }

    // ═══════════════════════════════════════════════════════
    //  Data integrity — update only touches specified fields
    // ═══════════════════════════════════════════════════════

    @Test
    void updateMenteeFields_commonFieldsUnchanged() throws Exception {
        String token = registerAndLogin("Ali", "Yilmaz", "mentee@test.com", false);

        // Update only mentee-specific fields
        MenteeProfileRequest update = new MenteeProfileRequest();
        update.setGoals("New goals");
        update.setMajor("Physics");

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // Verify common fields untouched
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yilmaz"))
                .andExpect(jsonPath("$.email").value("mentee@test.com"))
                .andExpect(jsonPath("$.goals").value("New goals"))
                .andExpect(jsonPath("$.major").value("Physics"));
    }

    // ═══════════════════════════════════════════════════════
    //  List endpoints — no password hash exposure
    // ═══════════════════════════════════════════════════════

    @Test
    void getAllUsers_doesNotExposePasswordHash() throws Exception {
        String token = registerAndLogin("user@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(page.get("totalElements"), "Page response should contain totalElements");
        for (JsonNode user : page.get("content")) {
            assertNull(user.get("passwordHash"), "passwordHash should not be in list response");
        }
    }

    @Test
    void getAllMentors_doesNotExposePasswordHash() throws Exception {
        String token = registerAndLogin("mentor@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/mentors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(page.get("totalElements"), "Page response should contain totalElements");
        for (JsonNode mentor : page.get("content")) {
            assertNull(mentor.get("passwordHash"), "passwordHash should not be in mentors list");
            assertNotNull(mentor.get("role"), "role should be present");
        }
    }

    @Test
    void getAllMentees_doesNotExposePasswordHash() throws Exception {
        registerAndLogin("mentee@test.com", false);
        String mentorToken = registerAndLogin("mentor@test.com", true);

        MvcResult result = mockMvc.perform(get("/api/users/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(page.get("totalElements"), "Page response should contain totalElements");
        for (JsonNode mentee : page.get("content")) {
            assertNull(mentee.get("passwordHash"), "passwordHash should not be in mentees list");
            assertNotNull(mentee.get("role"), "role should be present");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Deleted user with valid JWT
    // ═══════════════════════════════════════════════════════

    @Test
    void getOwnProfile_deletedUser_returns404() throws Exception {
        String token = registerAndLogin("user@test.com", false);
        Long userId = getUserId(token);

        // Delete the user directly from DB
        userRepository.deleteById(userId);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
