package com.group7.backend.repository;

import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByMentorshipIdOrderByStartTimeAsc(Long mentorshipId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END "
            + "FROM Meeting m JOIN m.mentorship ms "
            + "WHERE (ms.mentor.id = :mentorId OR ms.mentee.id = :menteeId) "
            + "AND m.status IN :statuses "
            + "AND m.startTime < :endAt AND m.endTime > :startAt")
    boolean existsOverlappingForParticipants(
            @Param("mentorId") Long mentorId,
            @Param("menteeId") Long menteeId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("statuses") Collection<MeetingStatus> statuses);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END "
            + "FROM Meeting m JOIN m.mentorship ms "
            + "WHERE m.id <> :meetingId "
            + "AND (ms.mentor.id = :mentorId OR ms.mentee.id = :menteeId) "
            + "AND m.status IN :statuses "
            + "AND m.startTime < :endAt AND m.endTime > :startAt")
    boolean existsOverlappingForParticipantsExcludingMeeting(
            @Param("meetingId") Long meetingId,
            @Param("mentorId") Long mentorId,
            @Param("menteeId") Long menteeId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("statuses") Collection<MeetingStatus> statuses);

    @Query("SELECT m FROM Meeting m "
            + "WHERE m.status = :status AND m.confirmationDeadline <= :cutoff")
    List<Meeting> findByStatusAndConfirmationDeadlineBefore(
            @Param("status") MeetingStatus status,
            @Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT m FROM Meeting m "
            + "WHERE m.status = :status AND m.endTime <= :cutoff")
    List<Meeting> findByStatusAndEndTimeBefore(
            @Param("status") MeetingStatus status,
            @Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT m FROM Meeting m "
            + "WHERE m.status = :status AND m.startTime BETWEEN :windowStart AND :windowEnd")
    List<Meeting> findByStatusAndStartTimeBetween(
            @Param("status") MeetingStatus status,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd);

    /**
     * Meetings of a mentorship whose {@code startTime} falls inside the inclusive
     * {@code [from, to]} window. Used by the mentorship timeline aggregation (#332).
     *
     * <p>The window predicate anchors on {@code startTime} only — a meeting that began
     * before {@code from} and ended inside the window is excluded. This matches the
     * timeline's "chronological position = startTime" model. If the product wants
     * straddling meetings to surface in their endpoint window, change the predicate to
     * {@code m.startTime <= :to AND m.endTime >= :from}.
     */
    @Query("SELECT m FROM Meeting m "
            + "WHERE m.mentorship.id = :mentorshipId "
            + "AND m.startTime BETWEEN :from AND :to")
    List<Meeting> findInWindow(
            @Param("mentorshipId") Long mentorshipId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Modifying
    @Query("UPDATE Meeting m SET m.status = 'EXPIRED' WHERE m.id = :meetingId AND m.status = 'PENDING_CONFIRMATION'")
    int expireMeeting(@Param("meetingId") Long meetingId);

    @Modifying
    @Query("UPDATE Meeting m SET m.status = 'COMPLETED' WHERE m.id = :meetingId AND m.status = 'CONFIRMED'")
    int completeMeeting(@Param("meetingId") Long meetingId);

    // Stats aggregations (#253).

    long countByMentorshipIdAndStatus(Long mentorshipId, MeetingStatus status);

    @Query("SELECT COUNT(m) FROM Meeting m "
            + "WHERE m.mentorship.mentee.id = :menteeId "
            + "AND m.startTime > :now "
            + "AND m.status IN :statuses")
    long countUpcomingByMenteeId(@Param("menteeId") Long menteeId,
                                 @Param("now") OffsetDateTime now,
                                 @Param("statuses") Collection<MeetingStatus> statuses);

    /**
     * Total completed meeting hours for a mentor across all their mentorships.
     * Native query because JPQL has no portable interval-to-seconds conversion;
     * Postgres' {@code EXTRACT(EPOCH FROM interval)} is the cleanest single-row
     * aggregate. Returns 0.0 (not null) for mentors with zero completed meetings.
     */
    @Query(value = "SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (m.end_time - m.start_time)) / 3600.0), 0) "
            + "FROM meetings m JOIN mentorships ms ON m.mentorship_id = ms.id "
            + "WHERE ms.mentor_id = :mentorId AND m.status = 'COMPLETED'",
            nativeQuery = true)
    double sumCompletedMeetingHoursForMentor(@Param("mentorId") Long mentorId);
}

