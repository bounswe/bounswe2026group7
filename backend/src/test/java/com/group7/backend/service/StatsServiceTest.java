package com.group7.backend.service;

import com.group7.backend.dto.response.MenteeStatsResponse;
import com.group7.backend.dto.response.MentorStatsResponse;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final long MENTOR_ID = 11L;
    private static final long MENTEE_ID = 22L;

    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private MentorshipRequestRepository mentorshipRequestRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private MeetingRepository meetingRepository;

    @InjectMocks
    private StatsService statsService;

    // ── Mentor stats ────────────────────────────────────────────────────────

    @Test
    void mentorStats_mapsEachFieldToTheRightRepoCall() {
        when(mentorshipRepository.countDistinctMenteesByMentorId(MENTOR_ID)).thenReturn(12L);
        when(mentorshipRepository.countByMentorIdAndStatus(MENTOR_ID, MentorshipStatus.ACTIVE))
                .thenReturn(3L);
        when(mentorshipRepository.countByMentorIdAndStatus(MENTOR_ID, MentorshipStatus.COMPLETED))
                .thenReturn(9L);
        when(taskRepository.countByMentorId(MENTOR_ID)).thenReturn(47L);
        when(taskRepository.countByMentorIdAndStatus(MENTOR_ID, TaskStatus.COMPLETED))
                .thenReturn(31L);
        when(meetingRepository.sumCompletedMeetingHoursForMentor(MENTOR_ID)).thenReturn(18.5);
        when(mentorshipRequestRepository.countByMentor_IdAndStatus(
                MENTOR_ID, MentorshipRequestStatus.PENDING))
                .thenReturn(2L);

        MentorStatsResponse r = statsService.getMentorStats(MENTOR_ID);

        assertThat(r.totalMentees()).isEqualTo(12L);
        assertThat(r.activeMentorships()).isEqualTo(3L);
        assertThat(r.completedMentorships()).isEqualTo(9L);
        assertThat(r.totalTasksAssigned()).isEqualTo(47L);
        assertThat(r.totalTasksCompleted()).isEqualTo(31L);
        assertThat(r.totalMeetingHours()).isEqualTo(18.5);
        assertThat(r.pendingRequests()).isEqualTo(2L);
        // Rating not yet wired (#237 stub) — assert the documented contract.
        assertThat(r.averageRating()).isNull();
        assertThat(r.ratingCount()).isEqualTo(0L);
    }

    @Test
    void mentorStats_returnsZerosForUserWithNoMentorActivity() {
        // No stubs: Mockito returns 0 / 0.0 / null for primitives + objects by default.

        MentorStatsResponse r = statsService.getMentorStats(MENTOR_ID);

        assertThat(r.totalMentees()).isZero();
        assertThat(r.activeMentorships()).isZero();
        assertThat(r.completedMentorships()).isZero();
        assertThat(r.totalTasksAssigned()).isZero();
        assertThat(r.totalTasksCompleted()).isZero();
        assertThat(r.totalMeetingHours()).isZero();
        assertThat(r.pendingRequests()).isZero();
    }

    // ── Mentee stats ────────────────────────────────────────────────────────

    @Test
    void menteeStats_mapsEachFieldToTheRightRepoCall() {
        when(mentorshipRepository.countByMenteeIdAndStatus(MENTEE_ID, MentorshipStatus.ACTIVE))
                .thenReturn(1L);
        when(taskRepository.countByMenteeIdAndStatus(MENTEE_ID, TaskStatus.COMPLETED))
                .thenReturn(12L);
        when(taskRepository.countByMenteeIdAndStatusIn(eq(MENTEE_ID), any()))
                .thenReturn(3L);
        when(meetingRepository.countUpcomingByMenteeId(eq(MENTEE_ID), any(), any()))
                .thenReturn(2L);
        when(mentorshipRepository.countDistinctMentorsByMenteeId(MENTEE_ID)).thenReturn(2L);
        when(mentorshipRequestRepository.countByMentee_Id(MENTEE_ID)).thenReturn(5L);

        MenteeStatsResponse r = statsService.getMenteeStats(MENTEE_ID);

        assertThat(r.activeMentorshipsCount()).isEqualTo(1L);
        assertThat(r.completedTasksCount()).isEqualTo(12L);
        assertThat(r.pendingTasksCount()).isEqualTo(3L);
        assertThat(r.upcomingMeetingsCount()).isEqualTo(2L);
        assertThat(r.totalMentorsWorkedWith()).isEqualTo(2L);
        assertThat(r.requestsSent()).isEqualTo(5L);
    }

    @Test
    void menteeStats_passesPendingTaskStatusSetToRepo() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TaskStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);

        when(taskRepository.countByMenteeIdAndStatusIn(eq(MENTEE_ID), statusesCaptor.capture()))
                .thenReturn(0L);
        // Other stubs left default (return 0).
        when(meetingRepository.countUpcomingByMenteeId(eq(MENTEE_ID), any(), any()))
                .thenReturn(0L);

        statsService.getMenteeStats(MENTEE_ID);

        // PENDING + REVISION_REQUESTED are both "still owe work" — see the constant
        // in StatsService and the documented convention in TaskRepository.
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(TaskStatus.PENDING, TaskStatus.REVISION_REQUESTED);
    }

    @Test
    void menteeStats_passesUpcomingMeetingStatusSetAndNowToRepo() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<MeetingStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<OffsetDateTime> nowCaptor =
                ArgumentCaptor.forClass(OffsetDateTime.class);

        OffsetDateTime before = OffsetDateTime.now();
        when(meetingRepository.countUpcomingByMenteeId(
                eq(MENTEE_ID), nowCaptor.capture(), statusesCaptor.capture()))
                .thenReturn(0L);

        statsService.getMenteeStats(MENTEE_ID);

        OffsetDateTime after = OffsetDateTime.now();
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrderElementsOf(
                        EnumSet.of(MeetingStatus.PENDING_CONFIRMATION, MeetingStatus.CONFIRMED));
        // The "now" passed to the repo must be roughly the call site's clock — assert
        // it is in the [before, after] window so a clock-skew bug surfaces here.
        assertThat(nowCaptor.getValue()).isBetween(before, after);
    }
}
