package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FeedInteractionController} read endpoints
 * with cache-discipline expectations: {@code GET /api/feed/posts/{id}/interactions}
 * carries {@code Cache-Control: no-cache} so likers' updates and viewer-relative
 * flags surface immediately, in contrast to the static
 * {@code GET /api/feed/posts/{id}} endpoint which serves an ETag.
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
}
