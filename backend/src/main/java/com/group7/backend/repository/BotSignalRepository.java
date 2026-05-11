package com.group7.backend.repository;

import com.group7.backend.entity.BotSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface BotSignalRepository extends JpaRepository<BotSignal, Long> {

    /** Sliding-window count for the per-IP suspicion threshold (#345). */
    long countByIpAndCreatedAtAfter(String ip, OffsetDateTime since);

    /** Sliding-window count for the per-email suspicion threshold (#345). */
    long countByEmailHashAndCreatedAtAfter(String emailHash, OffsetDateTime since);

    /**
     * Retention sweep — purges rows older than the configured retention. Run
     * daily by {@code BotSignalCleanupScheduler}; mirrors the
     * attachment-orphan cleanup pattern from #22.
     */
    @Modifying
    @Query("DELETE FROM BotSignal s WHERE s.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
