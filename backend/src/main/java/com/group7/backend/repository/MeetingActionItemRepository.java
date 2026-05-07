package com.group7.backend.repository;

import com.group7.backend.entity.MeetingActionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItem, Long> {

    List<MeetingActionItem> findByMeetingIdOrderByOrderIndexAscIdAsc(Long meetingId);
}
