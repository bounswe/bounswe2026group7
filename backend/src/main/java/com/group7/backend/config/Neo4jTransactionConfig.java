package com.group7.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Co-located JPA and Neo4j {@link org.springframework.transaction.PlatformTransactionManager}s
 * so Spring Data Neo4j repositories can open their own transactions inside our
 * {@code @TransactionalEventListener(AFTER_COMMIT)} flow while JPA-side
 * {@code @Transactional} keeps working unchanged.
 *
 * <p>Background: when both JPA and SDN are on the classpath, Spring Boot's auto-config
 * for either side is gated by {@code @ConditionalOnMissingBean(TransactionManager.class)}.
 * Whichever auto-config wires its manager first prevents the other from ever wiring.
 * In practice JPA wins, so SDN repository writes called from a non-transactional context
 * fail with {@code "TransactionTemplate.execute(...) because this.txTemplate is null"}.
 *
 * <p>This config side-steps that by declaring BOTH managers explicitly:
 * <ul>
 *   <li>{@code transactionManager} ({@code @Primary}, {@code JpaTransactionManager}) —
 *       used by every default {@code @Transactional} site in the codebase, unchanged.</li>
 *   <li>{@code neo4jTransactionManager} — used explicitly by
 *       {@code @Transactional("neo4jTransactionManager")} in {@code FollowGraphWriter}.</li>
 * </ul>
 *
 * <p>Gated by the same flag as the rest of the follow-graph sync layer so plain JPA
 * boots (sync disabled) are not affected — Spring Boot's regular JPA auto-config wires
 * the {@code transactionManager} as before when this config is absent.
 */
@Configuration
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class Neo4jTransactionConfig {

    @Bean
    @Primary
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(
            Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}
