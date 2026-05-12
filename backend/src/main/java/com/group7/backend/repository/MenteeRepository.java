package com.group7.backend.repository;

import com.group7.backend.entity.Mentee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenteeRepository extends JpaRepository<Mentee, Long> {

    /**
     * IDs of all unattached mentees, ordered for test determinism. Used by
     * {@code MatchNotificationScheduler} as the eligibility list — only
     * unattached mentees are candidates for a "match found" notification.
     * Returns just IDs to keep the per-tick memory bounded; the processor
     * re-loads each mentee in its own transaction for the race re-check.
     */
    @Query("SELECT m.id FROM Mentee m WHERE m.activeMentorId IS NULL ORDER BY m.id")
    List<Long> findUnattachedIds();

    /**
     * Shared JPQL — see {@link MentorRepository#SEARCH_JPQL} for rationale.
     */
    String SEARCH_JPQL = """
            SELECT m FROM Mentee m
            WHERE (:keyword IS NULL OR
                   LOWER(m.goals)           LIKE :keyword ESCAPE '|' OR
                   LOWER(m.major)           LIKE :keyword ESCAPE '|' OR
                   LOWER(m.careerInterest)  LIKE :keyword ESCAPE '|' OR
                   LOWER(m.backgroundInfo)  LIKE :keyword ESCAPE '|' OR
                   EXISTS (SELECT 1 FROM m.interests i WHERE LOWER(i.label) LIKE :keyword ESCAPE '|') OR
                   EXISTS (SELECT 1 FROM m.skills s    WHERE LOWER(s.label) LIKE :keyword ESCAPE '|'))
            AND (:interests IS NULL OR
                 EXISTS (SELECT 1 FROM m.interests i WHERE LOWER(i.label) IN :interests))
            AND (:skills IS NULL OR
                 EXISTS (SELECT 1 FROM m.skills s WHERE LOWER(s.label) IN :skills))
            AND (:major IS NULL OR LOWER(m.major) = :major)
            AND (:requireUnattached = false OR m.activeMentorId IS NULL)
            AND (:bypassVisibility = true OR m.profileVisibility = true)
            AND (:requesterMentorId IS NULL OR
                 EXISTS (SELECT 1 FROM AvailabilitySlot ms, MenteeAvailabilitySlot mes
                         WHERE mes.mentee.id = m.id
                           AND ms.mentor.id = :requesterMentorId
                           AND ms.dayOfWeek = mes.dayOfWeek
                           AND ms.startTime < mes.endTime
                           AND mes.startTime < ms.endTime))
            ORDER BY m.id DESC
            """;

    /**
     * DB-level mentee search with composable filters. Symmetric to
     * {@link MentorRepository#searchByFilters} — see its javadoc for the
     * parameter normalisation contract; the same null-coalescing and
     * lowercasing rules apply here.
     *
     * <p>The mentee-side has one extra filter: {@code requireUnattached}
     * excludes mentees with a non-null {@code activeMentorId} (already
     * partnered with a mentor). Used by the matching path's
     * {@code getCandidateMentees}; general search passes false.
     */
    @Query(SEARCH_JPQL)
    Page<Mentee> searchByFilters(
            @Param("keyword") String keyword,
            @Param("interests") List<String> interests,
            @Param("skills") List<String> skills,
            @Param("major") String major,
            @Param("requireUnattached") boolean requireUnattached,
            @Param("bypassVisibility") boolean bypassVisibility,
            @Param("requesterMentorId") Long requesterMentorId,
            Pageable pageable);

    /**
     * Same filter shape as {@link #searchByFilters} but returns List — no
     * count query. Used by the matching path's candidate-mentee fetch where
     * {@code totalElements} is computed in-memory after the post-filter.
     */
    @Query(SEARCH_JPQL)
    List<Mentee> findRankingCandidates(
            @Param("keyword") String keyword,
            @Param("interests") List<String> interests,
            @Param("skills") List<String> skills,
            @Param("major") String major,
            @Param("requireUnattached") boolean requireUnattached,
            @Param("bypassVisibility") boolean bypassVisibility,
            @Param("requesterMentorId") Long requesterMentorId,
            Pageable pageable);
}
