package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.service.ConversationService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMessagingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminMessagingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MessageService messageService;
    @MockitoBean private ConversationService conversationService;
    @MockitoBean private JwtService jwtService;

    private void mockJwt(String token, String role, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn(role.toLowerCase() + "@example.com");
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private Conversation broadcast() {
        Conversation c = new Conversation();
        c.setId(7000L);
        c.setKind(ConversationKind.ADMIN_BROADCAST);
        return c;
    }

    private Conversation adminDirect() {
        Conversation c = new Conversation();
        c.setId(800L);
        c.setKind(ConversationKind.ADMIN_DIRECT);
        c.setPairAId(1L);
        c.setPairBId(99L);
        return c;
    }

    private MessageResponse sampleMessage(Long convId) {
        MessageResponse r = new MessageResponse();
        r.setId(1L);
        r.setConversationId(convId);
        r.setSenderId(99L);
        r.setSenderFirstName("Ops");
        r.setContent("ops update");
        r.setSentAt(OffsetDateTime.parse("2026-05-12T10:00:00Z"));
        return r;
    }

    // ── POST /direct/{userId} ──────────────────────────────────────────────

    @Test
    void direct_asAdmin_returns201() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(conversationService.findOrCreateForAdminDirect(99L, 1L)).thenReturn(adminDirect());
        when(messageService.send(eq(99L), eq(800L), any())).thenReturn(sampleMessage(800L));

        mockMvc.perform(post("/api/admin/messages/direct/1")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"ops update\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(800));
    }

    @Test
    void direct_asMentee_returns403() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);

        mockMvc.perform(post("/api/admin/messages/direct/2")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    // ── POST /broadcast ────────────────────────────────────────────────────

    @Test
    void broadcast_asAdmin_returns201() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(conversationService.findOrCreateAdminBroadcast()).thenReturn(broadcast());
        when(messageService.send(eq(99L), eq(7000L), any())).thenReturn(sampleMessage(7000L));

        mockMvc.perform(post("/api/admin/messages/broadcast")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"team announcement\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(7000));
    }

    // ── GET /broadcast (new) ───────────────────────────────────────────────

    @Test
    void listBroadcast_asAdmin_returns200WithPagedMessages() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(conversationService.findOrCreateAdminBroadcast()).thenReturn(broadcast());
        when(messageService.list(eq(99L), eq(7000L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleMessage(7000L))));

        mockMvc.perform(get("/api/admin/messages/broadcast")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("ops update"))
                .andExpect(jsonPath("$.content[0].conversationId").value(7000));
    }

    @Test
    void listBroadcast_asMentor_returns403() throws Exception {
        mockJwt("mentor-token", "MENTOR", 7L);

        mockMvc.perform(get("/api/admin/messages/broadcast")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());

        verify(messageService, never()).list(any(), any(), any());
    }

    @Test
    void listBroadcast_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/messages/broadcast"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /broadcast/read (new) ────────────────────────────────────────

    @Test
    void markBroadcastRead_asAdmin_returns204() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(conversationService.findOrCreateAdminBroadcast()).thenReturn(broadcast());

        mockMvc.perform(patch("/api/admin/messages/broadcast/read")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        verify(messageService).markAllRead(eq(99L), eq(7000L));
    }

    @Test
    void markBroadcastRead_asMentee_returns403() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);

        mockMvc.perform(patch("/api/admin/messages/broadcast/read")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());

        verify(messageService, never()).markAllRead(any(), any());
    }
}
