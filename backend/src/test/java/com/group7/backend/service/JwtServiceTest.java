package com.group7.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "bXlTdXBlclNlY3JldEtleUZvckdyb3VwN0JhY2tlbmRBcHBsaWNhdGlvbjIwMjY=");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    @Test
    void generateAndExtractEmail() {
        String token = jwtService.generateToken(1L, "test@example.com", "MENTOR");
        assertEquals("test@example.com", jwtService.extractEmail(token));
    }

    @Test
    void generateAndExtractUserId() {
        String token = jwtService.generateToken(42L, "test@example.com", "MENTEE");
        assertEquals(42L, jwtService.extractUserId(token));
    }

    @Test
    void generateAndExtractRole() {
        String token = jwtService.generateToken(1L, "test@example.com", "MENTOR");
        assertEquals("MENTOR", jwtService.extractRole(token));
    }

    @Test
    void tokenIsValid() {
        String token = jwtService.generateToken(1L, "test@example.com", "MENTOR");
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void expiredTokenIsInvalid() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        String token = jwtService.generateToken(1L, "test@example.com", "MENTOR");
        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateToken(1L, "test@example.com", "MENTOR");
        assertFalse(jwtService.isTokenValid(token + "tampered"));
    }

    @Test
    void menteeRole() {
        String token = jwtService.generateToken(1L, "mentee@example.com", "MENTEE");
        assertEquals("MENTEE", jwtService.extractRole(token));
    }
}
