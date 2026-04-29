package com.group7.backend.config.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Resolves the client IP used as a rate-limit bucket key.
 *
 * <p>When {@code trustForwardedFor} is enabled, takes the leftmost entry of
 * {@code X-Forwarded-For}. Otherwise uses {@link HttpServletRequest#getRemoteAddr()}.
 * IPv6 addresses are normalized to their /64 prefix so an attacker cannot escape
 * the limiter by rotating the low 64 bits.
 *
 * <p>Trusting XFF is unsafe in any deploy where the client can set the header
 * directly (e.g., local dev, or a misconfigured reverse proxy). Default off.
 */
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    /**
     * Returns a stable rate-limit key for the request's client IP. Always
     * non-null and non-blank: falls back to {@code "unknown"} if no address is
     * available, or {@code "raw:<value>"} if parsing fails.
     */
    public String resolve(HttpServletRequest request) {
        String raw = trustForwardedFor ? leftmostForwardedFor(request) : null;
        if (raw == null || raw.isBlank()) {
            raw = request.getRemoteAddr();
        }
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(raw);
        } catch (UnknownHostException e) {
            return "raw:" + raw;
        }

        if (address instanceof Inet6Address) {
            return ipv6SlashSixtyFour(address.getAddress(), raw);
        }
        return address.getHostAddress();
    }

    private static String leftmostForwardedFor(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String first = (comma == -1) ? header : header.substring(0, comma);
        return first.trim();
    }

    private static String ipv6SlashSixtyFour(byte[] fullAddress, String fallback) {
        byte[] padded = Arrays.copyOf(Arrays.copyOf(fullAddress, 8), 16);
        try {
            return Inet6Address.getByAddress(null, padded, -1).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return fallback;
        }
    }
}
