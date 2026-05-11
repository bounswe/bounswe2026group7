package com.group7.backend.service;

import com.group7.backend.config.SpamDetectionProperties;
import com.group7.backend.exception.SpamDetectionException;
import com.group7.backend.entity.BotSignal.SignalType;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC-SHA256-signed form-render token (#345). Encodes the time the
 * registration form was issued; the backend verifies signature + age on
 * submit. The token shape is {@code base64url(epochSeconds).base64url(hmac)}
 * so it survives JSON round-tripping without escaping.
 *
 * <p>Constant-time MAC comparison guards against signature-side timing
 * leaks; we do not need that for the timestamp itself since it's not a
 * secret.
 */
@Service
public class FormTokenService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGO = "HmacSHA256";

    private final SpamDetectionProperties properties;
    private final Clock clock;

    public FormTokenService(SpamDetectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Issue a fresh token carrying the current epoch second. */
    public String issue() {
        long epochSecond = Instant.now(clock).getEpochSecond();
        return sign(epochSecond);
    }

    /**
     * Verifies signature + TTL and returns the age. Throws on any failure
     * so the caller can record the appropriate {@link SignalType}.
     */
    public Duration verifyAndAge(String token) {
        if (token == null || token.isBlank()) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_INVALID);
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_INVALID);
        }
        String tsPart = token.substring(0, dot);
        String macPart = token.substring(dot + 1);

        long epochSecond;
        try {
            epochSecond = Long.parseLong(new String(DECODER.decode(tsPart), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_INVALID);
        }

        byte[] expectedMac;
        byte[] providedMac;
        try {
            expectedMac = hmac(Long.toString(epochSecond));
            providedMac = DECODER.decode(macPart);
        } catch (IllegalArgumentException ex) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_INVALID);
        }

        if (!MessageDigest.isEqual(expectedMac, providedMac)) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_INVALID);
        }

        Duration age = Duration.between(Instant.ofEpochSecond(epochSecond), Instant.now(clock));
        if (age.isNegative() || age.compareTo(properties.getFormTokenTtl()) > 0) {
            throw new SpamDetectionException(SignalType.FORM_TOKEN_EXPIRED);
        }
        return age;
    }

    private String sign(long epochSecond) {
        String tsB64 = ENCODER.encodeToString(Long.toString(epochSecond).getBytes(StandardCharsets.UTF_8));
        String macB64 = ENCODER.encodeToString(hmac(Long.toString(epochSecond)));
        return tsB64 + "." + macB64;
    }

    private byte[] hmac(String payload) {
        String secret = properties.getFormTokenSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("app.spam.form-token-secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC initialization failed", ex);
        }
    }
}
