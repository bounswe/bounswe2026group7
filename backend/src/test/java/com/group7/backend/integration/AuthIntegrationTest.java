package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    // --- Register (1.2.3.1) ---

    @Test
    void registerMenteeEndToEnd() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yilmaz"))
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        assertTrue(userRepository.existsByEmail("ali@example.com"));
    }

    @Test
    void registerMentorEndToEnd() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ayse");
        request.setLastName("Demir");
        request.setEmail("ayse@example.com");
        request.setPassword("Password1");
        request.setIsMentor(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MENTOR"));

        assertTrue(userRepository.existsByEmail("ayse@example.com"));
    }

    // --- Unique Email (1.2.3.2) ---

    @Test
    void registerDuplicateEmailFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))));
    }

    // --- Password Validation (1.2.3.3) ---

    @Test
    void registerWithWeakPasswordReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("weak");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail("ali@example.com"));
    }

    // --- Login (1.2.3.5, 1.2.3.6) ---

    @Test
    void loginAfterRegisterReturnsToken() throws Exception {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setFirstName("Ali");
        regRequest.setLastName("Yilmaz");
        regRequest.setEmail("ali@example.com");
        regRequest.setPassword("Password1");
        regRequest.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").isString())
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(body.get("sessionToken").asText());
        assertFalse(body.get("sessionToken").asText().isEmpty());
    }

    @Test
    void loginMentorReturnsCorrectRole() throws Exception {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setFirstName("Ayse");
        regRequest.setLastName("Demir");
        regRequest.setEmail("ayse@example.com");
        regRequest.setPassword("Password1");
        regRequest.setIsMentor(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ayse@example.com");
        loginRequest.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"));
    }

    // --- Invalid Credentials (1.2.3.7) ---

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setFirstName("Ali");
        regRequest.setLastName("Yilmaz");
        regRequest.setEmail("ali@example.com");
        regRequest.setPassword("Password1");
        regRequest.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("WrongPass1");

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))));
    }

    @Test
    void loginWithNonexistentEmailFails() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("noone@example.com");
        loginRequest.setPassword("Password1");

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))));
    }

    // --- Password Stored as Hash (2.2.1) ---

    @Test
    void passwordIsStoredHashed() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        var user = userRepository.findByEmail("ali@example.com").orElseThrow();
        assertNotEquals("Password1", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$"));
    }

    // --- JWT Token Protects Endpoints ---

    @Test
    void protectedEndpointReturns403WithoutToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWorksWithValidToken() throws Exception {
        RegisterRequest regRequest = new RegisterRequest();
        regRequest.setFirstName("Ali");
        regRequest.setLastName("Yilmaz");
        regRequest.setEmail("ali@example.com");
        regRequest.setPassword("Password1");
        regRequest.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("sessionToken").asText();

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
