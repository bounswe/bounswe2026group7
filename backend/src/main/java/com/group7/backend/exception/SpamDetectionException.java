package com.group7.backend.exception;

import com.group7.backend.entity.BotSignal.SignalType;

/**
 * Marker for a request rejected by the spam-bot detection layer (#345).
 * Carries the {@link SignalType} so the recording side can log the exact
 * trigger, but {@code GlobalExceptionHandler} maps every variant to the
 * same generic 400 body to avoid leaking which signal fired — the bot
 * shouldn't learn what specifically caught it.
 */
public class SpamDetectionException extends RuntimeException {

    private final SignalType signalType;

    public SpamDetectionException(SignalType signalType) {
        super("Request rejected");
        this.signalType = signalType;
    }

    public SignalType getSignalType() {
        return signalType;
    }
}
