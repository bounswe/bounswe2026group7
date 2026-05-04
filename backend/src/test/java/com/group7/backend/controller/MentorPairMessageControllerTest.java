package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.ConversationService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MentorPairMessageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MentorPairMessageControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MessageService messageService;
    @MockitoBean private ConversationService conversationService;
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

    private Conversation pairConversation(Long pairAId, Long pairBId) {
        Conversation c = new Conversation();
        c.setId(901L);
        c.setKind(ConversationKind.MENTOR_PAIR);
        c.setPairAId(Math.min(pairAId, pairBId));
        c.setPairBId(Math.max(pairAId, pairBId));
        return c;
    }

    private MessageResponse sampleMessage() {
        MessageResponse r = new MessageResponse();
        r.setId(1L);
        r.setConversationId(901L);
        r.setSenderId(1L);
        r.setSenderFirstName("Mira");
        r.setSenderLastName("Aydin");
        r.setContent("hi");
        r.setSentAt(OffsetDateTime.parse("2026-05-04T10:00:00Z"));
        return r;
    }

    // ── POST: send ─────────────────────────────────────────────────────────

    @Test
    void send_asMentor_returns201() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 3L))
                .thenReturn(pairConversation(1L, 3L));
        when(messageService.send(eq(1L), eq(901L), any())).thenReturn(sampleMessage());

        mockMvc.perform(post("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("hi"))
                .andExpect(jsonPath("$.conversationId").value(901));
    }

    @Test
    void send_asMentee_returns403() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        mockMvc.perform(post("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/conversations/mentor-pair/3/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_selfPair_returns400() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 1L))
                .thenThrow(new IllegalArgumentException("Cannot start a conversation with yourself"));

        mockMvc.perform(post("/api/conversations/mentor-pair/1/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_menteeTarget_returns400() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 2L))
                .thenThrow(new IllegalArgumentException("Both participants must be mentors"));

        mockMvc.perform(post("/api/conversations/mentor-pair/2/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_unknownTarget_returns404() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 999L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/api/conversations/mentor-pair/999/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void send_emptyContent_returns400() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);

        mockMvc.perform(post("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_oversizedContent_returns400() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        String content = "x".repeat(4001);

        mockMvc.perform(post("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentor-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET: list ──────────────────────────────────────────────────────────

    @Test
    void list_asMentor_returns200() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 3L))
                .thenReturn(pairConversation(1L, 3L));
        Page<MessageResponse> page = new PageImpl<>(List.of(sampleMessage()));
        when(messageService.list(eq(1L), eq(901L), any())).thenReturn(page);

        mockMvc.perform(get("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("hi"));
    }

    @Test
    void list_asMentee_returns403() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        mockMvc.perform(get("/api/conversations/mentor-pair/3/messages")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /read ────────────────────────────────────────────────────────

    @Test
    void markRead_asMentor_returns204() throws Exception {
        mockMentorJwt("mentor-a-token", 1L);
        when(conversationService.findOrCreateForMentorPair(1L, 3L))
                .thenReturn(pairConversation(1L, 3L));
        when(messageService.markAllRead(1L, 901L)).thenReturn(2);

        mockMvc.perform(patch("/api/conversations/mentor-pair/3/messages/read")
                        .header("Authorization", "Bearer mentor-a-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void markRead_asMentee_returns403() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        mockMvc.perform(patch("/api/conversations/mentor-pair/3/messages/read")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }
}
