package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PhotoUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    private static final String TOKEN = "test-jwt-token";

    private static final byte[] VALID_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    private void mockAuth(Long userId, String role) {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn("user@test.com");
        when(jwtService.extractUserId(TOKEN)).thenReturn(userId);
        when(jwtService.extractRole(TOKEN)).thenReturn(role);
    }

    private MentorResponse buildMentorResponse(String photoUrl) {
        MentorResponse r = new MentorResponse();
        r.setId(1L);
        r.setFirstName("Test");
        r.setLastName("Mentor");
        r.setEmail("user@test.com");
        r.setProfilePhoto(photoUrl);
        r.setIsEmailVerified(true);
        r.setCreatedAt(LocalDateTime.now());
        r.setRole("MENTOR");
        r.setInterests(List.of());
        r.setPreferredMenteeSkills(List.of());
        return r;
    }

    // ── Upload tests ──

    @Test
    void uploadPhoto_validFile_returns200WithPhotoUrl() throws Exception {
        mockAuth(1L, "MENTOR");
        MentorResponse response = buildMentorResponse("http://localhost:8080/api/uploads/photos/uuid.jpg");
        when(userService.uploadProfilePhoto(eq(1L), any(MultipartFile.class))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);

        mockMvc.perform(multipart("/api/users/me/photo").file(file)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhoto").value("http://localhost:8080/api/uploads/photos/uuid.jpg"));

        verify(userService).uploadProfilePhoto(eq(1L), any(MultipartFile.class));
    }

    @Test
    void uploadPhoto_noAuth_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isForbidden());

        verify(userService, never()).uploadProfilePhoto(any(), any());
    }

    @Test
    void uploadPhoto_serviceThrowsIllegalArgument_returns400() throws Exception {
        mockAuth(1L, "MENTOR");
        when(userService.uploadProfilePhoto(eq(1L), any(MultipartFile.class)))
                .thenThrow(new IllegalArgumentException("Invalid file type. Allowed: JPEG, PNG, GIF, WebP"));

        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "text".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type. Allowed: JPEG, PNG, GIF, WebP"));
    }

    // ── Delete tests ──

    @Test
    void deletePhoto_authenticated_returns200WithNullPhoto() throws Exception {
        mockAuth(1L, "MENTOR");
        MentorResponse response = buildMentorResponse(null);
        when(userService.deleteProfilePhoto(1L)).thenReturn(response);

        mockMvc.perform(delete("/api/users/me/photo")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhoto").doesNotExist());

        verify(userService).deleteProfilePhoto(1L);
    }

    @Test
    void deletePhoto_noAuth_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/me/photo"))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteProfilePhoto(any());
    }

    // ── PATCH no longer sets profilePhoto ──

    @Test
    void patchProfile_profilePhotoFieldIgnored() throws Exception {
        mockAuth(1L, "MENTOR");
        MentorResponse response = buildMentorResponse("http://localhost:8080/api/uploads/photos/existing.jpg");
        when(userService.updateProfile(eq(1L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType("application/json")
                        .content("{\"profilePhoto\":\"javascript:alert(1)\",\"bio\":\"test\"}"))
                .andExpect(status().isOk());

        // Verify the service was called but profilePhoto was NOT changed
        // The existing photo URL should remain
        verify(userService).updateProfile(eq(1L), any());
    }
}
