package com.group7.backend.entity;

/**
 * Origin of a {@link Ban} row. Persisted as a string so the contract is
 * legible in DB dumps and forward-compatible with new variants.
 *
 * <p>The discriminator exists because each ban kind has its own lift
 * policy — most importantly, {@code SYSTEM_SPAM} bans must only be
 * touched by the bot-flag clear flow (#345); previously every ban was
 * structurally identical and a "clear bot flag" admin click could lift
 * an unrelated admin ban that happened to be the user's latest-expiring
 * row.
 */
public enum BanSource {
    /** Auto-ban from mentee-initiated cancellation escalation (#134). */
    MENTEE_CANCELLATION,
    /** Manual admin imposition via the admin console (#280). */
    ADMIN,
    /** System auto-ban triggered by the spam-bot signal threshold (#345). */
    SYSTEM_SPAM
}
