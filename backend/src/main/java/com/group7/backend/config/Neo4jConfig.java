package com.group7.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j configuration anchor for the follow-graph mirror (#437).
 *
 * <p>Only active when the follow-graph sync is enabled
 * ({@code app.recommendations.follow.sync.enabled=true}). Until the
 * sync flag is flipped on, the Neo4j repositories are never scanned and
 * the application boots without requiring a reachable Neo4j instance —
 * the legacy ranker continues to work against Postgres alone.
 *
 * <p>The Bolt {@code Driver} bean itself is auto-configured by Spring Boot
 * from {@code spring.neo4j.*} properties; this class only narrows the
 * repository-scan to {@code repository.graph} so JPA repositories elsewhere
 * are not accidentally treated as Neo4j ones.
 */
@Configuration
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
@EnableNeo4jRepositories(basePackages = "com.group7.backend.repository.graph")
public class Neo4jConfig {
}
