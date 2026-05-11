package com.group7.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j configuration anchor for the follow-graph mirror (#437).
 *
 * <p>Always active so the repository scan stays narrowed to
 * {@code repository.graph} — without this, Spring Data Neo4j's default
 * auto-configuration would attempt to identify every JPA repository as
 * a candidate Neo4j repository, producing 40+ noisy "Could not safely
 * identify store assignment" log entries at startup.
 *
 * <p>The Bolt {@code Driver} bean is auto-configured by Spring Boot from
 * {@code spring.neo4j.*} properties and is <b>lazy</b>: instantiation does
 * not open a connection, so the application boots whether or not Neo4j is
 * actually reachable. The functional gates that actually <i>use</i> the
 * driver ({@code FollowGraphSyncListener}, {@code FollowGraphResyncJob},
 * {@code Neo4jConfig}'s downstream beans in PR 2) are
 * {@code @ConditionalOnProperty(app.recommendations.follow.sync.enabled)}
 * so no Neo4j query is issued until the sync flag is flipped on.
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.group7.backend.repository.graph")
public class Neo4jConfig {
}
