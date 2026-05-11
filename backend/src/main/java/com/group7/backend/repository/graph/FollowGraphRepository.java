package com.group7.backend.repository.graph;

import com.group7.backend.entity.graph.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data Neo4j repository for the follow-graph mirror (#437).
 *
 * <p>Writes are issued by {@code FollowGraphSyncListener} after the
 * matching Postgres transaction commits. The mirror is intentionally
 * minimal: nodes carry only {@code userId}, edges carry no properties.
 *
 * <p>The Personalized PageRank query lives in {@code FollowGraphRepository}
 * in PR 2 once the GDS projection lifecycle is in place; this PR ships only
 * the CRUD surface required by the sync layer.
 */
public interface FollowGraphRepository extends Neo4jRepository<UserNode, Long> {

    /** Idempotent node upsert. Caller does not need to check existence. */
    @Query("MERGE (u:User {userId: $userId})")
    void mergeUser(@Param("userId") Long userId);

    /**
     * Idempotent edge upsert. Both endpoint nodes must exist — call
     * {@link #mergeUser(Long)} for follower and followee first.
     */
    @Query("""
            MATCH (a:User {userId: $followerId}), (b:User {userId: $followeeId})
            MERGE (a)-[:FOLLOWS]->(b)
            """)
    void mergeFollow(@Param("followerId") Long followerId,
                     @Param("followeeId") Long followeeId);

    /** No-op if the edge does not exist. */
    @Query("""
            MATCH (:User {userId: $followerId})-[r:FOLLOWS]->(:User {userId: $followeeId})
            DELETE r
            """)
    void deleteFollow(@Param("followerId") Long followerId,
                      @Param("followeeId") Long followeeId);

    /** Drift detection — compared against Postgres {@code follows} row count. */
    @Query("MATCH ()-[r:FOLLOWS]->() RETURN count(r)")
    long countAllFollows();

    /**
     * Wipes every {@code FOLLOWS} relationship without touching the
     * {@code :User} nodes. Used by {@code FollowGraphResyncJob}'s full
     * rebuild path so cached node references stay valid.
     */
    @Query("MATCH ()-[r:FOLLOWS]->() DELETE r")
    void deleteAllFollowEdges();

    /**
     * Removes a user node and every incident {@code :FOLLOWS} edge in one
     * Cypher call. Mirrors the Postgres-side {@code ON DELETE CASCADE} on
     * {@code follows}, fired by {@code FollowGraphSyncListener} when a
     * {@link com.group7.backend.event.FollowChangedEvent} of type
     * {@code USER_DELETED} arrives.
     */
    @Query("MATCH (u:User {userId: $userId}) DETACH DELETE u")
    void detachDeleteUser(@Param("userId") Long userId);
}
