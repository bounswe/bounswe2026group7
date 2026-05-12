package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiny Spring AOP shim that wraps Spring Data Neo4j repository calls in an
 * explicit {@code @Transactional("neo4jTransactionManager")} boundary.
 *
 * <p>Why a separate bean is necessary: in this codebase both JPA and SDN are on
 * the classpath. Spring Boot's auto-config makes {@code JpaTransactionManager}
 * the only (and primary) {@code PlatformTransactionManager} — the Neo4j one is
 * skipped by {@code @ConditionalOnMissingBean}. SDN repository writes therefore
 * have no transaction template to use, and fail with {@code "TransactionTemplate
 * .execute(...) because this.txTemplate is null"} when called from a
 * non-transactional context (which {@code @TransactionalEventListener
 * (AFTER_COMMIT)} guarantees by design).
 *
 * <p>{@code Neo4jTransactionConfig} declares the Neo4j tx manager under a named
 * bean; the methods on this class then route through Spring AOP so each call
 * opens its own dedicated Neo4j transaction.
 *
 * <p>Failure semantics: any Cypher failure bubbles up unchanged so the calling
 * {@code FollowGraphSyncListener} retry loop can decide whether to retry or
 * queue. The transactional boundary just guarantees the Cypher actually runs.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphWriter {

    private final FollowGraphRepository graph;

    public FollowGraphWriter(FollowGraphRepository graph) {
        this.graph = graph;
    }

    @Transactional("neo4jTransactionManager")
    public void apply(FollowChangedEvent event) {
        switch (event.type()) {
            case FOLLOWED -> {
                graph.mergeUser(event.followerId());
                graph.mergeUser(event.followeeId());
                graph.mergeFollow(event.followerId(), event.followeeId());
            }
            case UNFOLLOWED -> {
                graph.mergeUser(event.followerId());
                graph.mergeUser(event.followeeId());
                graph.deleteFollow(event.followerId(), event.followeeId());
            }
            case USER_DELETED -> graph.detachDeleteUser(event.followerId());
        }
    }

    /**
     * Replay a queued failure row. Same per-type dispatch as
     * {@link #apply(FollowChangedEvent)} but reads the inputs from the
     * persisted row.
     */
    @Transactional("neo4jTransactionManager")
    public void replay(FailedGraphSync row) {
        switch (row.getChangeType()) {
            case FOLLOWED -> {
                graph.mergeUser(row.getFollowerId());
                graph.mergeUser(row.getFolloweeId());
                graph.mergeFollow(row.getFollowerId(), row.getFolloweeId());
            }
            case UNFOLLOWED -> {
                graph.mergeUser(row.getFollowerId());
                graph.mergeUser(row.getFolloweeId());
                graph.deleteFollow(row.getFollowerId(), row.getFolloweeId());
            }
            case USER_DELETED -> graph.detachDeleteUser(row.getFollowerId());
        }
    }

    /** Full-rebuild support — used by the bootstrap and resync paths. */
    @Transactional("neo4jTransactionManager")
    public void deleteAllFollowEdges() {
        graph.deleteAllFollowEdges();
    }

    @Transactional("neo4jTransactionManager")
    public void mergeFollow(Long followerId, Long followeeId) {
        graph.mergeUser(followerId);
        graph.mergeUser(followeeId);
        graph.mergeFollow(followerId, followeeId);
    }

    /** Read-side count used for drift detection. Read-only transaction. */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public long countAllFollows() {
        return graph.countAllFollows();
    }

    /**
     * Personalized PageRank — read-only Cypher against the GDS projection.
     * Wrapped in a Neo4j tx (read-only) so Spring Data Neo4j has a
     * TransactionTemplate available — without this the call would fail with
     * "TransactionTemplate.execute(...) because this.txTemplate is null"
     * (the PR 1 bug we already cured for the write paths).
     */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public java.util.List<com.group7.backend.repository.graph.UserScoreProjection>
            personalizedPageRank(java.util.List<Long> seedUserIds, double alpha, int iterations) {
        return graph.personalizedPageRank(seedUserIds, alpha, iterations);
    }
}
