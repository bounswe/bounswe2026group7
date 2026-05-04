package com.group7.backend.config.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void usesRemoteAddrWhenForwardingDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(false, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void readsTrustedXffEntryFromRightForSingleProxyHop() {
        // Single trusted hop: only the rightmost XFF entry is the trusted
        // proxy's view of the client. An attacker could prepend anything
        // to the left, so the resolver must take the entry at index
        // (len - 1) — the proxy-supplied one — and not the leftmost.
        ClientIpResolver resolver = new ClientIpResolver(true, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 198.51.100.1, 10.0.0.1");

        assertThat(resolver.resolve(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void readsTrustedXffEntryAcrossMultipleProxyHops() {
        // CDN -> ALB -> app: 2 trusted hops. With XFF
        // "<attacker>, <real client>, <CDN>", the trusted entry is the one
        // appended by the CDN before ALB added its own. Index = len - 2 = 1.
        ClientIpResolver resolver = new ClientIpResolver(true, 2);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "ATTACKER, 203.0.113.7, 198.51.100.1");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddrWhenXffShorterThanTrustDepth() {
        // Chain shorter than expected (someone misconfigured the proxy
        // count, or a hop is missing). Returning the leftmost XFF entry
        // would expose an attacker-controllable value, so the resolver
        // refuses XFF in this case and yields remoteAddr instead — the IP
        // of whoever actually connected, never spoofable.
        ClientIpResolver resolver = new ClientIpResolver(true, 3);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToRemoteAddrWhenXffBlank() {
        ClientIpResolver resolver = new ClientIpResolver(true, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("X-Forwarded-For", "");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddrWhenXffMissing() {
        ClientIpResolver resolver = new ClientIpResolver(true, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void normalizesIpv6ToSlashSixtyFour() {
        ClientIpResolver resolver = new ClientIpResolver(false, 1);
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
        ClientIpResolver resolver = new ClientIpResolver(false, 1);
        MockHttpServletRequest reqA = new MockHttpServletRequest();
        reqA.setRemoteAddr("2001:db8:abcd:0012::1");
        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setRemoteAddr("2001:db8:abcd:0013::1");

        assertThat(resolver.resolve(reqA)).isNotEqualTo(resolver.resolve(reqB));
    }

    @Test
    void returnsUnknownWhenNoAddressAvailable() {
        ClientIpResolver resolver = new ClientIpResolver(false, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(null);

        assertThat(resolver.resolve(req)).isEqualTo("unknown");
    }

    @Test
    void trimsXffWhitespace() {
        ClientIpResolver resolver = new ClientIpResolver(true, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "  203.0.113.7  , 198.51.100.1  ");

        assertThat(resolver.resolve(req)).isEqualTo("198.51.100.1");
    }

    @Test
    void clampsTrustedProxiesCountBelowOne() {
        // Constructor coerces values below 1 to 1 — defensive, since the
        // only sensible deployment has at least one trusted hop when XFF
        // trust is enabled.
        ClientIpResolver resolver = new ClientIpResolver(true, 0);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "ATTACKER, 203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void singleEntryXffWithDefaultTrustDepthReturnsThatEntry() {
        // Default deployment: one trusted proxy, XFF arrives with one entry
        // = the trusted proxy's view of the client. Common case must work.
        ClientIpResolver resolver = new ClientIpResolver(true, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }
}
