package com.group7.backend.entity;

import com.group7.backend.event.FollowChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 16, nullable = false)
    private FollowChangedEvent.ChangeType changeType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private OffsetDateTime failedAt;

    @Column(name = "resynced_at")
    private OffsetDateTime resyncedAt;

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) {
            failedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public static FailedGraphSync from(FollowChangedEvent event, String failureReason) {
        FailedGraphSync row = new FailedGraphSync();
        row.setFollowerId(event.followerId());
        row.setFolloweeId(event.followeeId());
        row.setChangeType(event.type());
        row.setFailureReason(failureReason);
        return row;
    }
}
