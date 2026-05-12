package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AdminUserBanStatusFilter;
import com.group7.backend.dto.request.AdminUserRoleFilter;
import com.group7.backend.dto.response.AdminUserListItem;
import com.group7.backend.service.AdminUserQueryService;
import com.group7.backend.service.BanService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.SpamDetectionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for GET /api/admin/users (#569). Covers auth gating,
 * paged body shape, page-size clamping, and the three optional filter
 * parameters individually + combined.
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminUserListControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminUserQueryService adminUserQueryService;
    // AdminController also depends on these two — provided as mocks so the
    // slice context boots even though these endpoints don't exercise them.
    @MockitoBean private BanService banService;
    @MockitoBean private SpamDetectionService spamDetectionService;
    @MockitoBean private JwtService jwtService;

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String MENTEE_TOKEN = "mentee-token";

    private void mockJwt(String token, String role, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn(role.toLowerCase() + "@example.com");
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private AdminUserListItem sampleItem(Long id, String role, String banStatus) {
        return new AdminUserListItem(
                id,
                "First" + id,
                "Last" + id,
                "user" + id + "@example.com",
                role,
                banStatus,
                Boolean.FALSE,
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void listUsers_nonAdmin_returns403() throws Exception {
        mockJwt(MENTEE_TOKEN, "MENTEE", 1L);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + MENTEE_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_asAdmin_returnsPagedBody() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        sampleItem(1L, "MENTOR", "NONE"),
                        sampleItem(2L, "MENTEE", "ACTIVE"))));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("First1"))
                .andExpect(jsonPath("$.content[0].role").value("MENTOR"))
                .andExpect(jsonPath("$.content[0].banStatus").value("NONE"))
                .andExpect(jsonPath("$.content[0].suspectedBot").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].role").value("MENTEE"))
                .andExpect(jsonPath("$.content[1].banStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listUsers_clampsSizeOver100() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/users?size=9999")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserQueryService).listUsers(
                isNull(), isNull(), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listUsers_filterByRole_mentor() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(
                eq(AdminUserRoleFilter.MENTOR), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleItem(1L, "MENTOR", "NONE"))));

        mockMvc.perform(get("/api/admin/users?role=MENTOR")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("MENTOR"));

        verify(adminUserQueryService).listUsers(
                eq(AdminUserRoleFilter.MENTOR), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listUsers_filterByBanStatus_active() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(
                isNull(), eq(AdminUserBanStatusFilter.ACTIVE), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleItem(2L, "MENTEE", "ACTIVE"))));

        mockMvc.perform(get("/api/admin/users?banStatus=ACTIVE")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].banStatus").value("ACTIVE"));

        verify(adminUserQueryService).listUsers(
                isNull(), eq(AdminUserBanStatusFilter.ACTIVE), isNull(), any(Pageable.class));
    }

    @Test
    void listUsers_keywordFilter_passesQDownstream() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(
                isNull(), isNull(), eq("ayse"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleItem(1L, "MENTOR", "NONE"))));

        mockMvc.perform(get("/api/admin/users?q=ayse")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(adminUserQueryService).listUsers(
                isNull(), isNull(), eq("ayse"), any(Pageable.class));
    }

    @Test
    void listUsers_combinedFilters_allPassedThrough() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.listUsers(
                eq(AdminUserRoleFilter.MENTEE),
                eq(AdminUserBanStatusFilter.NONE),
                eq("ali"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleItem(2L, "MENTEE", "NONE"))));

        mockMvc.perform(get("/api/admin/users?role=MENTEE&banStatus=NONE&q=ali&page=1&size=5")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("MENTEE"))
                .andExpect(jsonPath("$.content[0].banStatus").value("NONE"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserQueryService).listUsers(
                eq(AdminUserRoleFilter.MENTEE),
                eq(AdminUserBanStatusFilter.NONE),
                eq("ali"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listUsers_invalidRoleEnum_returns400() throws Exception {
        // Spring's StringToEnumConverter rejects unknown values; this guards
        // the "enum filter (not free-form string)" decision in the plan.
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);

        mockMvc.perform(get("/api/admin/users?role=BOGUS")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isBadRequest());
    }
}
