package com.group7.backend.repository;

import com.group7.backend.entity.Ban;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BanRepository extends JpaRepository<Ban, Long> {

    /**
     * Returns the user's currently-active ban, if any. Active = not lifted
     * AND not expired. Orders by {@code expiresAt DESC} so that if there
     * happen to be multiple overlapping rows (admin-created edge cases) the
     * one with the latest expiry wins.
     */
    @Query("""
            SELECT b FROM Ban b
            WHERE b.user.id = :userId
              AND b.liftedAt IS NULL
              AND b.expiresAt > :now
            ORDER BY b.expiresAt DESC
            """)
    Optional<Ban> findActive(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /** Audit: full history newest-first. */
    List<Ban> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** Scheduler sweep — rows whose timer ran out and that haven't been notified. */
    @Query("""
            SELECT b FROM Ban b
            WHERE b.expiresAt <= :now
              AND b.expiryNotified = FALSE
              AND b.liftedAt IS NULL
            """)
    List<Ban> findExpiredUnnotified(@Param("now") OffsetDateTime now);

    /** Total bans ever imposed on this user — drives the escalation ordinal. */
    long countByUser_Id(Long userId);
}
