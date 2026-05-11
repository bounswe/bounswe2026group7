package com.group7.backend.config;

import com.group7.backend.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

/**
 * Plain POJO bound by {@link AdminBootstrapConfiguration}'s {@code @Bean} method.
 *
 * Intentionally NOT annotated with {@code @ConfigurationProperties} at the class
 * level — keeping it free of that annotation makes it invisible to any future
 * {@code @ConfigurationPropertiesScan}. Binding happens via the {@code @Bean}
 * method, which is gated by {@code @ConditionalOnProperty} on the configuration
 * class. {@code @Validated} on the class triggers Bean Validation during binding
 * (Spring's {@code ConfigurationPropertiesBindHandler} reads class-level
 * {@code @Validated} from the bean instance), but only fires when binding
 * actually runs — i.e., when the conditional admits the {@code @Bean} method.
 */
@Validated
public class AdminBootstrapProperties {

    // Note: the `enabled` flag is read by @ConditionalOnProperty directly from
    // the Spring Environment; it is intentionally NOT a field on this bean,
    // since binding it would create unread state on every populated instance.

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 12)
    @ValidPassword
    private String password;

    @NotBlank
    private String firstName = "System";

    @NotBlank
    private String lastName = "Admin";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
