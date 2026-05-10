package com.group7.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that with the default config (test profile inherits
 * {@code app.test-endpoints.enabled=false}), the /api/test/** surface from
 * {@link com.group7.backend.controller.TestSupportController} is invisible
 * — the conditional bean is not registered and SecurityConfig ignores the
 * path so DispatcherServlet returns 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSupportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetEndpointReturns404WhenTestEndpointsDisabled() throws Exception {
        mockMvc.perform(post("/api/test/reset"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verificationTokenEndpointReturns404WhenTestEndpointsDisabled() throws Exception {
        mockMvc.perform(get("/api/test/verification-token").param("email", "anyone@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void seedUserEndpointReturns404WhenTestEndpointsDisabled() throws Exception {
        mockMvc.perform(post("/api/test/users").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
}
