package com.group7.backend.config.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void usesRemoteAddrWhenForwardingDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void readsLeftmostXffWhenTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 198.51.100.1, 10.0.0.1");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddrWhenXffBlank() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("X-Forwarded-For", "");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddrWhenXffMissing() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void normalizesIpv6ToSlashSixtyFour() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest reqA = new MockHttpServletRequest();
        reqA.setRemoteAddr("2001:db8:abcd:0012::1");
        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setRemoteAddr("2001:db8:abcd:0012:ffff:ffff:ffff:ffff");

        String keyA = resolver.resolve(reqA);
        String keyB = resolver.resolve(reqB);

        assertThat(keyA).isEqualTo(keyB);
        assertThat(keyA).endsWith("/64");
    }

    @Test
    void differentIpv6SlashSixtyFourPrefixesProduceDifferentKeys() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest reqA = new MockHttpServletRequest();
        reqA.setRemoteAddr("2001:db8:abcd:0012::1");
        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setRemoteAddr("2001:db8:abcd:0013::1");

        assertThat(resolver.resolve(reqA)).isNotEqualTo(resolver.resolve(reqB));
    }

    @Test
    void returnsUnknownWhenNoAddressAvailable() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(null);

        assertThat(resolver.resolve(req)).isEqualTo("unknown");
    }

    @Test
    void trimsXffWhitespace() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "  203.0.113.7  , 198.51.100.1");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }
}
