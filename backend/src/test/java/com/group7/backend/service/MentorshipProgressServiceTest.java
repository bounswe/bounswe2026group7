package com.group7.backend.service;

import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.MilestoneStatus;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.TaskSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipProgressServiceTest {

    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;
    @Mock private MilestoneRepository milestoneRepository;

    private MentorshipProgressService progressService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;

    private static final long MID = 100L;
    private static final long MENTOR_ID = 1L;
    private static final long MENTEE_ID = 2L;

    @BeforeEach
    void setUp() {
        // Real MentorshipService so we exercise the real findForParticipant + ResourceNotFoundException path.
        MentorshipService mentorshipService = new MentorshipService(
                mentorshipRepository, /* mentorshipRequestRepository */ null,
                /* notificationEventPublisher */ null, /* clock */ null);
        progressService = new MentorshipProgressService(
                mentorshipService, taskRepository, taskSubmissionRepository, milestoneRepository);

        mentor = new Mentor(); mentor.setId(MENTOR_ID); mentor.setFirstName("Mentor");
        mentee = new Mentee(); mentee.setId(MENTEE_ID); mentee.setFirstName("Mentee");

        mentorship = new Mentorship();
        mentorship.setId(MID);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
    }

    private void mockFound() {
        when(mentorshipRepository.findById(MID)).thenReturn(Optional.of(mentorship));
    }

    private void mockCounts(long taskTotal, long taskCompleted, long taskSubmitted,
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

    private void mockTimestamps(OffsetDateTime sub, OffsetDateTime rev, OffsetDateTime mile) {
        lenient().when(taskSubmissionRepository.findMaxSubmittedAtForMentorship(MID)).thenReturn(sub);
        lenient().when(taskSubmissionRepository.findMaxReviewedAtForMentorship(MID)).thenReturn(rev);
        lenient().when(milestoneRepository.findMaxCompletedAtForMentorship(MID)).thenReturn(mile);
    }

    // ── Authorization ───────────────────────────────────────────────────────

    @Test
    void getProgress_returnsForMentor() {
        mockFound();
        mockCounts(0, 0, 0, 0, 0);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getProgress_returnsForMentee() {
        mockFound();
        mockCounts(0, 0, 0, 0, 0);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTEE_ID, MID);

        assertThat(r.mentorshipId()).isEqualTo(MID);
    }

    @Test
    void getProgress_404ForNonParticipant() {
        mockFound();

        assertThatThrownBy(() -> progressService.getProgress(999L, MID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProgress_404ForUnknownId() {
        when(mentorshipRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.getProgress(MENTOR_ID, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Percentage formula branches ─────────────────────────────────────────

    @Test
    void percentage_isZeroWhenNoTasksAndNoMilestones() {
        mockFound();
        mockCounts(0, 0, 0, 0, 0);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressPercentage()).isEqualTo(0.0f);
        assertThat(r.lastActivityAt()).isNull();
    }

    @Test
    void percentage_tasksOnlyUsesTaskRatio() {
        mockFound();
        mockCounts(10, 4, 2, 0, 0);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressPercentage()).isEqualTo(0.4f);
        assertThat(r.taskTotal()).isEqualTo(10);
        assertThat(r.taskCompleted()).isEqualTo(4);
        assertThat(r.taskSubmitted()).isEqualTo(2);
    }

    @Test
    void percentage_milestonesOnlyUsesMilestoneRatio() {
        mockFound();
        mockCounts(0, 0, 0, 4, 1);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressPercentage()).isEqualTo(0.25f);
        assertThat(r.milestoneTotal()).isEqualTo(4);
        assertThat(r.milestoneCompleted()).isEqualTo(1);
    }

    @Test
    void percentage_bothPresentUsesEqualWeight() {
        mockFound();
        // task ratio 4/10 = 0.4 ; milestone ratio 1/4 = 0.25 ; weighted = 0.5*0.4 + 0.5*0.25 = 0.325
        mockCounts(10, 4, 2, 4, 1);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.progressPercentage()).isCloseTo(0.325f, within(0.0001f));
    }

    @Test
    void percentage_neverNaNOrInfinityForLargeCounts() {
        mockFound();
        mockCounts(Integer.MAX_VALUE, Integer.MAX_VALUE / 2, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(Float.isNaN(r.progressPercentage())).isFalse();
        assertThat(Float.isInfinite(r.progressPercentage())).isFalse();
        assertThat(r.progressPercentage()).isBetween(0.0f, 1.0f);
    }

    // ── lastActivityAt precedence ───────────────────────────────────────────

    @Test
    void lastActivity_takesSubmittedWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        mockFound();
        mockCounts(1, 0, 1, 0, 0);
        mockTimestamps(ts, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesReviewedWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        mockFound();
        mockCounts(1, 1, 0, 0, 0);
        mockTimestamps(null, ts, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesMilestoneWhenOnlyOne() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 3, 0, 0, 0, 0, ZoneOffset.UTC);
        mockFound();
        mockCounts(0, 0, 0, 1, 1);
        mockTimestamps(null, null, ts);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_takesLatestAcrossAllThree() {
        OffsetDateTime sub = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rev = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC); // latest
        OffsetDateTime mil = OffsetDateTime.of(2026, 5, 3, 0, 0, 0, 0, ZoneOffset.UTC);
        mockFound();
        mockCounts(1, 1, 0, 1, 1);
        mockTimestamps(sub, rev, mil);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(rev);
    }

    @Test
    void lastActivity_milestoneWinsWhenLatest() {
        OffsetDateTime sub = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rev = OffsetDateTime.of(2026, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime mil = OffsetDateTime.of(2026, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC); // latest
        mockFound();
        mockCounts(1, 1, 0, 1, 1);
        mockTimestamps(sub, rev, mil);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isEqualTo(mil);
    }

    @Test
    void lastActivity_handlesEqualTimestamps() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        mockFound();
        mockCounts(1, 1, 0, 1, 1);
        mockTimestamps(ts, ts, ts);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        // Equal timestamps: laterOf returns the earlier one (a) when not after; but result is same instant.
        assertThat(r.lastActivityAt()).isEqualTo(ts);
    }

    @Test
    void lastActivity_isNullWhenAllNull() {
        mockFound();
        mockCounts(0, 0, 0, 0, 0);
        mockTimestamps(null, null, null);

        MentorshipProgressResponse r = progressService.getProgress(MENTOR_ID, MID);

        assertThat(r.lastActivityAt()).isNull();
    }
}
