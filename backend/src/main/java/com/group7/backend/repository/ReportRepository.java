package com.group7.backend.repository;

import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Report} (#135).
 *
 * <p>Two read paths:
 * <ul>
 *   <li>{@link #findByReporterIdOrderByCreatedAtDesc} — user's own
 *       submitted reports for the {@code GET /api/reports/me} surface.
 *       Filters strictly by the authenticated reporter id; no cross-user
 *       access is structurally possible (BOLA-safe).</li>
 *   <li>{@link #findForAdminQueue} — admin queue. Status and target-type
 *       are both nullable filters; the JPQL uses
 *       {@code (:param IS NULL OR ...)} so a single endpoint serves all
 *       four combinations.</li>
 * </ul>
 *
 * <p>The duplicate-prevention partial unique index lives on the table
 * itself ({@code idx_reports_active_unique} in V27) — there's no
 * dedicated finder here. {@link com.group7.backend.service.ReportService}
 * catches the {@code DataIntegrityViolationException} from the index
 * and translates to a 409 via {@code DuplicateReportException}.
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * "My reports" page for {@code GET /api/reports/me}. Caller passes
     * the authenticated reporter id; no other user's reports are
     * reachable through this method.
     */
    Page<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    /**
     * Admin queue with optional status + target-type filters. Both
     * parameters can be null to mean "no filter on this dimension."
     * Sorted recent-first; backed by {@code idx_reports_status_created}
     * when {@code status} is supplied.
     */
    @Query("""
            SELECT r FROM Report r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<Report> findForAdminQueue(@Param("status") ReportStatus status,
                                    @Param("targetType") ReportTargetType targetType,
                                    Pageable pageable);
}
