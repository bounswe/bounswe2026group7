package com.group7.backend.config;

import com.group7.backend.config.ratelimit.RateLimitFilter;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Optional<RateLimitFilter> rateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String[] allowedOrigins;

    /**
     * {@code rateLimitFilter} is wrapped in {@link Optional} so {@code @WebMvcTest}
     * controller slices that import {@link SecurityConfig} without the rate-limit
     * beans still wire a filter chain.
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          Optional<RateLimitFilter> rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**"
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/uploads/photos/**").permitAll()
                // /api/uploads/attachments/** is intentionally NOT permitAll —
                // chat attachments are private message content and the
                // download path lives behind AttachmentDownloadController,
                // which enforces JWT auth and conversation-participant ACL.
                // The /ws/chat HTTP handshake is permitted; the
                // JwtChannelInterceptor authenticates the STOMP CONNECT frame
                // before any subscription or send is allowed.
                .requestMatchers("/ws/chat/**").permitAll()
                // Mentor-availability iCalendar exports (#250). Calendar apps
                // (Google, Apple, Outlook) cannot send Authorization headers
                // on subscription URLs, so this is anonymous by necessity.
                // Data exposed (mentor name + availability) is already visible
                // to any authenticated user via GET /{mentorId}; this is a
                // transport concession, not a sensitivity change. Drive-by
                // enumeration is rate-limited by IP via the
                // {@code availability-ical} rule in application.properties.
                .requestMatchers(HttpMethod.GET, "/api/availability/*/ical").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        rateLimitFilter.ifPresent(filter ->
                http.addFilterAfter(filter, JwtAuthenticationFilter.class));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins) {
            config.addAllowedOrigin(origin.trim());
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
