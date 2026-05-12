package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.EditProfileRequest;
import com.group7.backend.dto.request.MentorProfileRequest;
import com.group7.backend.dto.request.MenteeProfileRequest;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.UserProfileResponse;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    // #518: UserController constructor now also depends on MentorRatingService.
    @MockitoBean
    private com.group7.backend.service.MentorRatingService mentorRatingService;

    @MockitoBean
    private JwtService jwtService;

    private static final String TEST_TOKEN = "test-jwt-token";

    private void mockValidToken(Long userId, String role) {
        when(jwtService.isTokenValid(TEST_TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TEST_TOKEN)).thenReturn("user@example.com");
        when(jwtService.extractUserId(TEST_TOKEN)).thenReturn(userId);
        when(jwtService.extractRole(TEST_TOKEN)).thenReturn(role);
    }

    /**
     * Wrapper helpers for the new UserProfileResponse return type (#343).
     * The endpoints under test now return UserProfileResponse, which uses
     * {@code @JsonUnwrapped} to keep the wire shape flat — top-level
     * mentor/mentee fields stay where they were, with follower/following
     * counts appended. The body assertions in these tests target the
     * top-level fields, so they keep passing without needing to change
     * the assertion JSON paths.
     */
    private UserProfileResponse wrapMentor() {
        return new UserProfileResponse(buildMentorResponse(), 0L, 0L, false);
    }

    private UserProfileResponse wrapMentee() {
        return new UserProfileResponse(buildMenteeResponse(), 0L, 0L, false);
    }

    private MentorResponse buildMentorResponse() {
        MentorResponse response = new MentorResponse();
        response.setId(1L);
        response.setFirstName("Ayse");
        response.setLastName("Demir");
        response.setEmail("ayse@example.com");
        response.setProfilePhoto("https://example.com/photo.jpg");
        response.setIsEmailVerified(false);
        response.setCreatedAt(OffsetDateTime.of(2026, 3, 15, 10, 0, 0, 0, ZoneOffset.UTC));
        response.setRole("MENTOR");
        response.setBio("Expert in CS");
        response.setField("Computer Science");
        response.setExpertise("Backend");
        response.setAffiliation("Bogazici University");
        response.setInterests(List.of("AI", "Systems"));
        response.setMaxMenteeCapacity(3);
        response.setCurrentMenteeCount(1);
        response.setPreferredMenteeSkills(List.of("Java", "Python"));
        response.setPreferredMenteeMajor("Computer Engineering");
        response.setMentoringGoals("Help students with career guidance");
        response.setMentorshipDuration(3);
        return response;
    }

    private MenteeResponse buildMenteeResponse() {
        MenteeResponse response = new MenteeResponse();
        response.setId(2L);
        response.setFirstName("Ali");
        response.setLastName("Yilmaz");
        response.setEmail("ali@example.com");
        response.setProfilePhoto(null);
        response.setIsEmailVerified(false);
        response.setCreatedAt(OffsetDateTime.of(2026, 3, 16, 10, 0, 0, 0, ZoneOffset.UTC));
        response.setRole("MENTEE");
        response.setProfileVisibility(true);
        response.setGoals("Learn AI");
        response.setMajor("Computer Engineering");
        response.setInterests(List.of("AI", "Web Development"));
        response.setCareerInterest("Data Science");
        response.setSkills(List.of("Python", "SQL"));
        response.setMeetingFreqPref("Weekly");
        response.setBackgroundInfo("2nd year student");
        response.setCancelCount(0);
        return response;
    }

    // ── GET /api/users/me — Mentor ──────────────────────────

    @Test
    void getOwnProfile_mentor_returns200WithAllFields() throws Exception {
        mockValidToken(1L, "MENTOR");
        when(userService.getOwnUserProfile(1L)).thenReturn(wrapMentor());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Ayse"))
                .andExpect(jsonPath("$.lastName").value("Demir"))
                .andExpect(jsonPath("$.email").value("ayse@example.com"))
                .andExpect(jsonPath("$.profilePhoto").value("https://example.com/photo.jpg"))
                .andExpect(jsonPath("$.isEmailVerified").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andExpect(jsonPath("$.bio").value("Expert in CS"))
                .andExpect(jsonPath("$.field").value("Computer Science"))
                .andExpect(jsonPath("$.expertise").value("Backend"))
                .andExpect(jsonPath("$.affiliation").value("Bogazici University"))
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.interests[0]").value("AI"))
                .andExpect(jsonPath("$.maxMenteeCapacity").value(3))
                .andExpect(jsonPath("$.currentMenteeCount").value(1))
                .andExpect(jsonPath("$.preferredMenteeSkills.length()").value(2))
                .andExpect(jsonPath("$.preferredMenteeMajor").value("Computer Engineering"))
                .andExpect(jsonPath("$.mentoringGoals").value("Help students with career guidance"))
                .andExpect(jsonPath("$.mentorshipDuration").value(3));
    }

    @Test
    void getOwnProfile_mentor_doesNotExposePasswordHash() throws Exception {
        mockValidToken(1L, "MENTOR");
        when(userService.getOwnUserProfile(1L)).thenReturn(wrapMentor());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ── GET /api/users/me — Mentee ──────────────────────────

    @Test
    void getOwnProfile_mentee_returns200WithAllFields() throws Exception {
        mockValidToken(2L, "MENTEE");
        when(userService.getOwnUserProfile(2L)).thenReturn(wrapMentee());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yilmaz"))
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.isEmailVerified").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.profileVisibility").value(true))
                .andExpect(jsonPath("$.goals").value("Learn AI"))
                .andExpect(jsonPath("$.major").value("Computer Engineering"))
                .andExpect(jsonPath("$.interests.length()").value(2))
                .andExpect(jsonPath("$.careerInterest").value("Data Science"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0]").value("Python"))
                .andExpect(jsonPath("$.meetingFreqPref").value("Weekly"))
                .andExpect(jsonPath("$.backgroundInfo").value("2nd year student"))
                .andExpect(jsonPath("$.cancelCount").value(0));
    }

    @Test
    void getOwnProfile_mentee_doesNotExposeMentorFields() throws Exception {
        mockValidToken(2L, "MENTEE");
        when(userService.getOwnUserProfile(2L)).thenReturn(wrapMentee());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.expertise").doesNotExist())
                .andExpect(jsonPath("$.maxMenteeCapacity").doesNotExist());
    }

    // ── GET /api/users/me — Auth ────────────────────────────

    @Test
    void getOwnProfile_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOwnProfile_invalidToken_returns403() throws Exception {
        when(jwtService.isTokenValid("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/users/{id} ─────────────────────────────────

    @Test
    void getProfileById_existingUser_returns200() throws Exception {
        mockValidToken(1L, "MENTOR");
        when(userService.getUserProfile(2L, 1L)).thenReturn(wrapMentee());

        mockMvc.perform(get("/api/users/2")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.role").value("MENTEE"));
    }

    @Test
    void getProfileById_notFound_returns404() throws Exception {
        mockValidToken(1L, "MENTOR");
        when(userService.getUserProfile(999L, 1L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/users/999")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found with id: 999"));
    }

    @Test
    void getProfileById_noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfileById_mentorProfile_returnsAllMentorFields() throws Exception {
        mockValidToken(2L, "MENTEE");
        when(userService.getUserProfile(1L, 2L)).thenReturn(wrapMentor());

        mockMvc.perform(get("/api/users/1")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andExpect(jsonPath("$.bio").value("Expert in CS"))
                .andExpect(jsonPath("$.expertise").value("Backend"))
                .andExpect(jsonPath("$.maxMenteeCapacity").value(3));
    }

    @Test
    void getProfileById_doesNotExposePasswordHash() throws Exception {
        mockValidToken(1L, "MENTOR");
        when(userService.getUserProfile(2L, 1L)).thenReturn(wrapMentee());

        mockMvc.perform(get("/api/users/2")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getProfileById_menteeViewsMentee_returns403() throws Exception {
        mockValidToken(2L, "MENTEE");
        when(userService.getUserProfile(3L, 2L))
                .thenThrow(new ProfileNotVisibleException("Mentees cannot view other mentee profiles"));

        mockMvc.perform(get("/api/users/3")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Mentees cannot view other mentee profiles"));
    }

    // ── PATCH /api/users/me/mentor and /me/mentee — Happy paths ───────────────────

    @Test
    void updateOwnProfile_mentorFields_returns200() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setBio("Updated bio");
        request.setExpertise("Full Stack");
        request.setInterests(List.of("AI", "ML", "Cloud"));

        MentorResponse response = buildMentorResponse();
        response.setBio("Updated bio");
        response.setExpertise("Full Stack");
        response.setInterests(List.of("AI", "ML", "Cloud"));

        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.expertise").value("Full Stack"))
                .andExpect(jsonPath("$.interests.length()").value(3));
    }

    @Test
    void updateOwnProfile_commonFields_returns200() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setFirstName("NewFirst");
        request.setLastName("NewLast");

        MentorResponse response = buildMentorResponse();
        response.setFirstName("NewFirst");
        response.setLastName("NewLast");

        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("NewFirst"))
                .andExpect(jsonPath("$.lastName").value("NewLast"));
    }

    @Test
    void updateOwnProfile_menteeFields_returns200() throws Exception {
        mockValidToken(2L, "MENTEE");

        MenteeProfileRequest request = new MenteeProfileRequest();
        request.setGoals("Learn distributed systems");
        request.setSkills(List.of("Java", "Spring", "Docker"));
        request.setProfileVisibility(false);

        MenteeResponse response = buildMenteeResponse();
        response.setGoals("Learn distributed systems");
        response.setSkills(List.of("Java", "Spring", "Docker"));
        response.setProfileVisibility(false);

        when(userService.updateProfile(eq(2L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentee")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value("Learn distributed systems"))
                .andExpect(jsonPath("$.skills.length()").value(3))
                .andExpect(jsonPath("$.profileVisibility").value(false));
    }

    @Test
    void updateOwnProfile_emptyBody_returns200() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse response = buildMentorResponse();
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/users/me/mentor — Validation errors ─────────────

    @Test
    void updateOwnProfile_blankFirstName_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setFirstName("");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages.firstName").exists());
    }

    @Test
    void updateOwnProfile_blankLastName_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setLastName("");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.lastName").exists());
    }

    @Test
    void updateOwnProfile_firstNameTooLong_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setFirstName("A".repeat(101));

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.firstName").exists());
    }

    @Test
    void updateOwnProfile_bioTooLong_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setBio("A".repeat(1001));

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.bio").exists());
    }

    @Test
    void updateOwnProfile_negativeMenteeCapacity_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setMaxMenteeCapacity(-1);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.maxMenteeCapacity").exists());
    }

    @Test
    void updateOwnProfile_zeroMentorshipDuration_returns400() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setMentorshipDuration(0);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.mentorshipDuration").exists());
    }

    @Test
    void updateOwnProfile_multipleValidationErrors_returnsAll() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorProfileRequest request = new MentorProfileRequest();
        request.setFirstName("");
        request.setLastName("");
        request.setMaxMenteeCapacity(-5);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages.firstName").exists())
                .andExpect(jsonPath("$.messages.lastName").exists())
                .andExpect(jsonPath("$.messages.maxMenteeCapacity").exists());
    }

    // ── PATCH /api/users/me — Auth ──────────────────────────

    @Test
    void updateOwnProfile_noToken_returns403() throws Exception {
        MentorProfileRequest request = new MentorProfileRequest();
        request.setBio("Trying to update");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOwnProfile_invalidToken_returns403() throws Exception {
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        MentorProfileRequest request = new MentorProfileRequest();
        request.setBio("Trying to update");

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── Null fields pass validation (skip update) ───────────

    @Test
    void updateOwnProfile_nullFirstName_passesValidation() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse response = buildMentorResponse();
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Just updating bio\"}"))
                .andExpect(status().isOk());
    }

    // ── Boundary validation ─────────────────────────────────

    @Test
    void updateOwnProfile_firstNameExactly1Char_passes() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse response = buildMentorResponse();
        response.setFirstName("A");
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("A"));
    }

    @Test
    void updateOwnProfile_firstNameExactly100Chars_passes() throws Exception {
        mockValidToken(1L, "MENTOR");

        String name100 = "A".repeat(100);
        MentorResponse response = buildMentorResponse();
        response.setFirstName(name100);
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"" + name100 + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateOwnProfile_bioExactly1000Chars_passes() throws Exception {
        mockValidToken(1L, "MENTOR");

        String bio1000 = "A".repeat(1000);
        MentorResponse response = buildMentorResponse();
        response.setBio(bio1000);
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        MentorProfileRequest request = new MentorProfileRequest();
        request.setBio(bio1000);

        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ── Unknown fields ignored ──────────────────────────────

    @Test
    void updateOwnProfile_unknownFieldsIgnored() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse response = buildMentorResponse();
        when(userService.updateProfile(eq(1L), any(EditProfileRequest.class))).thenReturn(response);

        // Send fields that don't exist on MentorProfileRequest — Jackson should ignore them
        mockMvc.perform(patch("/api/users/me/mentor")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"hack@evil.com\", \"passwordHash\": \"stolen\", \"bio\": \"legit update\"}"))
                .andExpect(status().isOk());
    }

    // ── List endpoints don't expose passwordHash ────────────

    @Test
    void getAllUsers_doesNotExposePasswordHash() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse mentorResp = buildMentorResponse();
        when(userService.getAllUsersFiltered(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mentorResp)));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].firstName").value("Ayse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllMentors_doesNotExposePasswordHash() throws Exception {
        mockValidToken(1L, "MENTOR");

        MentorResponse mentorResp = buildMentorResponse();
        when(userService.getAllMentors(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mentorResp)));

        mockMvc.perform(get("/api/users/mentors")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].role").value("MENTOR"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllMentees_doesNotExposePasswordHash() throws Exception {
        mockValidToken(1L, "MENTOR");

        MenteeResponse menteeResp = buildMenteeResponse();
        when(userService.getAllMentees(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(menteeResp)));

        mockMvc.perform(get("/api/users/mentees")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].role").value("MENTEE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
