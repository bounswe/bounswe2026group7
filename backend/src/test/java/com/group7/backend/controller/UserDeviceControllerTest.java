package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.RegisterDeviceRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.UserDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserDeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserDeviceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserDeviceService userDeviceService;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private UserDevice sampleDevice() {
        Mentee user = new Mentee();
        user.setId(7L);
        UserDevice d = new UserDevice();
        d.setId(1L);
        d.setUser(user);
        d.setToken("tok-A");
        d.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        d.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
        return d;
    }

    @Test
    void registerReturns201WithBody() throws Exception {
        mockMenteeJwt("tok", 7L);
        when(userDeviceService.register(eq(7L), eq("tok-A"))).thenReturn(sampleDevice());

        RegisterDeviceRequest body = new RegisterDeviceRequest();
        body.setToken("tok-A");

        mockMvc.perform(post("/api/users/me/devices")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("tok-A"));
    }

    @Test
    void registerRejectsBlankTokenWith400() throws Exception {
        mockMenteeJwt("tok", 7L);
        RegisterDeviceRequest body = new RegisterDeviceRequest();
        body.setToken("");

        mockMvc.perform(post("/api/users/me/devices")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsOversizedTokenWith400() throws Exception {
        mockMenteeJwt("tok", 7L);
        String oversized = "x".repeat(513);
        RegisterDeviceRequest body = new RegisterDeviceRequest();
        body.setToken(oversized);

        mockMvc.perform(post("/api/users/me/devices")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsUnauthenticated() throws Exception {
        RegisterDeviceRequest body = new RegisterDeviceRequest();
        body.setToken("tok-A");

        mockMvc.perform(post("/api/users/me/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unregisterReturns204() throws Exception {
        mockMenteeJwt("tok", 7L);

        mockMvc.perform(delete("/api/users/me/devices/tok-A")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());

        verify(userDeviceService).unregister(7L, "tok-A");
    }
}
