package com.group7.backend.repository;

import com.group7.backend.entity.MeetingReminderState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingReminderStateRepository extends JpaRepository<MeetingReminderState, Long> {

    boolean existsByMeeting_IdAndReminderOffsetMinutes(Long meetingId, int reminderOffsetMinutes);
}
