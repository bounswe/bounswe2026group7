package com.group7.backend.config;

import com.group7.backend.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(prefix = "app.admin-bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "app.admin-bootstrap")
    public AdminBootstrapProperties adminBootstrapProperties() {
        return new AdminBootstrapProperties();
    }

    @Bean
    public AdminBootstrapper adminBootstrapper(AdminBootstrapProperties props,
                                               UserRepository userRepository,
                                               PasswordEncoder passwordEncoder) {
        return new AdminBootstrapper(props, userRepository, passwordEncoder);
    }
}
