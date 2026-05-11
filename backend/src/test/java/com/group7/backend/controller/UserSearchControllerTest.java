package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.SearchRole;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@code GET /api/users/search} (#262). Focus is the controller-side
 * concerns: role gating delegated to the service, query-parameter binding,
 * pageable clamping, and exception → status mapping. The search algorithm
 * itself is exercised in {@code UserSearchIntegrationTest}.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserSearchControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtService jwtService;

    private static final String TOKEN = "test-jwt";

    private void mockValidToken(Long userId, String role) {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn("u@example.com");
        when(jwtService.extractUserId(TOKEN)).thenReturn(userId);
        when(jwtService.extractRole(TOKEN)).thenReturn(role);
    }

    private static Page<ProfileResponse> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private static Page<ProfileResponse> singletonMentorPage() {
        MentorResponse m = new MentorResponse();
        m.setId(7L);
        m.setFirstName("Mira");
        return new PageImpl<>(List.of(m));
    }

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    void searchAsMentee_searchingMentor_returns200() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(eq(SearchRole.MENTOR), any(), any(), any(), any(),
                anyBoolean(), eq(1L), any(Pageable.class)))
                .thenReturn(singletonMentorPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void searchAsMentor_searchingMentee_returns200() throws Exception {
        mockValidToken(2L, "MENTOR");
        when(userService.searchUsers(eq(SearchRole.MENTEE), any(), any(), any(), any(),
                anyBoolean(), eq(2L), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void searchAsAdmin_searchingMentor_returns200() throws Exception {
        mockValidToken(3L, "ADMIN");
        when(userService.searchUsers(eq(SearchRole.MENTOR), any(), any(), any(), any(),
                anyBoolean(), eq(3L), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void searchAsAdmin_searchingMentee_returns200() throws Exception {
        mockValidToken(3L, "ADMIN");
        when(userService.searchUsers(eq(SearchRole.MENTEE), any(), any(), any(), any(),
                anyBoolean(), eq(3L), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    // ── Service-layer rejections surface as the right status ──────────────────

    @Test
    void searchAsMentee_searchingMentee_returns403() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(eq(SearchRole.MENTEE), any(), any(), any(), any(),
                anyBoolean(), eq(1L), any(Pageable.class)))
                .thenThrow(new ProfileNotVisibleException("Mentees cannot search for other mentees"));

        mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAsMentor_searchingMentor_returns403() throws Exception {
        mockValidToken(2L, "MENTOR");
        when(userService.searchUsers(eq(SearchRole.MENTOR), any(), any(), any(), any(),
                anyBoolean(), eq(2L), any(Pageable.class)))
                .thenThrow(new ProfileNotVisibleException("Mentors cannot search for other mentors"));

        mockMvc.perform(get("/api/users/search?role=MENTOR")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAsAdmin_hasAvailabilityTrue_returns400() throws Exception {
        mockValidToken(3L, "ADMIN");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                eq(true), eq(3L), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException(
                        "hasAvailability filter is not applicable for admin searches"));

        mockMvc.perform(get("/api/users/search?role=MENTOR&hasAvailability=true")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchHasAvailability_requesterMissingSlots_returns400() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                eq(true), eq(1L), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException(
                        "Set your availability before filtering by overlap"));

        mockMvc.perform(get("/api/users/search?role=MENTOR&hasAvailability=true")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
    }

    // ── Spring-side input validation ──────────────────────────────────────────

    @Test
    void searchInvalidRole_returns400() throws Exception {
        mockValidToken(1L, "MENTEE");

        mockMvc.perform(get("/api/users/search?role=ADMIN")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchMissingRole_returns400() throws Exception {
        mockValidToken(1L, "MENTEE");

        mockMvc.perform(get("/api/users/search")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchUnauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/search?role=MENTOR"))
                .andExpect(status().isForbidden());
    }

    // ── Short-keyword and edge-case bindings ─────────────────────────────────

    @Test
    void searchKeywordTooShort_stillReaches200_serviceTreatsAsNoFilter() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        // q="ab" — service-side normaliser returns null for length<3; controller
        // forwards as-is. Documented as "no keyword filter" semantic.
        mockMvc.perform(get("/api/users/search?role=MENTOR&q=ab")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        // Verify the raw "ab" was forwarded — normalisation is the service's job.
        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        verify(userService).searchUsers(any(), kw.capture(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class));
        assertThat(kw.getValue()).isEqualTo("ab");
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    @Test
    void searchPaginationParamsHonored() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR&page=2&size=5")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), pg.capture());
        assertThat(pg.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pg.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void searchOversizedPage_clampedTo100() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR&size=10000")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), pg.capture());
        assertThat(pg.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void searchNegativePage_clampedToZero() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR&page=-5")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), pg.capture());
        assertThat(pg.getValue().getPageNumber()).isEqualTo(0);
    }

    // ── Multi-value filter binding ───────────────────────────────────────────

    @Test
    void searchMultiValueInterests_forwardedAsList() throws Exception {
        mockValidToken(1L, "MENTEE");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/search?role=MENTOR&interests=AI&interests=Databases")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> interests = ArgumentCaptor.forClass(List.class);
        verify(userService).searchUsers(any(), any(), interests.capture(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class));
        assertThat(interests.getValue()).containsExactly("AI", "Databases");
    }

    @Test
    void searchAllParams_forwardedToService() throws Exception {
        mockValidToken(2L, "MENTOR");
        when(userService.searchUsers(any(), any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(Pageable.class)))
                .thenReturn(emptyPage());

        // Use param() rather than inline query string so MockMvc URL-decodes
        // the multi-word "Computer Science" value before binding.
        mockMvc.perform(get("/api/users/search")
                        .param("role", "MENTEE")
                        .param("q", "research")
                        .param("interests", "AI")
                        .param("skills", "Java")
                        .param("major", "Computer Science")
                        .param("hasAvailability", "false")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        verify(userService).searchUsers(
                eq(SearchRole.MENTEE),
                eq("research"),
                eq(List.of("AI")),
                eq(List.of("Java")),
                eq("Computer Science"),
                eq(false),
                eq(2L),
                any(Pageable.class));
    }
}
