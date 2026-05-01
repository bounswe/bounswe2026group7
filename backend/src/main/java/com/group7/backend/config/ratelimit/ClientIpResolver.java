package com.group7.backend.config.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Resolves the client IP used as a rate-limit bucket key.
 *
 * <p>When {@code trustForwardedFor} is enabled, the resolver reads
 * {@code X-Forwarded-For} and walks the list from the right, skipping
 * {@code trustedProxiesCount} entries (each one represents a trusted hop
 * between the app and the wild internet). The entry that lands at that
 * position is treated as the client IP. With the default {@code count = 1},
 * the resolver returns the entry that the single trusted reverse proxy
 * appended — never the leftmost, which an attacker can populate by setting
 * {@code X-Forwarded-For} on the request before it ever reaches the proxy.
 *
 * <p>When {@code trustForwardedFor} is disabled (the default), the resolver
 * uses {@link HttpServletRequest#getRemoteAddr()}.
 *
 * <p>IPv6 addresses are normalised to their {@code /64} prefix so an attacker
 * cannot rotate the low 64 bits to escape rate limiting.
 *
 * <p><strong>Deployment requirement:</strong> when XFF trust is on,
 * {@code trustedProxiesCount} MUST equal the number of proxies that append
 * to {@code X-Forwarded-For} between the public internet and this app.
 * Setting it too low lets an attacker spoof the client IP. Setting it too
 * high — or any path where the chain arrives shorter than expected — makes
 * the resolver fall back to {@link HttpServletRequest#getRemoteAddr()}: the
 * IP of whoever actually connected, which is never attacker-spoofable, but
 * means everyone behind that proxy gets bucketed together. See
 * {@code PRODUCTION_CHECKLIST.md} for the deployment matrix.
 */
public class ClientIpResolver {

    private final boolean trustForwardedFor;
    private final int trustedProxiesCount;

    public ClientIpResolver(boolean trustForwardedFor, int trustedProxiesCount) {
        this.trustForwardedFor = trustForwardedFor;
        this.trustedProxiesCount = Math.max(1, trustedProxiesCount);
    }

    /**
     * Returns a stable rate-limit key for the request's client IP. Always
     * non-null and non-blank: falls back to {@code "unknown"} if no address is
     * available, or {@code "raw:<value>"} if parsing fails.
     */
    public String resolve(HttpServletRequest request) {
        String raw = trustForwardedFor ? trustedClientIpFromXff(request) : null;
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

    /**
     * Walks {@code X-Forwarded-For} from the right, skipping
     * {@code trustedProxiesCount} entries, and returns the next one. Returns
     * {@code null} (so {@link #resolve} falls back to {@code remoteAddr})
     * when the header is missing, blank, or shorter than the configured
     * trust depth. The short-chain case must NOT yield the leftmost entry
     * because that entry is attacker-controllable (the attacker sets
     * {@code X-Forwarded-For} on their original request before any trusted
     * proxy has a chance to append).
     */
    private String trustedClientIpFromXff(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] entries = header.split(",");
        if (entries.length < trustedProxiesCount) {
            return null;
        }
        int idx = entries.length - trustedProxiesCount;
        return entries[idx].trim();
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
