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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDirectMessageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminDirectMessageControllerTest {

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

    private Conversation adminDirect(Long pairAId, Long pairBId) {
        Conversation c = new Conversation();
        c.setId(901L);
        c.setKind(ConversationKind.ADMIN_DIRECT);
        c.setPairAId(Math.min(pairAId, pairBId));
        c.setPairBId(Math.max(pairAId, pairBId));
        return c;
    }

    private MessageResponse sampleMessage() {
        MessageResponse r = new MessageResponse();
        r.setId(1L);
        r.setConversationId(901L);
        r.setSenderId(99L);
        r.setSenderFirstName("Ops");
        r.setContent("Please review the guidelines.");
        r.setSentAt(OffsetDateTime.parse("2026-05-12T10:00:00Z"));
        return r;
    }

    @Test
    void list_asMenteeRecipient_returns200() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(conversationService.findAdminDirectByPair(1L, 99L))
                .thenReturn(Optional.of(adminDirect(1L, 99L)));
        when(messageService.list(eq(1L), eq(901L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleMessage())));

        mockMvc.perform(get("/api/conversations/admin-direct/99/messages")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Please review the guidelines."))
                .andExpect(jsonPath("$.content[0].conversationId").value(901));
    }

    @Test
    void list_asAdminSender_returns200() throws Exception {
        mockJwt("admin-token", "ADMIN", 99L);
        when(conversationService.findAdminDirectByPair(99L, 1L))
                .thenReturn(Optional.of(adminDirect(99L, 1L)));
        when(messageService.list(eq(99L), eq(901L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleMessage())));

        mockMvc.perform(get("/api/conversations/admin-direct/1/messages")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void list_noConversationForPair_returns404() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(conversationService.findAdminDirectByPair(1L, 99L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/conversations/admin-direct/99/messages")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isNotFound());

        verify(messageService, never()).list(any(), any(), any());
    }

    @Test
    void list_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/conversations/admin-direct/99/messages"))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAllRead_asMenteeRecipient_returns204() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(conversationService.findAdminDirectByPair(1L, 99L))
                .thenReturn(Optional.of(adminDirect(1L, 99L)));

        mockMvc.perform(patch("/api/conversations/admin-direct/99/messages/read")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isNoContent());

        verify(messageService).markAllRead(eq(1L), eq(901L));
    }

    @Test
    void markAllRead_noConversationForPair_returns404() throws Exception {
        mockJwt("mentee-token", "MENTEE", 1L);
        when(conversationService.findAdminDirectByPair(1L, 99L))
                .thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/conversations/admin-direct/99/messages/read")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isNotFound());

        verify(messageService, never()).markAllRead(any(), any());
    }
}
