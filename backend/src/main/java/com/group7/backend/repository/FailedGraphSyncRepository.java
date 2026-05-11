package com.group7.backend.repository;

import com.group7.backend.entity.FailedGraphSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Postgres-backed queue of follow-graph mutations that failed to replay
 * onto Neo4j (#437). Drained by {@code FollowGraphResyncJob}.
 */
public interface FailedGraphSyncRepository extends JpaRepository<FailedGraphSync, Long> {

    /**
     * Rows still awaiting replay onto Neo4j, ordered oldest first so the
     * job processes them in the same order they failed.
     */
    @Query("""
            select f from FailedGraphSync f
            where f.resyncedAt is null
            order by f.failedAt asc, f.id asc
            """)
    List<FailedGraphSync> findAllUnsynced();

    long countByResyncedAtIsNull();

    /**
     * Deletes resynced rows older than {@code threshold} — called by the
     * nightly resync job to keep the queue from growing unboundedly. Only
     * touches rows where {@code resyncedAt IS NOT NULL} so unprocessed
     * failures stay queued even if they're old.
     *
     * @return number of rows deleted (driven by Spring Data's @Modifying
     *         delete return type).
     */
    @Modifying
    @Query("""
            delete from FailedGraphSync f
            where f.resyncedAt is not null
              and f.resyncedAt < :threshold
            """)
    int deleteResyncedOlderThan(@Param("threshold") OffsetDateTime threshold);
}
