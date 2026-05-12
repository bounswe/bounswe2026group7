package com.group7.backend.entity.graph;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j node mirroring a Postgres {@code users.id} (#437).
 *
 * <p>The graph store is a derived read-only mirror used exclusively for
 * graph-algorithm computation (Personalized PageRank via GDS). Postgres
 * remains the source of truth — node creation flows from
 * {@code FollowGraphSyncListener} after the SQL transaction commits.
 *
 * <p>Only {@code userId} is mirrored; no other user attributes are needed
 * for the supported algorithms. Relationships are typed
 * {@code (:User)-[:FOLLOWS]->(:User)}.
 */
@Node("User")
public record UserNode(@Id Long userId) {
}
