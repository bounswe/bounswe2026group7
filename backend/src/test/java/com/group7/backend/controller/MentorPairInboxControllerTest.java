package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MentorPairInboxItem;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorPairInboxService;
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

@WebMvcTest(MentorPairInboxController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MentorPairInboxControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MentorPairInboxService inboxService;
    @MockitoBean private JwtService jwtService;

    private void mockMentorJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentor@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTOR");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentee@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private MentorPairInboxItem sampleItem() {
        MentorPairInboxItem item = new MentorPairInboxItem();
        item.setConversationId(901L);
        item.setPeerId(42L);
        item.setPeerFirstName("Mira");
        item.setLastMessageContent("see you Tuesday");
        item.setLastMessageSentAt(OffsetDateTime.parse("2026-05-04T10:00:00Z"));
        item.setUnreadCount(3L);
        return item;
    }

    // ── 9. Happy path: 200 + paged body shape ──────────────────────────────

    @Test
    void listInbox_asMentor_returns200WithPagedBody() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        Page<MentorPairInboxItem> page = new PageImpl<>(List.of(sampleItem()));
        when(inboxService.listInbox(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/conversations/mentor-pair")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].conversationId").value(901))
                .andExpect(jsonPath("$.content[0].peerId").value(42))
                .andExpect(jsonPath("$.content[0].peerFirstName").value("Mira"))
                .andExpect(jsonPath("$.content[0].lastMessageContent").value("see you Tuesday"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(3));
    }

    // ── 10. Mentee role denied ─────────────────────────────────────────────

    @Test
    void listInbox_asMentee_returns403() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        mockMvc.perform(get("/api/conversations/mentor-pair")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    // ── 11. Unauthenticated denied (this codebase: 403, not 401) ───────────

    @Test
    void listInbox_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/conversations/mentor-pair"))
                .andExpect(status().isForbidden());
    }

    // ── 12. Pagination params honoured (page+size both within bounds) ──────

    @Test
    void listInbox_paginationParamsHonored() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(inboxService.listInbox(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/mentor-pair?page=2&size=5")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).listInbox(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    // ── 13. Oversized size clamps to 100 ───────────────────────────────────

    @Test
    void listInbox_clampsOversizedPageSize() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(inboxService.listInbox(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/mentor-pair?size=10000")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).listInbox(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    // ── 14. Negative inputs clamped silently (page→0, size→1) ──────────────

    @Test
    void listInbox_clampsNegativeInputs() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(inboxService.listInbox(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/conversations/mentor-pair?page=-1&size=-5")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inboxService).listInbox(eq(1L), captor.capture());
        // Math.max(page, 0) -> 0; Math.max(size, 1) wins over Math.min(_, 100) -> 1.
        // The 20 default would only fire if `size` were absent (defaultValue),
        // but here it was supplied as -5, so the clamp lower bound applies.
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
