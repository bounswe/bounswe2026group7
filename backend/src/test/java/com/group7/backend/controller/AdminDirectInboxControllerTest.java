package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.AdminDirectInboxItem;
import com.group7.backend.service.AdminDirectInboxService;
import com.group7.backend.service.JwtService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDirectInboxController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminDirectInboxControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminDirectInboxService inboxService;
    @MockitoBean private JwtService jwtService;

    private void mockJwt(String token, String role, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn(role.toLowerCase() + "@example.com");
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private AdminDirectInboxItem sampleItem(boolean peerIsAdmin) {
        AdminDirectInboxItem item = new AdminDirectInboxItem();
        item.setConversationId(901L);
        item.setPeerId(42L);
        item.setPeerFirstName(peerIsAdmin ? "Ops" : "Mira");
        item.setPeerIsAdmin(peerIsAdmin);
        item.setLastMessageContent("Please review the guidelines.");
        item.setLastMessageSentAt(OffsetDateTime.parse("2026-05-12T10:00:00Z"));
        item.setUnreadCount(1L);
        return item;
    }

    @Test
    void listInbox_asMentee_returns200WithPagedBody() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(inboxService.listInbox(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleItem(true))));

        mockMvc.perform(get("/api/conversations/admin-direct")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].conversationId").value(901))
                .andExpect(jsonPath("$.content[0].peerId").value(42))
                .andExpect(jsonPath("$.content[0].peerFirstName").value("Ops"))
                .andExpect(jsonPath("$.content[0].peerIsAdmin").value(true))
                .andExpect(jsonPath("$.content[0].lastMessageContent").value("Please review the guidelines."))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
    }

    @Test
    void listInbox_asMentor_returns200() throws Exception {
        mockJwt("mentor-token", "MENTOR", 7L);
        when(inboxService.listInbox(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/admin-direct")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk());
    }

    @Test
    void listInbox_asAdmin_returnsOwnAdminDirectThreads() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(inboxService.listInbox(eq(99L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleItem(false))));

        mockMvc.perform(get("/api/conversations/admin-direct")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].peerIsAdmin").value(false));
    }

    @Test
    void listInbox_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/conversations/admin-direct"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInbox_paginationParamsHonored() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(inboxService.listInbox(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/admin-direct?page=2&size=5")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).listInbox(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listInbox_clampsOversizedPageSize() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(inboxService.listInbox(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/admin-direct?size=10000")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).listInbox(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
