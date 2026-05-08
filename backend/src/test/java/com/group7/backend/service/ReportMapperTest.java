package com.group7.backend.service;

import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ReportMapper} (#135). Two concerns:
 * <ul>
 *   <li>{@link #toResponses_isN1Free_acrossPolymorphicTargets} pins the
 *       batched path's query budget — for any page of any target-type
 *       mix, the mapper makes at most three batch queries (one user,
 *       one mentorship, one post), not three per row.</li>
 *   <li>The remaining tests cover fallback strings for missing targets
 *       and the slim non-admin shape (no target summary, no extra
 *       lookups for target rows).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReportMapperTest {

    @Mock private UserRepository userRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private FeedPostRepository feedPostRepository;
    @InjectMocks private ReportMapper mapper;

    @Test
    void toResponses_isN1Free_acrossPolymorphicTargets() {
        // Page of 4 reports: 2 USER targets, 1 MENTORSHIP, 1 POST.
        Report r1 = buildReport(1L, 10L, ReportTargetType.USER, 100L, null, null);
        Report r2 = buildReport(2L, 11L, ReportTargetType.USER, 101L, 999L, ReportStatus.RESOLVED);
        Report r3 = buildReport(3L, 12L, ReportTargetType.MENTORSHIP, 200L, null, null);
        Report r4 = buildReport(4L, 13L, ReportTargetType.POST, 300L, null, null);
        Page<Report> page = new PageImpl<>(List.of(r1, r2, r3, r4),
                PageRequest.of(0, 20), 4);

        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                user(10L, "Alice", "A"), user(11L, "Bob", "B"),
                user(12L, "Cara", "C"), user(13L, "Dan", "D"),
                user(100L, "TargetA", "X"), user(101L, "TargetB", "Y"),
                user(999L, "Reviewer", "R")));
        Mentor mentor = mentorWithFirstName(500L, "MentorMike");
        Mentee mentee = menteeWithFirstName(501L, "MenteeMia");
        when(mentorshipRepository.findAllById(anyCollection())).thenReturn(List.of(
                mentorship(200L, mentor, mentee)));
        when(feedPostRepository.findAllByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of(feedPost(300L, "Some post body content")));

        Page<ReportResponse> mapped = mapper.toResponses(page, true);

        // Exactly one batch per repository, regardless of page size. Per-row
        // findById is the anti-pattern this test guards against.
        verify(userRepository, times(1)).findAllById(anyCollection());
        verify(mentorshipRepository, times(1)).findAllById(anyCollection());
        verify(feedPostRepository, times(1)).findAllByIdInAndDeletedAtIsNull(anyCollection());
        verify(userRepository, never()).findById(any());
        verify(mentorshipRepository, never()).findById(any());
        verify(feedPostRepository, never()).findByIdAndDeletedAtIsNull(any());

        assertThat(mapped.getTotalElements()).isEqualTo(4);
        ReportResponse mappedR1 = mapped.getContent().get(0);
        assertThat(mappedR1.reporterFirstName()).isEqualTo("Alice");
        assertThat(mappedR1.targetSummary()).isEqualTo("TargetA X");
        ReportResponse mappedR2 = mapped.getContent().get(1);
        assertThat(mappedR2.reviewedByFirstName()).isEqualTo("Reviewer");
        ReportResponse mappedR3 = mapped.getContent().get(2);
        assertThat(mappedR3.targetSummary())
                .isEqualTo("Mentor: MentorMike / Mentee: MenteeMia");
        ReportResponse mappedR4 = mapped.getContent().get(3);
        assertThat(mappedR4.targetSummary()).isEqualTo("Some post body content");
    }

    @Test
    void toResponses_skipsMentorshipAndPostBatches_whenSummaryNotIncluded() {
        Report r = buildReport(1L, 10L, ReportTargetType.MENTORSHIP, 200L, null, null);
        Page<Report> page = new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1);

        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(user(10L, "Alice", "A")));

        Page<ReportResponse> mapped = mapper.toResponses(page, false);

        verify(userRepository, times(1)).findAllById(anyCollection());
        verify(mentorshipRepository, never()).findAllById(anyCollection());
        verify(feedPostRepository, never()).findAllByIdInAndDeletedAtIsNull(anyCollection());

        assertThat(mapped.getContent().get(0).targetSummary()).isNull();
        assertThat(mapped.getContent().get(0).reporterFirstName()).isEqualTo("Alice");
    }

    @Test
    void toResponses_handlesEmptyPage_withoutAnyRepositoryCalls() {
        Page<Report> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        Page<ReportResponse> mapped = mapper.toResponses(empty, true);

        verify(userRepository, never()).findAllById(anyCollection());
        verify(mentorshipRepository, never()).findAllById(anyCollection());
        verify(feedPostRepository, never()).findAllByIdInAndDeletedAtIsNull(anyCollection());
        assertThat(mapped.getTotalElements()).isZero();
    }

    @Test
    void toResponses_returnsFallbackStrings_forMissingTargets() {
        Report rUser = buildReport(1L, 10L, ReportTargetType.USER, 100L, null, null);
        Report rMentorship = buildReport(2L, 11L, ReportTargetType.MENTORSHIP, 200L, null, null);
        Report rPost = buildReport(3L, 12L, ReportTargetType.POST, 300L, null, null);
        Page<Report> page = new PageImpl<>(List.of(rUser, rMentorship, rPost),
                PageRequest.of(0, 20), 3);

        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                user(10L, "Alice", "A"), user(11L, "Bob", "B"), user(12L, "Cara", "C")
                // user 100 deliberately missing
        ));
        when(mentorshipRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(feedPostRepository.findAllByIdInAndDeletedAtIsNull(anyCollection()))
                .thenReturn(List.of());

        Page<ReportResponse> mapped = mapper.toResponses(page, true);

        assertThat(mapped.getContent().get(0).targetSummary()).isEqualTo("[user deleted]");
        assertThat(mapped.getContent().get(1).targetSummary()).isEqualTo("[mentorship deleted]");
        assertThat(mapped.getContent().get(2).targetSummary())
                .isEqualTo("[post deleted or unavailable]");
    }

    // ── single-row path: detail endpoint ────────────────────────────────────

    @Test
    void toResponseSingle_includesTargetSummary_forUserTarget() {
        Report r = buildReport(1L, 10L, ReportTargetType.USER, 100L, null, null);
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(10L, "Alice", "A")));
        when(userRepository.findById(100L))
                .thenReturn(Optional.of(user(100L, "TargetA", "X")));

        ReportResponse out = mapper.toResponse(r, true);

        assertThat(out.reporterFirstName()).isEqualTo("Alice");
        assertThat(out.targetSummary()).isEqualTo("TargetA X");
    }

    @Test
    void toResponseSingle_includesTargetSummary_forMentorshipTarget() {
        Report r = buildReport(1L, 10L, ReportTargetType.MENTORSHIP, 200L, null, null);
        Mentor mentor = mentorWithFirstName(500L, "MentorMike");
        Mentee mentee = menteeWithFirstName(501L, "MenteeMia");
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(10L, "Alice", "A")));
        when(mentorshipRepository.findById(200L))
                .thenReturn(Optional.of(mentorship(200L, mentor, mentee)));

        ReportResponse out = mapper.toResponse(r, true);

        assertThat(out.targetSummary()).isEqualTo("Mentor: MentorMike / Mentee: MenteeMia");
    }

    @Test
    void toResponseSingle_includesTargetSummary_forPostTarget_truncatesOverLimitBody() {
        // 150-char body exceeds the 120-char excerpt limit; mapper must
        // truncate and append the ellipsis. Closes the truncation branch
        // in ReportMapper.excerpt.
        Report r = buildReport(1L, 10L, ReportTargetType.POST, 300L, null, null);
        String longBody = "x".repeat(150);
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(10L, "Alice", "A")));
        when(feedPostRepository.findByIdAndDeletedAtIsNull(300L))
                .thenReturn(Optional.of(feedPost(300L, longBody)));

        ReportResponse out = mapper.toResponse(r, true);

        assertThat(out.targetSummary())
                .hasSize(121)   // 120 chars + the U+2026 ellipsis
                .endsWith("…");
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static Report buildReport(Long id, Long reporterId,
                                      ReportTargetType type, Long targetId,
                                      Long reviewedById, ReportStatus status) {
        Report r = new Report();
        r.setId(id);
        r.setReporterId(reporterId);
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setProblemType(ProblemType.HARASSMENT);
        r.setDescription("desc");
        r.setStatus(status == null ? ReportStatus.OPEN : status);
        r.setCreatedAt(OffsetDateTime.now());
        if (reviewedById != null) {
            r.setReviewedById(reviewedById);
            r.setReviewedAt(OffsetDateTime.now());
        }
        return r;
    }

    private static User user(Long id, String first, String last) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName(first);
        m.setLastName(last);
        m.setEmail(first + "@test.com");
        return m;
    }

    private static Mentor mentorWithFirstName(Long id, String first) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(first);
        m.setLastName("L");
        m.setEmail(first + "@test.com");
        return m;
    }

    private static Mentee menteeWithFirstName(Long id, String first) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName(first);
        m.setLastName("L");
        m.setEmail(first + "@test.com");
        return m;
    }

    private static Mentorship mentorship(Long id, Mentor mentor, Mentee mentee) {
        Mentorship m = new Mentorship();
        m.setId(id);
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setStatus(MentorshipStatus.ACTIVE);
        return m;
    }

    private static FeedPost feedPost(Long id, String body) {
        FeedPost p = new FeedPost();
        p.setId(id);
        p.setBody(body);
        return p;
    }

    @SuppressWarnings("unused")
    private static Collection<Long> idsOf(Long... ids) {
        return List.of(ids);
    }
}
