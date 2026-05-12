package com.group7.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.group7.backend.dto.request.CreateReportRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.event.ReportSubmittedEvent;
import com.group7.backend.exception.DuplicateReportException;
import com.group7.backend.exception.InvalidReportTransitionException;
import com.group7.backend.exception.ReportNotPermittedException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SelfReportException;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.ReportRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link ReportService} (#135). Mocks every repo +
 * the notification publisher so each test exercises one branch of
 * control flow.
 *
 * <p>Two log-discipline tests use a Logback list-appender to assert the
 * audit log lines exclude the {@code description} content (OWASP A09 —
 * sensitive content must not appear in logs).
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private FeedPostRepository feedPostRepository;
    @Mock private ReportMapper reportMapper;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks private ReportService reportService;

    private static final long REPORTER_ID = 1L;
    private static final long TARGET_USER_ID = 2L;
    private static final long ADMIN_A_ID = 100L;
    private static final String SECRET_DESCRIPTION =
            "PII content: phone +1-555-0100, email victim@example.com";

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReportService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReportService.class);
        logger.detachAppender(logAppender);
    }

    // ── createReport: happy path ────────────────────────────────────────────

    @Test
    void createReport_persistsReport_andPublishesSingleSubmittedEvent() {
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });
        when(userRepository.findById(REPORTER_ID))
                .thenReturn(Optional.of(menteeWithFirstName(REPORTER_ID, "Ada")));
        when(reportMapper.toResponse(any(Report.class), anyBoolean()))
                .thenReturn(stubResponse(500L));

        reportService.createReport(REPORTER_ID, request(ReportTargetType.USER, TARGET_USER_ID));

        verify(reportRepository).save(any(Report.class));
        ArgumentCaptor<ReportSubmittedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReportSubmittedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        ReportSubmittedEvent ev = eventCaptor.getValue();
        assertThat(ev.reportId()).isEqualTo(500L);
        assertThat(ev.reporterId()).isEqualTo(REPORTER_ID);
        assertThat(ev.reporterFirstName()).isEqualTo("Ada");
        assertThat(ev.targetType()).isEqualTo(ReportTargetType.USER);
    }

    @Test
    void createReport_carriesNullReporterName_whenReporterRowAlreadyGone() {
        // Edge case: reporter passed JWT validation but the row is gone
        // (e.g., admin force-deletion mid-request). Service still publishes
        // the event with null name; listener falls back to "Someone".
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(501L);
            return r;
        });
        when(userRepository.findById(REPORTER_ID)).thenReturn(Optional.empty());
        when(reportMapper.toResponse(any(Report.class), anyBoolean()))
                .thenReturn(stubResponse(501L));

        reportService.createReport(REPORTER_ID, request(ReportTargetType.USER, TARGET_USER_ID));

        ArgumentCaptor<ReportSubmittedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReportSubmittedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reporterFirstName()).isNull();
    }

    // ── createReport: validation paths ──────────────────────────────────────

    @Test
    void createReport_rejectsSelfReport_onUserTarget() {
        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.USER, REPORTER_ID)))
                .isInstanceOf(SelfReportException.class)
                .hasMessageContaining("themselves");

        verify(reportRepository, never()).save(any(Report.class));
        verify(applicationEventPublisher, never()).publishEvent(any(ReportSubmittedEvent.class));
    }

    @Test
    void createReport_rejectsMissingUserTarget_with404() {
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(false);

        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.USER, TARGET_USER_ID)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void createReport_rejectsMissingMentorshipTarget_with404() {
        when(mentorshipRepository.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.MENTORSHIP, 50L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createReport_rejectsNonParticipantMentorshipReport_with400() {
        Mentor mentor = mentorWithId(11L);
        Mentee mentee = menteeWithFirstName(12L, "Other");
        Mentorship m = mentorshipBetween(mentor, mentee);
        when(mentorshipRepository.findById(50L)).thenReturn(Optional.of(m));

        // REPORTER_ID = 1 is neither the mentor (11) nor the mentee (12).
        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.MENTORSHIP, 50L)))
                .isInstanceOf(ReportNotPermittedException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void createReport_acceptsMentorshipReport_whenReporterIsTheMentee() {
        // The OR check in validateTargetExists short-circuits on the
        // mentor branch when reporter == mentor (covered by the
        // …whenReporterIsParticipant test). This test exercises the
        // other side: reporter is the mentee, so the mentor branch
        // returns false and the mentee branch is the one that admits.
        Mentor mentor = mentorWithId(99L);
        Mentee mentee = menteeWithFirstName(REPORTER_ID, "Reporter");
        Mentorship m = mentorshipBetween(mentor, mentee);
        when(mentorshipRepository.findById(50L)).thenReturn(Optional.of(m));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(800L);
            return r;
        });
        when(reportMapper.toResponse(any(Report.class), anyBoolean()))
                .thenReturn(stubResponse(800L));

        ReportResponse out = reportService.createReport(REPORTER_ID,
                request(ReportTargetType.MENTORSHIP, 50L));

        assertThat(out).isNotNull();
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void createReport_acceptsMentorshipReport_whenReporterIsParticipant() {
        Mentor mentor = mentorWithId(REPORTER_ID);
        Mentee mentee = menteeWithFirstName(99L, "Bob");
        Mentorship m = mentorshipBetween(mentor, mentee);
        when(mentorshipRepository.findById(50L)).thenReturn(Optional.of(m));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(700L);
            return r;
        });
        when(reportMapper.toResponse(any(Report.class), anyBoolean()))
                .thenReturn(stubResponse(700L));

        ReportResponse out = reportService.createReport(REPORTER_ID,
                request(ReportTargetType.MENTORSHIP, 50L));

        assertThat(out).isNotNull();
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void createReport_rejectsMissingPostTarget_with404() {
        when(feedPostRepository.findByIdAndDeletedAtIsNull(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.POST, 999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createReport: duplicate caught from partial unique index ────────────

    @Test
    void createReport_translatesDataIntegrityViolation_to409DuplicateReportException() {
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);
        when(reportRepository.save(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"idx_reports_active_unique\""));

        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.USER, TARGET_USER_ID)))
                .isInstanceOf(DuplicateReportException.class)
                .hasMessageContaining("already exists");

        verify(applicationEventPublisher, never()).publishEvent(any(ReportSubmittedEvent.class));
    }

    @Test
    void createReport_rethrowsNonIndexIntegrityViolation_unchanged() {
        // Foreign-key violation on a deleted reporter (or any DIV that is
        // NOT the partial-unique-index hit) must not be misclassified as
        // "duplicate report" — clients would otherwise loop on a 409.
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException(
                "insert or update on table \"reports\" violates foreign key constraint \"reports_reporter_id_fkey\"");
        when(reportRepository.save(any(Report.class))).thenThrow(fkViolation);

        assertThatThrownBy(() ->
                reportService.createReport(REPORTER_ID,
                        request(ReportTargetType.USER, TARGET_USER_ID)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateReportException.class);

        verify(applicationEventPublisher, never()).publishEvent(any(ReportSubmittedEvent.class));
    }

    // ── State machine: every allowed transition + every rejected one ────────

    @Test
    void updateStatus_allowsOpenToUnderReview() { assertAllowed(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW); }

    @Test
    void updateStatus_allowsOpenToResolved() { assertAllowed(ReportStatus.OPEN, ReportStatus.RESOLVED); }

    @Test
    void updateStatus_allowsOpenToDismissed() { assertAllowed(ReportStatus.OPEN, ReportStatus.DISMISSED); }

    @Test
    void updateStatus_allowsUnderReviewToResolved() { assertAllowed(ReportStatus.UNDER_REVIEW, ReportStatus.RESOLVED); }

    @Test
    void updateStatus_allowsUnderReviewToDismissed() { assertAllowed(ReportStatus.UNDER_REVIEW, ReportStatus.DISMISSED); }

    @Test
    void updateStatus_rejectsResolvedToAnything() {
        assertRejected(ReportStatus.RESOLVED, ReportStatus.OPEN);
        assertRejected(ReportStatus.RESOLVED, ReportStatus.UNDER_REVIEW);
        assertRejected(ReportStatus.RESOLVED, ReportStatus.DISMISSED);
    }

    @Test
    void updateStatus_rejectsDismissedToAnything() {
        assertRejected(ReportStatus.DISMISSED, ReportStatus.OPEN);
        assertRejected(ReportStatus.DISMISSED, ReportStatus.UNDER_REVIEW);
        assertRejected(ReportStatus.DISMISSED, ReportStatus.RESOLVED);
    }

    @Test
    void updateStatus_rejectsUnderReviewToOpen() {
        assertRejected(ReportStatus.UNDER_REVIEW, ReportStatus.OPEN);
    }

    @Test
    void updateStatus_setsReviewedAtAndReviewedById_onTransitionOutOfOpen() {
        Report r = new Report();
        r.setId(7L);
        r.setStatus(ReportStatus.OPEN);
        when(reportRepository.findById(7L)).thenReturn(Optional.of(r));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponse(any(Report.class), anyBoolean())).thenReturn(stubResponse(7L));

        reportService.updateStatus(7L, ADMIN_A_ID, ReportStatus.UNDER_REVIEW);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.UNDER_REVIEW);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getReviewedById()).isEqualTo(ADMIN_A_ID);
    }

    @Test
    void updateStatus_throws404_whenReportMissing() {
        when(reportRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportService.updateStatus(404L, ADMIN_A_ID, ReportStatus.RESOLVED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── PII discipline: description must NEVER appear in logs ───────────────

    @Test
    void createReport_logLine_doesNotIncludeDescription() {
        when(userRepository.existsById(TARGET_USER_ID)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(900L);
            return r;
        });
        when(reportMapper.toResponse(any(Report.class), anyBoolean())).thenReturn(stubResponse(900L));

        reportService.createReport(REPORTER_ID,
                new CreateReportRequest(ReportTargetType.USER, TARGET_USER_ID,
                        ProblemType.HARASSMENT, SECRET_DESCRIPTION));

        for (ILoggingEvent ev : logAppender.list) {
            assertThat(ev.getFormattedMessage())
                    .as("Audit log must not contain the description content")
                    .doesNotContain(SECRET_DESCRIPTION);
        }
    }

    @Test
    void updateStatus_logLine_doesNotIncludeDescription() {
        Report r = new Report();
        r.setId(8L);
        r.setStatus(ReportStatus.OPEN);
        r.setDescription(SECRET_DESCRIPTION);
        when(reportRepository.findById(8L)).thenReturn(Optional.of(r));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponse(any(Report.class), anyBoolean())).thenReturn(stubResponse(8L));

        reportService.updateStatus(8L, ADMIN_A_ID, ReportStatus.RESOLVED);

        for (ILoggingEvent ev : logAppender.list) {
            assertThat(ev.getFormattedMessage())
                    .as("Transition log must not contain the description content")
                    .doesNotContain(SECRET_DESCRIPTION);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void assertAllowed(ReportStatus from, ReportStatus to) {
        Report r = new Report();
        r.setId(1L);
        r.setStatus(from);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponse(any(Report.class), anyBoolean())).thenReturn(stubResponse(1L));

        reportService.updateStatus(1L, ADMIN_A_ID, to);   // must not throw

        verify(reportRepository, times(1)).save(any(Report.class));
    }

    private void assertRejected(ReportStatus from, ReportStatus to) {
        Report r = new Report();
        r.setId(1L);
        r.setStatus(from);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reportService.updateStatus(1L, ADMIN_A_ID, to))
                .isInstanceOf(InvalidReportTransitionException.class)
                .hasMessageContaining("transition");

        verify(reportRepository, never()).save(any(Report.class));
    }

    private static CreateReportRequest request(ReportTargetType type, Long targetId) {
        return new CreateReportRequest(type, targetId, ProblemType.HARASSMENT, "ok");
    }

    private static Mentee menteeWithFirstName(Long id, String firstName) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(firstName + "@test.com");
        return m;
    }

    private static Mentor mentorWithId(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("Mentor");
        m.setLastName("L");
        m.setEmail("mentor" + id + "@test.com");
        return m;
    }

    private static Mentorship mentorshipBetween(Mentor mentor, Mentee mentee) {
        Mentorship m = new Mentorship();
        m.setId(50L);
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setStatus(MentorshipStatus.ACTIVE);
        return m;
    }

    private static ReportResponse stubResponse(Long id) {
        return new ReportResponse(id, REPORTER_ID, "Ada",
                ReportTargetType.USER, TARGET_USER_ID, null,
                ProblemType.HARASSMENT, "ok", ReportStatus.OPEN,
                OffsetDateTime.now(), null, null, null);
    }

}
