package com.group7.backend.repository;

import com.group7.backend.entity.Mentor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

public interface MentorRepository extends JpaRepository<Mentor, Long> {

    /**
     * Shared JPQL for {@link #searchByFilters} (Page, with count) and
     * {@link #findRankingCandidates} (List, without count). One source of
     * truth for the filter shape; the only difference between the two
     * methods is whether Spring Data emits a count query.
     *
     * <p>{@code ORDER BY m.id DESC} is required — Postgres returns rows in
     * arbitrary order otherwise, and the matching path's top-200 oversample
     * becomes a random sample rather than a meaningful one.
     */
    String SEARCH_JPQL = """
            SELECT m FROM Mentor m
            WHERE (:keyword IS NULL OR
                   LOWER(m.expertise)      LIKE :keyword ESCAPE '|' OR
                   LOWER(m.field)          LIKE :keyword ESCAPE '|' OR
                   LOWER(m.mentoringGoals) LIKE :keyword ESCAPE '|' OR
                   EXISTS (SELECT 1 FROM m.interests i             WHERE LOWER(i.label) LIKE :keyword ESCAPE '|') OR
                   EXISTS (SELECT 1 FROM m.preferredMenteeSkills s WHERE LOWER(s.label) LIKE :keyword ESCAPE '|'))
            AND (:interests IS NULL OR
                 EXISTS (SELECT 1 FROM m.interests i WHERE LOWER(i.label) IN :interests))
            AND (:skills IS NULL OR
                 EXISTS (SELECT 1 FROM m.preferredMenteeSkills s WHERE LOWER(s.label) IN :skills))
            AND (:major IS NULL OR
                 LOWER(m.preferredMenteeMajor) = :major OR
                 LOWER(m.field) = :major)
            AND (:requireCapacity = false OR m.currentMenteeCount < m.maxMenteeCapacity)
            AND (:bypassVisibility = true OR m.profileVisibility = true)
            AND (:requesterMenteeId IS NULL OR
                 EXISTS (SELECT 1 FROM AvailabilitySlot ms, MenteeAvailabilitySlot mes
                         WHERE ms.mentor.id = m.id
                           AND mes.mentee.id = :requesterMenteeId
                           AND ms.dayOfWeek = mes.dayOfWeek
                           AND ms.startTime < mes.endTime
                           AND mes.startTime < ms.endTime))
            AND (:availabilityDays IS NULL OR
                 EXISTS (SELECT 1 FROM AvailabilitySlot s
                         WHERE s.mentor.id = m.id
                           AND s.dayOfWeek IN :availabilityDays))
            AND (:mentorshipDuration IS NULL OR m.mentorshipDuration IN :mentorshipDuration)
            ORDER BY m.id DESC
            """;

    List<Mentor> findByExpertise(String expertise);

    /**
     * DB-level mentor search with composable filters. All multi-value filters
     * use {@code EXISTS} (never {@code JOIN} on element collections) to avoid
     * DISTINCT entanglements with Spring Data's auto-derived count query.
     *
     * <p><b>Parameter contract — required for correctness:</b>
     * <ul>
     *   <li>{@code keyword}: pre-lowercased, wildcard-escaped, wrapped in
     *       {@code %...%}, or {@code null} to skip the keyword filter.
     *       Hibernate would translate an empty string to a SQL parameter that
     *       LIKE-matches everything; null is the correct "no filter" signal.</li>
     *   <li>{@code interests} / {@code skills}: pre-lowercased lists, or
     *       {@code null} to skip. Empty lists must be coalesced to null at the
     *       service layer — Hibernate translates a non-null empty list to
     *       {@code IN ()} which Postgres rejects.</li>
     *   <li>{@code major}: pre-lowercased + trimmed, or {@code null}.</li>
     *   <li>{@code requireCapacity}: when true, excludes mentors at full
     *       capacity. Used by the matching path; general search passes false.</li>
     *   <li>{@code requesterMenteeId}: when non-null, restricts results to
     *       mentors whose availability slots overlap that mentee's slots
     *       (day-of-week + strict-inequality time overlap). Backed by the
     *       {@code idx_mentor_avail_mentor_day} composite index.</li>
     *   <li>{@code availabilityDays}: when non-null, restricts to mentors
     *       whose availability slots fall on any of the requested days
     *       (OR semantics across days). Empty sets must be coalesced to
     *       {@code null} at the service layer for the same Postgres reason
     *       as the lists above.</li>
     *   <li>{@code mentorshipDuration}: when non-null, restricts to mentors
     *       whose {@code mentorshipDuration} (months) is in the requested
     *       set. Same empty-set rule applies.</li>
     * </ul>
     *
     * <p>Used by {@code GET /api/users/search} where {@code totalElements} is
     * rendered to the client, justifying the count query. The matching path
     * uses {@link #findRankingCandidates} instead to skip the count.
     */
    @Query(SEARCH_JPQL)
    Page<Mentor> searchByFilters(
            @Param("keyword") String keyword,
            @Param("interests") List<String> interests,
            @Param("skills") List<String> skills,
            @Param("major") String major,
            @Param("requireCapacity") boolean requireCapacity,
            @Param("bypassVisibility") boolean bypassVisibility,
            @Param("requesterMenteeId") Long requesterMenteeId,
            @Param("availabilityDays") Set<DayOfWeek> availabilityDays,
            @Param("mentorshipDuration") Set<Integer> mentorshipDuration,
            Pageable pageable);

    /**
     * Same filter shape as {@link #searchByFilters} but returns a List rather
     * than a Page — Spring Data does not derive a count query for List-
     * returning methods, even when a {@code Pageable} is supplied. Saves one
     * query per call on the matching path, which never reads
     * {@code totalElements} (it computes pagination metadata against an
     * in-memory ranked window of bounded size).
     */
    @Query(SEARCH_JPQL)
    List<Mentor> findRankingCandidates(
            @Param("keyword") String keyword,
            @Param("interests") List<String> interests,
            @Param("skills") List<String> skills,
            @Param("major") String major,
            @Param("requireCapacity") boolean requireCapacity,
            @Param("bypassVisibility") boolean bypassVisibility,
            @Param("requesterMenteeId") Long requesterMenteeId,
            @Param("availabilityDays") Set<DayOfWeek> availabilityDays,
            @Param("mentorshipDuration") Set<Integer> mentorshipDuration,
            Pageable pageable);
}
