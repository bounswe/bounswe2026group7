package com.group7.backend.entity;

import com.group7.backend.event.FollowChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Queued record of a follow-graph mutation that could not be replayed
 * onto Neo4j after the source-of-truth Postgres write committed (#437).
 * Consumed by {@code FollowGraphResyncJob} on its nightly run.
 *
 * <p>{@code resyncedAt} is set when the queued event is successfully
 * replayed; a partial index on rows where it remains null keeps the
 * recovery sweep cheap.
 */
@Entity
@Table(name = "failed_graph_syncs")
@Getter
@Setter
@NoArgsConstructor
public class FailedGraphSync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    /**
     * Null when {@link #changeType} is {@code USER_DELETED} — the deleted
     * user's id is carried in {@link #followerId} only and there is no second
     * endpoint. A DB-side CHECK constraint enforces the invariant per row.
     */
    @Column(name = "followee_id")
    private Long followeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 16, nullable = false)
    private FollowChangedEvent.ChangeType changeType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Stamped by {@link com.group7.backend.service.graph.FailedGraphSyncWriter}
     * using the Spring {@code Clock} bean — single source of time for the
     * codebase, testable via {@code Clock.fixed}.
     */
    @Column(name = "failed_at", nullable = false, updatable = false)
    private OffsetDateTime failedAt;

    @Column(name = "resynced_at")
    private OffsetDateTime resyncedAt;

    public static FailedGraphSync from(FollowChangedEvent event, String failureReason) {
        FailedGraphSync row = new FailedGraphSync();
        row.setFollowerId(event.followerId());
        row.setFolloweeId(event.followeeId());
        row.setChangeType(event.type());
        row.setFailureReason(failureReason);
        return row;
    }
}
