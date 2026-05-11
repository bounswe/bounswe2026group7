package com.group7.backend.repository;

import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BanRepository extends JpaRepository<Ban, Long> {

    /**
     * Returns currently-active ban rows for the user (active = not lifted
     * AND not expired), ordered {@code expiresAt DESC} so that if there
     * happen to be multiple overlapping rows (admin-created edge cases) the
     * one with the latest expiry comes first. Callers pass
     * {@code PageRequest.of(0, 1)} and take {@code .stream().findFirst()};
     * this avoids the {@code @Query + Optional<T>}
     * {@code IncorrectResultSizeDataAccessException} trap that Spring Data
     * would otherwise throw on a multi-row result.
     */
    @Query("""
            SELECT b FROM Ban b
            WHERE b.user.id = :userId
              AND b.liftedAt IS NULL
              AND b.expiresAt > :now
            ORDER BY b.expiresAt DESC
            """)
    List<Ban> findActive(@Param("userId") Long userId,
                         @Param("now") OffsetDateTime now,
                         Pageable pageable);

    /**
     * Same active-ban lookup as {@link #findActive}, additionally constrained
     * to a specific {@link BanSource}. Used by the spam clear-flag path so an
     * admin lift on the bot heuristic cannot accidentally touch a parallel
     * admin ban that happens to expire later.
     */
    @Query("""
            SELECT b FROM Ban b
            WHERE b.user.id = :userId
              AND b.source = :source
              AND b.liftedAt IS NULL
              AND b.expiresAt > :now
            ORDER BY b.expiresAt DESC
            """)
    List<Ban> findActiveBySource(@Param("userId") Long userId,
                                 @Param("source") BanSource source,
                                 @Param("now") OffsetDateTime now,
                                 Pageable pageable);

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

    /**
     * Counts the user's bans that have not been admin-lifted — drives the
     * escalation ordinal so an admin override is a real override and the
     * user's next legitimate ban does not double on top of a lifted one.
     */
    @Query("SELECT COUNT(b) FROM Ban b WHERE b.user.id = :userId AND b.liftedAt IS NULL")
    long countNonLiftedByUserId(@Param("userId") Long userId);
}
