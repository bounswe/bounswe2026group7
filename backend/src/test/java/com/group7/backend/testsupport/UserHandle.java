package com.group7.backend.testsupport;

/**
 * Lightweight value object representing a registered, email-verified, logged-in
 * user during an E2E scenario. Use the static helpers on {@link
 * com.group7.backend.testsupport.builders.UserBuilder} to construct one.
 *
 * <p>Carries everything a downstream builder needs to act as this user:
 * the user id (path params, repository assertions), the email (logging,
 * disambiguation), the role string ("MENTOR" or "MENTEE"), the JWT
 * (Authorization header), and the display first name (for verifying
 * notification bodies and mentorship/meeting summaries).
 */
public record UserHandle(Long id, String email, String role, String token, String firstName) {

    public boolean isMentor() {
        return "MENTOR".equals(role);
    }

    public boolean isMentee() {
        return "MENTEE".equals(role);
    }
}
