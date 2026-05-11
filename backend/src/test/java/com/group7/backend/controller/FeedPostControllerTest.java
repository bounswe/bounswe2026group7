package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.feed.FeedPostLimits;
import com.group7.backend.dto.request.CreateFeedPostRequest;
import com.group7.backend.dto.request.UpdateFeedPostRequest;
import com.group7.backend.dto.response.FeedPostResponse;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.FeedPostService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FeedPostController} — auth gate, status-code
 * contract per endpoint, body validation, and the propagation of service-
 * layer exceptions to the right HTTP status via the global handler.
 *
 * <p>The {@link FeedPostService} is mocked so the controller's own behaviour
 * is what's under test. {@link SecurityConfig} and
 * {@link JwtAuthenticationFilter} are explicitly imported so the
 * authentication gate fires (without these the {@code @WebMvcTest} slice
 * skips the security filter chain and 403-anonymous tests would pass for
 * the wrong reason).
 */
@WebMvcTest(FeedPostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeedPostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private FeedPostService feedPostService;
    @MockitoBean private JwtService jwtService;

    private static final String TOKEN = "alice-token";

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("u" + userId + "@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private static FeedPostResponse stub(Long id, Long authorId) {
        return new FeedPostResponse(id, authorId, "Alice", "Hello", List.of("data"),
                OffsetDateTime.now(), OffsetDateTime.now(), false, true, List.of());
    }

    // ── POST /api/feed/posts ────────────────────────────────────────────────

    @Test
    void create_happyPath_returns201() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.create(eq(1L), any(), any(), any())).thenReturn(stub(42L, 1L));

        CreateFeedPostRequest body = new CreateFeedPostRequest("Hello", List.of("data"), null);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.authorId").value(1))
                .andExpect(jsonPath("$.body").value("Hello"))
                .andExpect(jsonPath("$.hashtags[0]").value("data"));
    }

    @Test
    void create_unauthenticated_returns403() throws Exception {
        CreateFeedPostRequest body = new CreateFeedPostRequest("Hello", List.of(), null);
        mockMvc.perform(post("/api/feed/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_blankBody_returns400() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        CreateFeedPostRequest body = new CreateFeedPostRequest("", List.of(), null);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_oversizeBody_returns400() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        String tooLong = "x".repeat(FeedPostLimits.MAX_BODY_LENGTH + 1);
        CreateFeedPostRequest body = new CreateFeedPostRequest(tooLong, List.of(), null);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_overHashtagCap_returns400() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        List<String> tooMany = IntStream.range(0, FeedPostLimits.MAX_HASHTAGS + 1)
                .mapToObj(i -> "tag" + i).collect(Collectors.toList());
        CreateFeedPostRequest body = new CreateFeedPostRequest("Hello", tooMany, null);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_adminRequester_returns403() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.create(eq(1L), any(), any(), any()))
                .thenThrow(new AccessDeniedException("Admins cannot create feed posts"));

        CreateFeedPostRequest body = new CreateFeedPostRequest("Hello", List.of(), null);

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/feed/posts/{id} ───────────────────────────────────────────

    @Test
    void getById_happyPath_returns200() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.getById(42L, 1L)).thenReturn(stub(42L, 1L));

        mockMvc.perform(get("/api/feed/posts/42").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void getById_softDeleted_returns404() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.getById(42L, 1L))
                .thenThrow(new ResourceNotFoundException("Feed post not found with id: 42"));

        mockMvc.perform(get("/api/feed/posts/42").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/feed/posts/42"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_setsETagLastModifiedAndCacheControlHeaders() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        // Fixed timestamps so the assertion is deterministic.
        OffsetDateTime t0 = OffsetDateTime.parse("2026-05-09T10:15:00Z");
        FeedPostResponse fixed = new FeedPostResponse(42L, 1L, "Alice", "Hello",
                List.of("data"), t0, t0, false, true);
        when(feedPostService.getById(42L, 1L)).thenReturn(fixed);

        mockMvc.perform(get("/api/feed/posts/42").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().exists("Last-Modified"))
                .andExpect(header().string("Cache-Control", "private, max-age=30"));
    }

    @Test
    void getById_returns304WhenIfNoneMatchMatches() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        OffsetDateTime t0 = OffsetDateTime.parse("2026-05-09T10:15:00Z");
        FeedPostResponse fixed = new FeedPostResponse(42L, 1L, "Alice", "Hello",
                List.of("data"), t0, t0, false, true);
        when(feedPostService.getById(42L, 1L)).thenReturn(fixed);

        // First fetch — capture the ETag.
        String etag = mockMvc.perform(get("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        // Conditional GET with the same ETag — server short-circuits to 304
        // with no body (controller returns null after checkNotModified).
        mockMvc.perform(get("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void getById_etagChangesAfterEdit() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        OffsetDateTime created = OffsetDateTime.parse("2026-05-09T10:15:00Z");
        OffsetDateTime edited = OffsetDateTime.parse("2026-05-09T11:02:34Z");
        FeedPostResponse before = new FeedPostResponse(42L, 1L, "Alice", "Hello",
                List.of("data"), created, created, false, true);
        FeedPostResponse after = new FeedPostResponse(42L, 1L, "Alice", "Hello edited",
                List.of("data"), created, edited, true, true);
        when(feedPostService.getById(42L, 1L)).thenReturn(before, after);

        String etagBefore = mockMvc.perform(get("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        String etagAfter = mockMvc.perform(get("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        org.junit.jupiter.api.Assertions.assertNotEquals(etagBefore, etagAfter,
                "ETag must change after an edit bumps updatedAt");
    }

    @Test
    void getById_nonNumericPath_returns404FromRouteRegex() throws Exception {
        // {id:\d+} regex rejects "me" — Spring routing returns 404 because
        // no handler matches.
        mockMenteeJwt(TOKEN, 1L);
        mockMvc.perform(get("/api/feed/posts/me").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
        verify(feedPostService, org.mockito.Mockito.never()).getById(any(), any());
    }

    // ── PATCH /api/feed/posts/{id} ─────────────────────────────────────────

    @Test
    void update_happyPath_returns200() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.update(eq(42L), eq(1L), any(), any(), any())).thenReturn(stub(42L, 1L));

        UpdateFeedPostRequest body = new UpdateFeedPostRequest("Updated", null, null);

        mockMvc.perform(patch("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void update_nonAuthor_returns403() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.update(eq(42L), eq(1L), any(), any(), any()))
                .thenThrow(new AccessDeniedException("Only the post author can perform this action"));

        UpdateFeedPostRequest body = new UpdateFeedPostRequest("hostile", null, null);

        mockMvc.perform(patch("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_softDeleted_returns404() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.update(eq(42L), eq(1L), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Feed post not found with id: 42"));

        UpdateFeedPostRequest body = new UpdateFeedPostRequest("late edit", null, null);

        mockMvc.perform(patch("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_concurrentModification_returns409() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        when(feedPostService.update(eq(42L), eq(1L), any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        UpdateFeedPostRequest body = new UpdateFeedPostRequest("v1", null, null);

        mockMvc.perform(patch("/api/feed/posts/42")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void update_unauthenticated_returns403() throws Exception {
        UpdateFeedPostRequest body = new UpdateFeedPostRequest("x", null, null);
        mockMvc.perform(patch("/api/feed/posts/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/feed/posts/{id} ────────────────────────────────────────

    @Test
    void delete_happyPath_returns204() throws Exception {
        mockMenteeJwt(TOKEN, 1L);

        mockMvc.perform(delete("/api/feed/posts/42").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        verify(feedPostService).delete(42L, 1L);
    }

    @Test
    void delete_nonAuthor_returns403() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        org.mockito.Mockito.doThrow(new AccessDeniedException("Only the post author can perform this action"))
                .when(feedPostService).delete(42L, 1L);

        mockMvc.perform(delete("/api/feed/posts/42").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_missingPost_returns404() throws Exception {
        mockMenteeJwt(TOKEN, 1L);
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Feed post not found with id: 99"))
                .when(feedPostService).delete(99L, 1L);

        mockMvc.perform(delete("/api/feed/posts/99").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/feed/posts/42"))
                .andExpect(status().isForbidden());
    }
}
