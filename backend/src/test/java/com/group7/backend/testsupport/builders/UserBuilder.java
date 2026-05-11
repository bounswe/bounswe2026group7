package com.group7.backend.testsupport.builders;

import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.testsupport.E2EClient;
import com.group7.backend.testsupport.UserHandle;

/**
 * Fluent builder for the register → verify-email → login chain.
 *
 * <p>Defaults are deliberate: a memorable password ("Password1") that
 * satisfies {@code @ValidPassword}, generic first/last names, and the
 * mentee role. Callers override only what their scenario cares about.
 *
 * <p>Terminal method {@link #registerVerifyAndLogin()} returns a
 * {@link UserHandle} that downstream builders consume.
 */
public final class UserBuilder {

    private final E2EClient client;
    private String email;
    private String password = "Password1";
    private String firstName = "E2E";
    private String lastName = "User";
    private boolean mentor = false;

    public UserBuilder(E2EClient client) {
        this.client = client;
    }

    public UserBuilder email(String email) {
        this.email = email;
        return this;
    }

    public UserBuilder password(String password) {
        this.password = password;
        return this;
    }

    public UserBuilder firstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public UserBuilder lastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public UserBuilder asMentor() {
        this.mentor = true;
        return this;
    }

    public UserBuilder asMentee() {
        this.mentor = false;
        return this;
    }

    /**
     * Drives the full auth flow against MockMvc and returns a handle that
     * carries the user id, role, and a freshly minted JWT.
     */
    public UserHandle registerVerifyAndLogin() throws Exception {
        if (email == null) {
            throw new IllegalStateException("email() must be set before registerVerifyAndLogin()");
        }
        RegisterRequest body = new RegisterRequest();
        body.setFirstName(firstName);
        body.setLastName(lastName);
        body.setEmail(email);
        body.setPassword(password);
        body.setIsMentor(mentor);

        Long userId = client.register(body);
        client.verifyEmail(userId);
        String token = client.login(email, password);
        return new UserHandle(userId, email, mentor ? "MENTOR" : "MENTEE", token, firstName);
    }
}
