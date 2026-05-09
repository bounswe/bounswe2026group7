package com.group7.backend.service;

import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.MilestoneStatus;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.TaskSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipProgressServiceTest {

    @Mock private MentorshipService mentorshipService;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;
    @Mock private MilestoneRepository milestoneRepository;

    @InjectMocks
    private MentorshipProgressService progressService;

    private Mentorship mentorship;

    private static final long MID = 100L;
    private static final long MENTOR_ID = 1L;
    private static final long MENTEE_ID = 2L;

    @BeforeEach
    void setUp() {
        Mentor mentor = new Mentor();
        mentor.setId(MENTOR_ID);
        mentor.setFirstName("Mentor");

        Mentee mentee = new Mentee();
        mentee.setId(MENTEE_ID);
        mentee.setFirstName("Mentee");

        mentorship = new Mentorship();
        mentorship.setId(MID);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
    }

    /** Stub MentorshipService.findForParticipant to return our prebuilt mentorship. */
    private void stubFound(long callerId) {
        when(mentorshipService.findForParticipant(callerId, MID)).thenReturn(mentorship);
    }

    private void stubCounts(long taskTotal, long taskCompleted, long taskSubmitted,
                            long milestoneTotal, long milestoneCompleted) {
        lenient().when(taskRepository.countByMentorshipId(MID)).thenReturn(taskTotal);
        lenient().when(taskRepository.countByMentorshipIdAndStatus(MID, TaskStatus.COMPLETED))
                .thenReturn(taskCompleted);
        lenient().when(taskRepository.countByMentorshipIdAndStatus(MID, TaskStatus.SUBMITTED))
                .thenReturn(taskSubmitted);
        lenient().when(milestoneRepository.countByMentorshipId(MID)).thenReturn(milestoneTotal);
        lenient().when(milestoneRepository.countByMentorshipIdAndStatus(MID, MilestoneStatus.COMPLETED))
                .thenReturn(milestoneCompleted);
    }

    private void stubTimestamps(OffsetDateTime sub, OffsetDateTime rev, OffsetDateTime mile) {
        lenient().when(taskSubmissionRepository.findMaxSubmittedAtForMentorship(MID)).thenReturn(sub);
        lenient().when(taskSubmissionRepository.findMaxReviewedAtForMentorship(MID)).thenReturn(rev);
        lenient().when(milestoneRepository.findMaxCompletedAtForMentorship(MID)).thenReturn(mile);
    }

    // ── Authorization (delegated to MentorshipService.findForParticipant) ──

    @Test
    void getProgress_returnsForMentor() {
        stubFound(MENTOR_ID);
        stubCounts(0, 0, 0, 0, 0);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getProgress_returnsForMentee() {
        stubFound(MENTEE_ID);
        stubCounts(0, 0, 0, 0, 0);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTEE_ID, MID);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getProgress_propagatesResourceNotFoundFromFinder() {
        when(mentorshipService.findForParticipant(999L, MID))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        assertThatThrownBy(() -> progressService.getProgress(999L, MID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Ratio formula branches ──────────────────────────────────────────────

    @Test
    void ratio_isZeroWhenNoTasksAndNoMilestones() {
        stubFound(MENTOR_ID);
        stubCounts(0, 0, 0, 0, 0);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressRatio()).isEqualTo(0.0f);
        assertThat(r.lastActivityAt()).isNull();
    }

    @Test
    void ratio_tasksOnlyUsesTaskRatio() {
        stubFound(MENTOR_ID);
        stubCounts(10, 4, 2, 0, 0);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressRatio()).isEqualTo(0.4f);
        assertThat(r.taskTotal()).isEqualTo(10);
        assertThat(r.taskCompleted()).isEqualTo(4);
        assertThat(r.taskSubmitted()).isEqualTo(2);
    }

    @Test
    void ratio_milestonesOnlyUsesMilestoneRatio() {
        stubFound(MENTOR_ID);
        stubCounts(0, 0, 0, 4, 1);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressRatio()).isEqualTo(0.25f);
        assertThat(r.milestoneTotal()).isEqualTo(4);
        assertThat(r.milestoneCompleted()).isEqualTo(1);
    }

    @Test
    void ratio_bothPresentUsesEqualWeight() {
        stubFound(MENTOR_ID);
        // task ratio 4/10 = 0.4 ; milestone ratio 1/4 = 0.25 ; weighted = 0.5*0.4 + 0.5*0.25 = 0.325
        stubCounts(10, 4, 2, 4, 1);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressRatio()).isCloseTo(0.325f, within(0.0001f));
    }

    @Test
    void ratio_neverNaNOrInfinityForLargeCounts() {
        stubFound(MENTOR_ID);
        stubCounts(Integer.MAX_VALUE, Integer.MAX_VALUE / 2, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(Float.isNaN(r.progressRatio())).isFalse();
        assertThat(Float.isInfinite(r.progressRatio())).isFalse();
        assertThat(r.progressRatio()).isBetween(0.0f, 1.0f);
    }

    // ── lastActivityAt precedence ───────────────────────────────────────────

    @Test
    void lastActivity_takesSubmittedWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        stubFound(MENTOR_ID);
        stubCounts(1, 0, 1, 0, 0);
        stubTimestamps(ts, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesReviewedWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        stubFound(MENTOR_ID);
        stubCounts(1, 1, 0, 0, 0);
        stubTimestamps(null, ts, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesMilestoneWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 3, 0, 0, 0, 0, ZoneOffset.UTC);
        stubFound(MENTOR_ID);
        stubCounts(0, 0, 0, 1, 1);
        stubTimestamps(null, null, ts);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesLatestAcrossAllThree() {
        OffsetDateTime sub = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rev = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC); // latest
        OffsetDateTime mil = OffsetDateTime.of(2026, 5, 3, 0, 0, 0, 0, ZoneOffset.UTC);
        stubFound(MENTOR_ID);
        stubCounts(1, 1, 0, 1, 1);
        stubTimestamps(sub, rev, mil);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(rev);
    }

    @Test
    void lastActivity_milestoneWinsWhenLatest() {
        OffsetDateTime sub = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rev = OffsetDateTime.of(2026, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime mil = OffsetDateTime.of(2026, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC); // latest
        stubFound(MENTOR_ID);
        stubCounts(1, 1, 0, 1, 1);
        stubTimestamps(sub, rev, mil);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(mil);
    }

    @Test
    void lastActivity_handlesEqualTimestamps() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        stubFound(MENTOR_ID);
        stubCounts(1, 1, 0, 1, 1);
        stubTimestamps(ts, ts, ts);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        // All three sources tied — any branch returns the same instant.
        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_isNullWhenAllNull() {
        stubFound(MENTOR_ID);
        stubCounts(0, 0, 0, 0, 0);
        stubTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isNull();
    }
}
