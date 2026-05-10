package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.CreateRatingRequest;
import com.group7.backend.dto.response.RatingResponse;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorshipRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MentorshipRatingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MentorshipRatingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MentorshipRatingService ratingService;
    @MockitoBean private JwtService jwtService;

    private void mockJwt(String token, Long userId, String role) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn(userId + "@test.com");
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private CreateRatingRequest validBody() {
        CreateRatingRequest r = new CreateRatingRequest();
        r.setStars(5);
        r.setComment("Great mentor");
        return r;
    }

    private RatingResponse stubResponse(Long id) {
        RatingResponse r = new RatingResponse();
        r.setId(id);
        r.setMentorshipId(10L);
        r.setRaterUserId(1L);
        r.setRatedUserId(2L);
        r.setStars(5);
        r.setComment("Great mentor");
        r.setCreatedAt(OffsetDateTime.now());
        return r;
    }

    // ── POST /api/mentorships/{id}/ratings ───────────────────────────────────

    @Test
    void createReturns201WithRating() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        when(ratingService.create(eq(1L), eq(10L), any(CreateRatingRequest.class)))
                .thenReturn(stubResponse(99L));

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.mentorshipId").value(10))
                .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void createReturns400WhenStarsBelowOne() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        CreateRatingRequest body = validBody();
        body.setStars(0);

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenStarsAboveFive() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        CreateRatingRequest body = validBody();
        body.setStars(6);

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400WhenStarsMissing() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        CreateRatingRequest body = new CreateRatingRequest();
        body.setComment("missing stars");

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns404WhenMentorshipNotFoundOrCallerNotParticipant() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        when(ratingService.create(eq(1L), eq(10L), any(CreateRatingRequest.class)))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns409WhenAlreadyRated() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        when(ratingService.create(eq(1L), eq(10L), any(CreateRatingRequest.class)))
                .thenThrow(new MentorshipRequestException("You have already submitted a rating for this mentorship"));

        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already submitted a rating for this mentorship"));
    }

    @Test
    void createReturns403WithoutToken() throws Exception {
        // No Authorization header → JwtAuthenticationFilter doesn't set principal,
        // @PreAuthorize("isAuthenticated()") rejects with 403 (Spring Security default).
        mockMvc.perform(post("/api/mentorships/10/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/mentorships/{id}/ratings ────────────────────────────────────

    @Test
    void listReturns200WithRatings() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        when(ratingService.list(eq(1L), eq(10L)))
                .thenReturn(List.of(stubResponse(99L), stubResponse(100L)));

        mockMvc.perform(get("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[1].id").value(100));
    }

    @Test
    void listReturns404WhenMentorshipNotFoundOrCallerNotParticipant() throws Exception {
        mockJwt("token", 1L, "MENTEE");
        when(ratingService.list(eq(1L), eq(10L)))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/10/ratings")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound());
    }
}
