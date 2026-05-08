package com.group7.backend.service;

import com.group7.backend.dto.request.CreateReportRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportStatusMachine;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.exception.DuplicateReportException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.ReportRepository;
import com.group7.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service layer for the user-reporting + admin-moderation surface (#135).
 *
 * <p>Concerns:
 * <ul>
 *   <li><b>Submit</b> ({@link #createReport}) — validates target +
 *       self-report rule, persists, fan-outs a {@code REPORT_RECEIVED}
 *       notification per admin, returns the slim DTO (no target summary).</li>
 *   <li><b>User-own list</b> ({@link #listMyReports}) — strictly filtered
 *       by the authenticated reporter id; structurally BOLA-safe.</li>
 *   <li><b>Admin queue</b> ({@link #listForAdmin} + {@link #getForAdmin}) —
 *       optional status + target-type filters; admin sees the
 *       denormalised target summary.</li>
 *   <li><b>Status transitions</b> ({@link #updateStatus}) — small
 *       state-machine validating transitions out of OPEN /
 *       UNDER_REVIEW; terminal RESOLVED / DISMISSED reject any further
 *       change.</li>
 * </ul>
 *
 * <p><b>Audit-logging discipline (OWASP A09).</b> Every report
 * submission and every admin transition is logged at INFO with enough
 * context for forensics (ids + enum types). The free-text
 * {@code description} field is <b>never</b> logged at any level —
 * potential PII / sensitive third-party content. Pinned by
 * {@code ReportServiceTest} via a Logback list-appender.
 *
 * <p><b>Concurrent-admin safety.</b> {@code Report.@Version} bumps on
 * every save, so two admins racing on the same transition will see one
 * winner; the loser surfaces {@code ObjectOptimisticLockingFailureException}
 * → 409 via the existing {@code ConcurrencyFailureException} handler.
 *
 * <p><b>Duplicate prevention.</b> Layered defence: optional pre-check
 * for friendly UX is omitted (extra DB read for marginal gain) — the
 * {@code idx_reports_active_unique} partial unique index is the source
 * of truth. The catch-and-translate happens here.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MentorshipRepository mentorshipRepository;
    private final FeedPostRepository feedPostRepository;
    private final ReportMapper reportMapper;
    private final NotificationEventPublisher notificationEventPublisher;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         MentorshipRepository mentorshipRepository,
                         FeedPostRepository feedPostRepository,
                         ReportMapper reportMapper,
                         NotificationEventPublisher notificationEventPublisher) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.feedPostRepository = feedPostRepository;
        this.reportMapper = reportMapper;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    // ── Submit ──────────────────────────────────────────────────────────────

    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        rejectSelfReport(request, reporterId);
        validateTargetExists(request.targetType(), request.targetId(), reporterId);

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setProblemType(request.problemType());
        report.setDescription(request.description());
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(OffsetDateTime.now());

        Report saved;
        try {
            saved = reportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            // Caught from the partial-unique index. Constraint-name detail
            // logged for ops triage; user-facing message is fixed and
            // schema-agnostic.
            log.warn("Duplicate report rejected: reporterId={}, targetType={}, targetId={}, cause={}",
                    reporterId, request.targetType(), request.targetId(),
                    ex.getMostSpecificCause().getMessage());
            throw new DuplicateReportException(
                    "An active report already exists for this target");
        }

        // OWASP A09 audit log. Description is intentionally NOT logged
        // (potential PII / sensitive third-party content).
        log.info("Report submitted: id={}, reporterId={}, targetType={}, targetId={}, problemType={}",
                saved.getId(), reporterId, saved.getTargetType(), saved.getTargetId(),
                saved.getProblemType());

        fanOutToAdmins(saved);
        return reportMapper.toResponse(saved, false);
    }

    // ── User-own list ───────────────────────────────────────────────────────

    public Page<ReportResponse> listMyReports(Long reporterId, Pageable pageable) {
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId, pageable)
                .map(r -> reportMapper.toResponse(r, false));
    }

    // ── Admin queue ─────────────────────────────────────────────────────────

    public Page<ReportResponse> listForAdmin(ReportStatus status,
                                              ReportTargetType targetType,
                                              Pageable pageable) {
        return reportRepository.findForAdminQueue(status, targetType, pageable)
                .map(r -> reportMapper.toResponse(r, true));
    }

    public ReportResponse getForAdmin(Long reportId) {
        Report r = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report not found with id: " + reportId));
        return reportMapper.toResponse(r, true);
    }

    // ── Status transitions ──────────────────────────────────────────────────

    @Transactional
    public ReportResponse updateStatus(Long reportId, Long adminId, ReportStatus newStatus) {
        Report r = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Report not found with id: " + reportId));
        ReportStatus from = r.getStatus();
        validateTransition(from, newStatus);

        r.setStatus(newStatus);
        r.setReviewedAt(OffsetDateTime.now());
        r.setReviewedById(adminId);
        Report saved = reportRepository.save(r);   // @Version bumps; concurrent → 409

        log.info("Report transitioned: id={}, adminId={}, from={}, to={}",
                saved.getId(), adminId, from, newStatus);
        return reportMapper.toResponse(saved, true);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void rejectSelfReport(CreateReportRequest request, Long reporterId) {
        if (request.targetType() == ReportTargetType.USER
                && reporterId.equals(request.targetId())) {
            throw new IllegalArgumentException("Users cannot report themselves");
        }
    }

    private void validateTargetExists(ReportTargetType type, Long targetId, Long reporterId) {
        switch (type) {
            case USER -> {
                if (!userRepository.existsById(targetId)) {
                    throw new ResourceNotFoundException("User not found with id: " + targetId);
                }
            }
            case MENTORSHIP -> {
                Mentorship m = mentorshipRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Mentorship not found with id: " + targetId));
                boolean isParticipant =
                        m.getMentor().getId().equals(reporterId)
                                || m.getMentee().getId().equals(reporterId);
                if (!isParticipant) {
                    throw new IllegalArgumentException(
                            "Reporter is not a participant of this mentorship");
                }
            }
            case POST -> {
                if (feedPostRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                    throw new ResourceNotFoundException(
                            "Feed post not found with id: " + targetId);
                }
            }
        }
    }

    private static void validateTransition(ReportStatus from, ReportStatus to) {
        if (!ReportStatusMachine.canTransition(from, to)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + from + " → " + to);
        }
    }

    /**
     * Publishes one {@code REPORT_RECEIVED} notification per admin via
     * the existing {@code NotificationEventPublisher}. The downstream
     * {@code NotificationEventListener} is {@code @Async}, so the
     * actual notification-row insert + push delivery is parallelised
     * on the global executor pool.
     *
     * <p>If admin count grows past dozens, refactor to a single
     * broadcast event that fans out internally inside a dedicated
     * listener — this avoids N publishEvent calls in the publisher's
     * transaction.
     */
    private void fanOutToAdmins(Report report) {
        String reporterFirstName = userRepository.findById(report.getReporterId())
                .map(u -> u.getFirstName())
                .orElse("Someone");
        List<Long> adminIds = userRepository.findAllAdminIds();
        for (Long adminId : adminIds) {
            notificationEventPublisher.publishReportReceived(
                    adminId, reporterFirstName, report.getTargetType());
        }
    }
}
