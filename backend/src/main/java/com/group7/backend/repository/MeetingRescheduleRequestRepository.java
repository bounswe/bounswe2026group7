package com.group7.backend.repository;

import com.group7.backend.entity.MeetingRescheduleRequest;
import com.group7.backend.entity.MeetingRescheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MeetingRescheduleRequestRepository extends JpaRepository<MeetingRescheduleRequest, Long> {

    Optional<MeetingRescheduleRequest> findByMeetingIdAndStatus(Long meetingId, MeetingRescheduleStatus status);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END "
            + "FROM MeetingRescheduleRequest r "
            + "JOIN r.meeting m JOIN m.mentorship ms "
            + "WHERE r.status = :status "
            + "AND (ms.mentor.id = :mentorId OR ms.mentee.id = :menteeId) "
            + "AND r.proposedStart < :endAt AND r.proposedEnd > :startAt")
    boolean existsOverlappingPendingForParticipants(
            @Param("mentorId") Long mentorId,
            @Param("menteeId") Long menteeId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("status") MeetingRescheduleStatus status);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END "
            + "FROM MeetingRescheduleRequest r "
            + "JOIN r.meeting m JOIN m.mentorship ms "
            + "WHERE r.status = :status "
            + "AND m.id <> :meetingId "
            + "AND (ms.mentor.id = :mentorId OR ms.mentee.id = :menteeId) "
            + "AND r.proposedStart < :endAt AND r.proposedEnd > :startAt")
    boolean existsOverlappingPendingForParticipantsExcludingMeeting(
            @Param("meetingId") Long meetingId,
            @Param("mentorId") Long mentorId,
            @Param("menteeId") Long menteeId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("status") MeetingRescheduleStatus status);
}
