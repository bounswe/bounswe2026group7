package com.group7.backend.config;

import com.group7.backend.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "bXlTdXBlclNlY3JldEtleUZvckdyb3VwN0JhY2tlbmRBcHBsaWNhdGlvbjIwMjY=";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_populatesSecurityContext() throws Exception {
        String token = jwtService.generateToken(42L, "user@example.com", "MENTOR");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authn = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authn).isNotNull();
        assertThat(authn.getPrincipal()).isEqualTo("user@example.com");
        assertThat(authn.getCredentials()).isEqualTo(42L);
        assertThat(authn.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MENTOR");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void tokenMissingUserId_doesNotPopulateContext_andCallsChain() throws Exception {
        String token = malformedToken(Map.<String, Object>of("role", "MENTOR"), "user@example.com");
        runFilter(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenMissingRole_doesNotPopulateContext_andCallsChain() throws Exception {
        String token = malformedToken(Map.<String, Object>of("userId", 1L), "user@example.com");
        runFilter(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenWithBlankRole_doesNotPopulateContext() throws Exception {
        String token = malformedToken(Map.<String, Object>of("userId", 1L, "role", ""), "user@example.com");
        runFilter(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noBearerHeader_doesNotPopulateContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    private void runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(request, response);
    }

    private static String malformedToken(Map<String, Object> claims, String subject) {
        Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key, SignatureAlgorithm.HS256);
        if (subject != null) {
            builder.setSubject(subject);
        }
        return builder.compact();
    }
}
