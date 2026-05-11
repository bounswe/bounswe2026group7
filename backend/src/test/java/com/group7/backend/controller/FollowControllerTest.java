package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SelfFollowException;
import com.group7.backend.service.FollowResult;
import com.group7.backend.service.FollowService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FollowController}: HTTP semantics
 * (201 fresh / 200 idempotent / 204 unfollow), the auth gate, the
 * 400 self-follow body shape, the 404 missing-followee path, and the
 * paged list-endpoint surfaces. The {@link FollowService} is mocked so
 * the controller's own behaviour is what's under test.
 */
@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FollowControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FollowService followService;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── POST /follow ─────────────────────────────────────────────────────────

    @Test
    void follow_freshInsert_returns201() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(followService.follow(1L, 2L)).thenReturn(new FollowResult(1L, 2L, true));

        mockMvc.perform(post("/api/users/2/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerId").value(1))
                .andExpect(jsonPath("$.followeeId").value(2));
    }

    @Test
    void follow_duplicate_returns200() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(followService.follow(1L, 2L)).thenReturn(new FollowResult(1L, 2L, false));

        mockMvc.perform(post("/api/users/2/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerId").value(1))
                .andExpect(jsonPath("$.followeeId").value(2));
    }

    @Test
    void follow_self_returns400_withProjectStandardErrorBody() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(followService.follow(1L, 1L))
                .thenThrow(new SelfFollowException("A user cannot follow themselves"));

        mockMvc.perform(post("/api/users/1/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("A user cannot follow themselves"))
                // No `code` field — handler emits {error, message} only.
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void follow_missingTarget_returns404() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(followService.follow(1L, 9999L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 9999"));

        mockMvc.perform(post("/api/users/9999/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void follow_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/users/2/follow"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /follow ───────────────────────────────────────────────────────

    @Test
    void unfollow_returns204_evenWhenEdgeMissing() throws Exception {
        mockMenteeJwt("alice-token", 1L);

        mockMvc.perform(delete("/api/users/2/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNoContent());

        verify(followService).unfollow(1L, 2L);
    }

    @Test
    void unfollow_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/2/follow"))
                .andExpect(status().isForbidden());
    }

    // ── GET /followers ───────────────────────────────────────────────────────

    @Test
    void listFollowers_returnsPagedBodyShape() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        Mentee follower = mentee(2L, "Bob");
        Page<User> page = new PageImpl<>(List.<User>of(follower));
        when(followService.listFollowers(eq(1L), eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/1/followers")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].firstName").value("Bob"))
                .andExpect(jsonPath("$.content[0].role").value("MENTEE"));
    }

    @Test
    void listFollowers_clampsExcessiveSize() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        Page<User> empty = new PageImpl<>(List.<User>of());
        when(followService.listFollowers(eq(1L), eq(1L), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/users/1/followers")
                        .param("size", "100000")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk());

        // The PageableSupport clamp turns 100000 into 100; verify the service
        // saw a Pageable with size <= 100.
        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(followService).listFollowers(eq(1L), eq(1L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize())
                .isLessThanOrEqualTo(100);
    }

    @Test
    void listFollowers_missingTarget_returns404() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(followService.listFollowers(eq(9999L), eq(1L), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 9999"));

        mockMvc.perform(get("/api/users/9999/followers")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFollowers_privacyGate_returns403() throws Exception {
        // Mentee→other-mentee follow-graph access is rejected by the
        // service, mapped to 403 by ProfileNotVisibleExceptionHandler.
        mockMenteeJwt("alice-token", 1L);
        when(followService.listFollowers(eq(2L), eq(1L), any(Pageable.class)))
                .thenThrow(new ProfileNotVisibleException(
                        "Mentees cannot view other mentees' follow graph"));

        mockMvc.perform(get("/api/users/2/followers")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isForbidden());
    }

    // ── GET /following ───────────────────────────────────────────────────────

    @Test
    void listFollowing_returnsPagedBodyShape() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        Mentee following = mentee(3L, "Carol");
        Page<User> page = new PageImpl<>(List.<User>of(following));
        when(followService.listFollowing(eq(1L), eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users/1/following")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].firstName").value("Carol"));
    }

    @Test
    void listFollowing_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/1/following"))
                .andExpect(status().isForbidden());
    }

    // ── /me must not be captured by /{id:\\d+}/follow regex ─────────────────

    @Test
    void followEndpointRegex_doesNotMatchMeInUrlSegment() throws Exception {
        // Sanity check: POST /api/users/me/follow must 404 (no such mapping)
        // because the {id:\\d+} regex rejects non-numeric segments.
        mockMenteeJwt("alice-token", 1L);

        mockMvc.perform(post("/api/users/me/follow")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNotFound());
        verify(followService, org.mockito.Mockito.never()).follow(anyLong(), anyLong());
    }

    private static Mentee mentee(Long id, String firstName) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(firstName.toLowerCase() + "@ex.com");
        return m;
    }
}
