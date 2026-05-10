package com.group7.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Verifies that the locked-down Content-Security-Policy values from
 * {@code application-prod.properties} produce a header without dev-only
 * directives. We replay the prod CSP values via {@link TestPropertySource}
 * rather than activating the full {@code prod} profile because that profile
 * also forces {@code sslmode=require} on the datasource which Testcontainers
 * does not support — the goal is to verify the SecurityConfig wiring of the
 * CSP property values, not to boot under prod's datasource constraints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.csp.script-src='self' 'unsafe-inline'",
    "app.security.csp.connect-src='self' wss: https:"
})
class SecurityConfigCspTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prodCspValues_doNotContainUnsafeEvalOrLocalhost() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();
        String csp = result.getResponse().getHeader("Content-Security-Policy");

        assertThat(csp).isNotNull();
        assertThat(csp).doesNotContain("'unsafe-eval'");
        assertThat(csp).doesNotContain("localhost:8080");
        assertThat(csp).contains("script-src 'self' 'unsafe-inline';");
        assertThat(csp).contains("connect-src 'self' wss: https:;");
    }
}
