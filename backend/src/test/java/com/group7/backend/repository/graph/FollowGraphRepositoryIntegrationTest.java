package com.group7.backend.repository.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips every Cypher query in {@link FollowGraphRepository} against a
 * real Neo4j 5 instance brought up by Testcontainers. Pure mock tests verify
 * the listener / job logic; this test guarantees the Cypher actually parses
 * and the schema (node label, relationship type) is consistent between code
 * and store.
 *
 * <p>Each test starts from a clean graph — {@link #wipeGraph()} clears
 * everything before every test method so order-independence and isolation
 * are explicit. The container is reused across tests in the class (one
 * Neo4j startup is ~10s).
 *
 * <p>Slice annotation is {@link DataNeo4jTest} so JPA / web layers don't
 * load — only the Neo4j driver + repositories under test.
 */
@Testcontainers
@DataNeo4jTest
class FollowGraphRepositoryIntegrationTest {

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>(DockerImageName.parse("neo4j:5-community"))
            .withoutAuthentication()
            .withReuse(false);

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "none");
    }

    @Autowired private FollowGraphRepository graph;

    @BeforeEach
    void wipeGraph() {
        // DataNeo4jTest gives us an SDN repository but no built-in cleanup.
        // deleteAll() removes all UserNode nodes (label :User) plus their
        // attached :FOLLOWS relationships via detach.
        graph.deleteAll();
    }

    @Test
    void mergeUser_isIdempotent() {
        graph.mergeUser(101L);
        graph.mergeUser(101L);   // second call must not duplicate

        assertThat(graph.count()).isEqualTo(1L);
        assertThat(graph.findById(101L)).isPresent();
    }

    @Test
    void mergeFollow_createsEdgeBetweenTwoUsers() {
        graph.mergeUser(101L);
        graph.mergeUser(202L);

        graph.mergeFollow(101L, 202L);

        assertThat(graph.countAllFollows()).isEqualTo(1L);
    }

    @Test
    void mergeFollow_isIdempotent() {
        graph.mergeUser(101L);
        graph.mergeUser(202L);

        graph.mergeFollow(101L, 202L);
        graph.mergeFollow(101L, 202L);
        graph.mergeFollow(101L, 202L);

        assertThat(graph.countAllFollows()).isEqualTo(1L);
    }

    @Test
    void mergeFollow_isDirectional() {
        graph.mergeUser(101L);
        graph.mergeUser(202L);

        graph.mergeFollow(101L, 202L);  // a → b only

        assertThat(graph.countAllFollows()).isEqualTo(1L);
        // The reverse edge does not exist; calling delete on it is a no-op.
        graph.deleteFollow(202L, 101L);
        assertThat(graph.countAllFollows()).isEqualTo(1L);
    }

    @Test
    void deleteFollow_removesOnlyTheTargetedEdge() {
        graph.mergeUser(101L);
        graph.mergeUser(202L);
        graph.mergeUser(303L);
        graph.mergeFollow(101L, 202L);
        graph.mergeFollow(101L, 303L);

        graph.deleteFollow(101L, 202L);

        assertThat(graph.countAllFollows()).isEqualTo(1L);
        // The 101 → 303 edge survives.
        graph.deleteFollow(101L, 303L);
        assertThat(graph.countAllFollows()).isZero();
    }

    @Test
    void deleteFollow_isSilent_whenEdgeDoesNotExist() {
        graph.mergeUser(101L);
        graph.mergeUser(202L);

        // No edge was created; deletion should be a silent no-op (matches the
        // FollowService.unfollow Spring-Data-style contract).
        graph.deleteFollow(101L, 202L);

        assertThat(graph.countAllFollows()).isZero();
    }

    @Test
    void deleteAllFollowEdges_clearsRelationshipsButKeepsNodes() {
        // Full-rebuild semantics in FollowGraphResyncJob: relationships are
        // wiped, then re-emitted from Postgres. Nodes must survive so any
        // cached references stay valid.
        graph.mergeUser(101L);
        graph.mergeUser(202L);
        graph.mergeFollow(101L, 202L);

        graph.deleteAllFollowEdges();

        assertThat(graph.countAllFollows()).isZero();
        assertThat(graph.count()).isEqualTo(2L);  // :User nodes survive
    }

    @Test
    void countAllFollows_returnsZero_onEmptyGraph() {
        assertThat(graph.countAllFollows()).isZero();
    }

    @Test
    void detachDeleteUser_removesUserAndAllIncidentEdges() {
        // Mirrors the Postgres ON DELETE CASCADE on follows: deleting Alice
        // must remove every outgoing AND every incoming :FOLLOWS edge in a
        // single Cypher call, then drop the node itself.
        graph.mergeUser(101L);          // Alice
        graph.mergeUser(202L);          // Bob
        graph.mergeUser(303L);          // Carol
        graph.mergeFollow(101L, 202L);  // Alice → Bob (outgoing)
        graph.mergeFollow(101L, 303L);  // Alice → Carol (outgoing)
        graph.mergeFollow(202L, 101L);  // Bob → Alice (incoming)
        graph.mergeFollow(303L, 101L);  // Carol → Alice (incoming)

        graph.detachDeleteUser(101L);

        // Alice is gone; her three other followers/followees still exist.
        assertThat(graph.findById(101L)).isEmpty();
        assertThat(graph.findById(202L)).isPresent();
        assertThat(graph.findById(303L)).isPresent();
        // Every edge incident to Alice (outgoing AND incoming) is gone.
        assertThat(graph.countAllFollows()).isZero();
    }

    @Test
    void detachDeleteUser_isSilent_whenUserDoesNotExist() {
        // A stale USER_DELETED event arriving after the node is already
        // gone (e.g. re-played from failed_graph_syncs) must be a no-op.
        graph.mergeUser(101L);

        graph.detachDeleteUser(999L);   // never existed

        assertThat(graph.count()).isEqualTo(1L);
        assertThat(graph.findById(101L)).isPresent();
    }
}
