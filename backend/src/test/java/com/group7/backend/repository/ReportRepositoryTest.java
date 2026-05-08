package com.group7.backend.repository;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for the {@link Report} entity, the
 * {@link ReportRepository} finders, and the V27 migration's schema
 * invariants — partial unique index, CHECK constraints, FK cascade
 * directions, and the polymorphic-no-FK target_id design.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportRepositoryTest {

    @Autowired private ReportRepository reportRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        // Children first — reports references users, so reports must
        // go before user-row deletion would cascade them anyway. Explicit
        // ordering is more debuggable than relying on cascade.
        jdbcTemplate.update("DELETE FROM reports");
        userRepository.deleteAll();
    }

    // ── Persist / retrieve ───────────────────────────────────────────────────

    @Test
    void persistAndRetrieve_roundTripsAllColumns() {
        Mentee reporter = saveMentee("rr_persist_a@test.com");
        Mentee target = saveMentee("rr_persist_b@test.com");

        Report saved = reportRepository.save(newReport(reporter, ReportTargetType.USER,
                target.getId(), ProblemType.HARASSMENT, "Sent abusive messages"));

        assertThat(saved.getId()).isNotNull();
        Report reloaded = reportRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReporterId()).isEqualTo(reporter.getId());
        assertThat(reloaded.getTargetType()).isEqualTo(ReportTargetType.USER);
        assertThat(reloaded.getTargetId()).isEqualTo(target.getId());
        assertThat(reloaded.getProblemType()).isEqualTo(ProblemType.HARASSMENT);
        assertThat(reloaded.getDescription()).isEqualTo("Sent abusive messages");
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getReviewedAt()).isNull();
        assertThat(reloaded.getReviewedById()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(0L);
    }

    // ── Repository finders ───────────────────────────────────────────────────

    @Test
    void findByReporterIdOrderByCreatedAtDesc_filtersToTheReporterOnly() {
        Mentee a = saveMentee("rr_my_a@test.com");
        Mentee b = saveMentee("rr_my_b@test.com");
        Mentee c = saveMentee("rr_my_c@test.com");
        reportRepository.save(newReport(a, ReportTargetType.USER, b.getId(),
                ProblemType.SPAM, "from a #1"));
        reportRepository.save(newReport(a, ReportTargetType.USER, c.getId(),
                ProblemType.SPAM, "from a #2"));
        reportRepository.save(newReport(b, ReportTargetType.USER, c.getId(),
                ProblemType.SPAM, "from b"));

        Page<Report> page = reportRepository.findByReporterIdOrderByCreatedAtDesc(
                a.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getContent()).allMatch(r -> r.getReporterId().equals(a.getId()));
    }

    @Test
    void findForAdminQueue_returnsAll_whenNoFiltersApplied() {
        Mentee a = saveMentee("rr_q_a@test.com");
        Mentee b = saveMentee("rr_q_b@test.com");
        reportRepository.save(newReport(a, ReportTargetType.USER, b.getId(),
                ProblemType.SPAM, "x"));

        Page<Report> page = reportRepository.findForAdminQueue(
                null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void findForAdminQueue_filtersByStatus() {
        Mentee a = saveMentee("rr_qs_a@test.com");
        Mentee b = saveMentee("rr_qs_b@test.com");
        Report r = reportRepository.save(newReport(a, ReportTargetType.USER, b.getId(),
                ProblemType.SPAM, "open"));
        Report resolved = newReport(a, ReportTargetType.USER, b.getId(),
                ProblemType.SPAM, "resolved");
        resolved.setStatus(ReportStatus.RESOLVED);
        resolved.setReviewedAt(OffsetDateTime.now());
        resolved.setReviewedById(a.getId()); // reuse a as a stand-in admin id
        // Detach the OPEN row from the active partial index so we can
        // submit a second report with the same target. Easier to insert
        // the resolved one directly via JdbcTemplate.
        jdbcTemplate.update(
                "INSERT INTO reports (reporter_id, target_type, target_id, problem_type, " +
                        "description, status, created_at, reviewed_at, reviewed_by_id, version) " +
                        "VALUES (?, 'USER', ?, 'SPAM', 'resolved', 'RESOLVED', NOW(), NOW(), ?, 0)",
                a.getId(), b.getId(), a.getId());

        Page<Report> openOnly = reportRepository.findForAdminQueue(
                ReportStatus.OPEN, null, PageRequest.of(0, 20));
        Page<Report> resolvedOnly = reportRepository.findForAdminQueue(
                ReportStatus.RESOLVED, null, PageRequest.of(0, 20));

        assertThat(openOnly.getTotalElements()).isEqualTo(1L);
        assertThat(openOnly.getContent().get(0).getId()).isEqualTo(r.getId());
        assertThat(resolvedOnly.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void findForAdminQueue_filtersByTargetType() {
        Mentee a = saveMentee("rr_qt_a@test.com");
        Mentee b = saveMentee("rr_qt_b@test.com");
        reportRepository.save(newReport(a, ReportTargetType.USER, b.getId(),
                ProblemType.SPAM, "user-target"));
        reportRepository.save(newReport(a, ReportTargetType.POST, 999L,
                ProblemType.SPAM, "post-target"));

        Page<Report> userTargets = reportRepository.findForAdminQueue(
                null, ReportTargetType.USER, PageRequest.of(0, 20));
        Page<Report> postTargets = reportRepository.findForAdminQueue(
                null, ReportTargetType.POST, PageRequest.of(0, 20));

        assertThat(userTargets.getTotalElements()).isEqualTo(1L);
        assertThat(userTargets.getContent().get(0).getTargetType())
                .isEqualTo(ReportTargetType.USER);
        assertThat(postTargets.getTotalElements()).isEqualTo(1L);
        assertThat(postTargets.getContent().get(0).getTargetType())
                .isEqualTo(ReportTargetType.POST);
    }

    // ── Partial unique index for active-report dedup ─────────────────────────

    @Test
    void partialUniqueIndex_blocksDuplicateActiveReportFromSameReporter() {
        Mentee reporter = saveMentee("rr_dup_a@test.com");
        Mentee target = saveMentee("rr_dup_b@test.com");
        reportRepository.save(newReport(reporter, ReportTargetType.USER, target.getId(),
                ProblemType.SPAM, "first"));

        Report dup = newReport(reporter, ReportTargetType.USER, target.getId(),
                ProblemType.HARASSMENT, "second");

        assertThatThrownBy(() -> {
            reportRepository.save(dup);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partialUniqueIndex_allowsFreshReportAfterPriorReportIsResolved() {
        Mentee reporter = saveMentee("rr_after_a@test.com");
        Mentee target = saveMentee("rr_after_b@test.com");
        Report first = reportRepository.save(newReport(reporter, ReportTargetType.USER,
                target.getId(), ProblemType.SPAM, "first"));
        // Move the first report out of the active partial index.
        jdbcTemplate.update(
                "UPDATE reports SET status='RESOLVED', reviewed_at=NOW(), reviewed_by_id=? WHERE id=?",
                reporter.getId(), first.getId());
        entityManager.clear();

        Report second = reportRepository.save(newReport(reporter, ReportTargetType.USER,
                target.getId(), ProblemType.HARASSMENT, "second"));

        assertThat(second.getId()).isNotNull();
    }

    // ── DB-level CHECK constraints (defence-in-depth) ────────────────────────

    @Test
    void noSelfReportCheck_rejectsUserReportingThemselves() {
        Mentee me = saveMentee("rr_self@test.com");

        assertThatThrownBy(() -> {
            // Bypass the service guard by using JdbcTemplate so the DB
            // CHECK is the layer under test.
            jdbcTemplate.update(
                    "INSERT INTO reports (reporter_id, target_type, target_id, problem_type, " +
                            "description, status, created_at, version) " +
                            "VALUES (?, 'USER', ?, 'OTHER', 'self', 'OPEN', NOW(), 0)",
                    me.getId(), me.getId());
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void descriptionLengthCheck_rejectsOversizeDescription() {
        Mentee a = saveMentee("rr_long_a@test.com");
        Mentee b = saveMentee("rr_long_b@test.com");
        String tooLong = "x".repeat(1001);

        Report r = newReport(a, ReportTargetType.USER, b.getId(), ProblemType.SPAM, tooLong);

        assertThatThrownBy(() -> {
            reportRepository.save(r);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reviewConsistencyCheck_rejectsOpenWithReviewer() {
        Mentee reporter = saveMentee("rr_ck_a@test.com");
        Mentee target = saveMentee("rr_ck_b@test.com");

        assertThatThrownBy(() -> {
            // OPEN with a reviewer set — violates the consistency CHECK.
            jdbcTemplate.update(
                    "INSERT INTO reports (reporter_id, target_type, target_id, problem_type, " +
                            "description, status, created_at, reviewed_at, reviewed_by_id, version) " +
                            "VALUES (?, 'USER', ?, 'SPAM', 'bad', 'OPEN', NOW(), NOW(), ?, 0)",
                    reporter.getId(), target.getId(), reporter.getId());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Cascade behaviour ────────────────────────────────────────────────────

    @Test
    void cascadeOnReporterDelete_dropsTheReport() {
        Mentee reporter = saveMentee("rr_cascade_a@test.com");
        Mentee target = saveMentee("rr_cascade_b@test.com");
        Report r = reportRepository.save(newReport(reporter, ReportTargetType.USER,
                target.getId(), ProblemType.SPAM, "x"));
        Long reporterId = reporter.getId();
        Long reportId = r.getId();

        menteeRepository.delete(reporter);
        entityManager.flush();
        entityManager.clear();

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE id = ?", Long.class, reportId);
        assertThat(remaining).isZero();
        assertThat(reporterId).isNotNull(); // sanity that we did delete the right user
    }

    @Test
    void targetIdHasNoForeignKey_targetDeletionLeavesReportInPlace() {
        Mentee reporter = saveMentee("rr_orphan_a@test.com");
        Mentee target = saveMentee("rr_orphan_b@test.com");
        Long targetId = target.getId();
        Report r = reportRepository.save(newReport(reporter, ReportTargetType.USER,
                targetId, ProblemType.SPAM, "x"));

        menteeRepository.delete(target);
        entityManager.flush();
        entityManager.clear();

        // Report row survives — target_id has no FK by design, so target
        // deletion cannot cascade-orphan the audit trail.
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE id = ?", Long.class, r.getId());
        assertThat(remaining).isEqualTo(1L);
    }

    @Test
    void findAllAdminIds_returnsOnlyAdmins() {
        // No admin in this test (mentees + mentors only seeded).
        Mentee a = saveMentee("rr_adm_a@test.com");
        Mentor m = saveMentor("rr_adm_m@test.com");

        java.util.List<Long> adminIds = userRepository.findAllAdminIds();

        assertThat(adminIds).doesNotContain(a.getId(), m.getId());
        // We don't seed an admin here; the AdminBootstrapper covers the
        // populated case in integration tests.
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Mentee saveMentee(String email) {
        Mentee m = new Mentee();
        m.setFirstName("Mentee");
        m.setLastName("User");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return menteeRepository.save(m);
    }

    private Mentor saveMentor(String email) {
        Mentor m = new Mentor();
        m.setFirstName("Mentor");
        m.setLastName("User");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(3);
        return mentorRepository.save(m);
    }

    private static Report newReport(User reporter, ReportTargetType type, Long targetId,
                                     ProblemType problem, String description) {
        Report r = new Report();
        r.setReporterId(reporter.getId());
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setProblemType(problem);
        r.setDescription(description);
        r.setStatus(ReportStatus.OPEN);
        r.setCreatedAt(OffsetDateTime.now());
        return r;
    }
}
