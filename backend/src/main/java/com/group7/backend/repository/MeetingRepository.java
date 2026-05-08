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

    @Modifying
    @Query("UPDATE Meeting m SET m.status = 'EXPIRED' WHERE m.id = :meetingId AND m.status = 'PENDING_CONFIRMATION'")
    int expireMeeting(@Param("meetingId") Long meetingId);

    @Modifying
    @Query("UPDATE Meeting m SET m.status = 'COMPLETED' WHERE m.id = :meetingId AND m.status = 'CONFIRMED'")
    int completeMeeting(@Param("meetingId") Long meetingId);
}

