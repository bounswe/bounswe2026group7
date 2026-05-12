package com.group7.backend.repository.graph;

import com.group7.backend.entity.graph.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    /**
     * Personalized PageRank seeded from the viewer's followee set. Reads
     * the in-memory GDS projection {@code follow-graph} populated by
     * {@code FollowGraphProjectionService}.
     *
     * <p>The pattern comprehension resolves Postgres-style {@code userId}s
     * to Neo4j internal node ids in the same Cypher round-trip — GDS's
     * {@code sourceNodes} parameter wants internal ids, not application
     * ids. {@code WHERE NOT u.userId IN $seedUserIds} drops the viewer's
     * own followees from the result so they don't appear as
     * recommendations.
     *
     * <p>Returns at most 200 candidates sorted by PR score desc. The
     * service layer caches the result per viewer.
     */
    @Query("""
            MATCH (seed:User) WHERE seed.userId IN $seedUserIds
            WITH collect(seed) AS seedNodes, $seedUserIds AS seedUserIds
            CALL gds.pageRank.stream('follow-graph', {
                sourceNodes: seedNodes,
                dampingFactor: $alpha,
                maxIterations: $iterations
            })
            YIELD nodeId, score
            WITH gds.util.asNode(nodeId) AS u, score, seedUserIds
            WHERE NOT u.userId IN seedUserIds
            RETURN u.userId AS userId, score
            ORDER BY score DESC LIMIT 200
            """)
    List<UserScoreProjection> personalizedPageRank(
            @Param("seedUserIds") List<Long> seedUserIds,
            @Param("alpha") double alpha,
            @Param("iterations") int iterations);
}
