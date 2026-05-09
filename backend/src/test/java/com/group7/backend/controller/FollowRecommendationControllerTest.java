package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.FollowRecommendationService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.ranking.ScoreResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FollowRecommendationController}: HTTP
 * shape, page-size clamp, and the auth gate. The
 * {@link FollowRecommendationService} is mocked so this test focuses on
 * controller wiring rather than scoring behaviour (covered by the
 * ranker + service tests).
 */
@WebMvcTest(FollowRecommendationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FollowRecommendationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FollowRecommendationService service;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    @Test
    void recommend_returnsPagedBodyShape() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        FollowRecommendationResponse rec = FollowRecommendationResponse.from(
                mentor(42L, "Mira"),
                new ScoreResult(11, List.of("shared-interest:AI", "followed-by-2-of-your-follows")));
        Page<FollowRecommendationResponse> page = new PageImpl<>(List.of(rec));
        when(service.recommend(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/me/follow-recommendations")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.content[0].firstName").value("Mira"))
                .andExpect(jsonPath("$.content[0].lastName").value("L"))
                .andExpect(jsonPath("$.content[0].role").value("MENTOR"))
                .andExpect(jsonPath("$.content[0].score").value(11))
                .andExpect(jsonPath("$.content[0].factors[0]").value("shared-interest:AI"))
                .andExpect(jsonPath("$.content[0].factors[1]").value("followed-by-2-of-your-follows"));
    }

    @Test
    void recommend_clampsExcessiveSize() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(service.recommend(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/users/me/follow-recommendations")
                        .param("size", "100000")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).recommend(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isLessThanOrEqualTo(100);
    }

    @Test
    void recommend_passesPageNumberThrough() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(service.recommend(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/users/me/follow-recommendations")
                        .param("page", "3")
                        .param("size", "5")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).recommend(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void recommend_unauthenticated_returns403() throws Exception {
        // Mirrors FollowControllerTest's expectation for unauthenticated
        // calls under the SecurityConfig in this project.
        mockMvc.perform(get("/api/users/me/follow-recommendations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recommend_viewerNotFound_returns404() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(service.recommend(eq(1L), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 1"));

        mockMvc.perform(get("/api/users/me/follow-recommendations")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void recommend_routeDoesNotCollideWithFollowGraphRoutes() throws Exception {
        // The /me path segment must not match FollowController's
        // /{id:\\d+}/... regex; this is a sanity check that adding
        // /api/users/me/... didn't break the existing routing.
        mockMenteeJwt("alice-token", 1L);
        when(service.recommend(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/users/me/follow-recommendations")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk());
    }

    private static Mentor mentor(Long id, String firstName) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(firstName.toLowerCase() + "@ex.com");
        return m;
    }
}
