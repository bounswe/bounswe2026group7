package com.group7.backend.entity;

/**
 * Discriminator for the participant set and creation policy of a {@link Conversation}.
 *
 * <ul>
 *   <li>{@link #MENTORSHIP} — mentor + mentee in an active mentorship (#245).</li>
 *   <li>{@link #MENTOR_PAIR} — two mentors exchanging peer messages (#284).</li>
 *   <li>{@link #ADMIN_DIRECT} — admin DM with any user, no mentorship required (#280).</li>
 *   <li>{@link #ADMIN_BROADCAST} — singleton conversation with every admin as participant (#280).</li>
 * </ul>
 *
 * Future kinds (system broadcasts, group chats) plug in here without schema change.
 */
public enum ConversationKind {
    MENTORSHIP,
    MENTOR_PAIR,
    ADMIN_DIRECT,
    ADMIN_BROADCAST
}
