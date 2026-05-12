package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.FeedCommentResponse;
import com.group7.backend.dto.response.FeedPostInteractionState;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.FeedInteractionService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FeedInteractionController}'s read endpoints:
 * the new comment permalink (#489) and the cache-discipline contract on
 * {@code GET /api/feed/posts/{id}/interactions}.
 *
 * <p>Toggle / list / mutation endpoints are integration-tested elsewhere;
 * this slice focuses on the surfaces #489 introduces or modifies at the
 * header level.
 */
@WebMvcTest(FeedInteractionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeedInteractionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FeedInteractionService interactionService;
    @MockitoBean private JwtService jwtService;

    private static final String TOKEN = "carol-token";

    private void mockMenteeJwt(Long userId) {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn("u" + userId + "@test.com");
        when(jwtService.extractRole(TOKEN)).thenReturn("MENTEE");
        when(jwtService.extractUserId(TOKEN)).thenReturn(userId);
    }

    // ── GET /api/feed/posts/{id}/interactions — cache discipline ──────────

    @Test
    void getInteractions_emitsNoCacheControl() throws Exception {
        mockMenteeJwt(1L);
        when(interactionService.getInteractionState(42L, 1L))
                .thenReturn(new FeedPostInteractionState(12, 3, 1, 2, true, false));

        mockMvc.perform(get("/api/feed/posts/42/interactions")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.likeCount").value(12));
    }

    @Test
    void getInteractions_softDeletedPost_returns404() throws Exception {
        mockMenteeJwt(1L);
        when(interactionService.getInteractionState(42L, 1L))
                .thenThrow(new ResourceNotFoundException("Feed post not found with id: 42"));

        mockMvc.perform(get("/api/feed/posts/42/interactions")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/feed/comments/{id} — permalink ──────────────────────────

    @Test
    void getComment_happyPath_returnsComment() throws Exception {
        mockMenteeJwt(1L);
        FeedCommentResponse stub = new FeedCommentResponse(
                101L, 42L, 1L, "Carol", "Great post!",
                OffsetDateTime.parse("2026-05-09T12:00:00Z"),
                OffsetDateTime.parse("2026-05-09T12:00:00Z"),
                false, true, false,
                0L, false);
        when(interactionService.getComment(101L, 1L)).thenReturn(stub);

        mockMvc.perform(get("/api/feed/comments/101")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.postId").value(42))
                .andExpect(jsonPath("$.body").value("Great post!"))
                .andExpect(jsonPath("$.isAuthor").value(true));
    }

    @Test
    void getComment_softDeletedComment_returns404() throws Exception {
        mockMenteeJwt(1L);
        when(interactionService.getComment(101L, 1L))
                .thenThrow(new ResourceNotFoundException("Comment not found with id: 101"));

        mockMvc.perform(get("/api/feed/comments/101")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_parentPostSoftDeleted_returns404() throws Exception {
        mockMenteeJwt(1L);
        when(interactionService.getComment(101L, 1L))
                .thenThrow(new ResourceNotFoundException("Comment not found with id: 101"));

        mockMvc.perform(get("/api/feed/comments/101")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComment_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/feed/comments/101"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getComment_nonNumericId_returns404FromRouteRegex() throws Exception {
        mockMenteeJwt(1L);
        mockMvc.perform(get("/api/feed/comments/bogus")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }
}
