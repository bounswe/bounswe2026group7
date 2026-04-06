package com.group7.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhotoUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final byte[] VALID_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    @Test
    void uploadPhoto_noAuth_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);
        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePhoto_noAuth_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/me/photo"))
                .andExpect(status().isForbidden());
    }

    @Test
    void servePhoto_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/uploads/photos/nonexistent.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servePhoto_pathTraversal_returns4xx() throws Exception {
        mockMvc.perform(get("/api/uploads/photos/../../../etc/passwd"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void uploadPhoto_noFile_returns4xx() throws Exception {
        mockMvc.perform(multipart("/api/users/me/photo"))
                .andExpect(status().is4xxClientError());
    }
}
