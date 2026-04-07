package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MenteeCandidateResponse;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchingService matchingService;

    @MockitoBean
    private JwtService jwtService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void mockValidMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentee@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockValidMentorJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentor@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTOR");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void getTopMentorsReturns200ForMentee() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        MentorMatchResponse match = new MentorMatchResponse();
        match.setFirstName("Ahmet");
        match.setMatchScore(10);
        when(matchingService.getTopMentors(eq(1L), any())).thenReturn(List.of(match));

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Ahmet"))
                .andExpect(jsonPath("$[0].matchScore").value(10));
    }

    @Test
    void getTopMentorsWithKeywordReturns200() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(matchingService.getTopMentors(eq(1L), eq("java"))).thenReturn(List.of());

        mockMvc.perform(get("/api/matching/mentors?keyword=java")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getTopMentorsReturns403ForMentorRole() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopMentorsReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/matching/mentors"))
                .andExpect(status().isForbidden()); // Spring Security returns 403 for unauthenticated by default
    }

    @Test
    void getTopMentorsReturns403WhenAlreadyHasMentor() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(matchingService.getTopMentors(eq(1L), any()))
                .thenThrow(new com.group7.backend.exception.MatchingNotAllowedException(
                        "You already have an active mentor"));

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    // ── Candidate mentees endpoint ──────────────────────────────────────────

    @Test
    void getCandidateMenteesReturns200ForMentor() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        MenteeCandidateResponse candidate = new MenteeCandidateResponse();
        candidate.setFirstName("Elif");
        candidate.setMajor("Computer Science");
        when(matchingService.getCandidateMentees(eq(2L), any())).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Elif"))
                .andExpect(jsonPath("$[0].major").value("Computer Science"));
    }

    @Test
    void getCandidateMenteesWithKeywordReturns200() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(matchingService.getCandidateMentees(eq(2L), eq("java"))).thenReturn(List.of());

        mockMvc.perform(get("/api/matching/mentees?keyword=java")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getCandidateMenteesReturns403ForMenteeRole() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCandidateMenteesReturns403WithoutToken() throws Exception {
        mockMvc.perform(get("/api/matching/mentees"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCandidateMenteesReturns403WhenAtCapacity() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(matchingService.getCandidateMentees(eq(2L), any()))
                .thenThrow(new com.group7.backend.exception.MatchingNotAllowedException(
                        "You have reached your maximum mentee capacity"));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCandidateMenteesReturns404WhenMentorNotFound() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(matchingService.getCandidateMentees(eq(2L), any()))
                .thenThrow(new com.group7.backend.exception.ResourceNotFoundException("Mentor not found"));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCandidateMenteesReturnsEmptyArrayWhenNoCandidates() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(matchingService.getCandidateMentees(eq(2L), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getCandidateMenteesResponseExcludesPrivateFields() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        MenteeCandidateResponse candidate = new MenteeCandidateResponse();
        candidate.setFirstName("Elif");
        candidate.setGoals("Learn AI");
        candidate.setMajor("CS");
        candidate.setInterests(List.of("AI"));
        candidate.setSkills(List.of("Java"));
        when(matchingService.getCandidateMentees(eq(2L), any())).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Elif"))
                .andExpect(jsonPath("$[0].goals").value("Learn AI"))
                .andExpect(jsonPath("$[0].interests[0]").value("AI"))
                .andExpect(jsonPath("$[0].skills[0]").value("Java"))
                .andExpect(jsonPath("$[0].lastName").doesNotExist())
                .andExpect(jsonPath("$[0].profilePhoto").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void getCandidateMenteesReturnsMultipleCandidates() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        MenteeCandidateResponse c1 = new MenteeCandidateResponse();
        c1.setFirstName("Elif");
        MenteeCandidateResponse c2 = new MenteeCandidateResponse();
        c2.setFirstName("Ayse");
        when(matchingService.getCandidateMentees(eq(2L), any())).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Elif"))
                .andExpect(jsonPath("$[1].firstName").value("Ayse"));
    }

    @Test
    void getCandidateMenteesCapacityErrorResponseFormat() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(matchingService.getCandidateMentees(eq(2L), any()))
                .thenThrow(new com.group7.backend.exception.MatchingNotAllowedException(
                        "You have reached your maximum mentee capacity"));

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You have reached your maximum mentee capacity"));
    }
}
