package com.group7.backend.repository.graph;

/**
 * Projection used by {@code FollowGraphRepository.personalizedPageRank}.
 * Spring Data Neo4j 7.x binds named columns from the {@code RETURN} clause
 * by record component name; the Cypher returns
 * {@code u.userId AS userId, score} so the components must match exactly.
 */
public record UserScoreProjection(Long userId, double score) {
}
