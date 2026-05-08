package com.group7.backend.event;

import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.NotificationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Fans out a {@code REPORT_RECEIVED} notification to every admin AFTER
 * a {@link com.group7.backend.entity.Report} create transaction commits
 * (#135).
 *
 * <p><b>Why AFTER_COMMIT.</b> A synchronous fanout from
 * {@code ReportService.createReport} would do an extra DB read (admin-id
 * list) and N publishEvent calls inside the write transaction, holding
 * row locks longer than necessary and coupling the report-submit
 * response to admin-list size. {@link TransactionPhase#AFTER_COMMIT}
 * keeps the submit endpoint fast, ensures fan-out fires only for
 * committed reports (rolled-back submissions produce zero
 * notifications), and matches {@link FeedFanoutListener}'s shape for
 * #349.
 *
 * <p><b>Why {@code @Async}.</b> Notification fan-out spans every admin;
 * even with admin counts in single digits today, running on a worker
 * thread keeps the commit thread free for the next request.
 * {@code fallbackExecution} stays at the default {@code false} — if
 * the publisher is ever invoked outside a transaction the listener
 * stays silent (fail-closed) rather than fanning out a phantom event.
 *
 * <p><b>Top-level try/catch.</b> The global
 * {@link com.group7.backend.config.AsyncConfig} handler logs uncaught
 * async failures, but only with the method signature. The local catch
 * is load-bearing because it logs the same failure with {@code reportId}
 * attached — the field production triage actually greps for. Defence
 * in depth: the local catch handles the expected failure modes (DB
 * blip while loading admins, downstream notification listener throwing);
 * the global handler is the last-resort net.
 *
 * <p><b>Description discipline.</b> The event carries no
 * {@code description} field — the report's free-text description is
 * potential PII and never crosses the listener boundary. The downstream
 * notification body is generic ("New report received") and constructed
 * from {@code reporterFirstName} + {@code targetType} only.
 */
@Component
public class ReportFanoutListener {

    private static final Logger log = LoggerFactory.getLogger(ReportFanoutListener.class);

    private final UserRepository userRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public ReportFanoutListener(UserRepository userRepository,
                                NotificationEventPublisher notificationEventPublisher) {
        this.userRepository = userRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        try {
            // The publisher already carries reporterFirstName so the
            // listener avoids a per-fanout user lookup. Fall back to a
            // generic placeholder if the publisher couldn't load the
            // name (rare — reporter deleted between submit and fanout).
            String reporterFirstName = event.reporterFirstName() != null
                    ? event.reporterFirstName() : "Someone";
            List<Long> adminIds = userRepository.findAllAdminIds();
            for (Long adminId : adminIds) {
                notificationEventPublisher.publishReportReceived(
                        adminId, reporterFirstName, event.targetType());
            }
            log.info("Report fanout: reportId={}, reporterId={}, recipients={}",
                    event.reportId(), event.reporterId(), adminIds.size());
        } catch (RuntimeException ex) {
            // Logged here with reportId context. AsyncConfig's
            // AsyncUncaughtExceptionHandler is the last-resort safety net
            // for anything we missed.
            log.error("Report fanout aborted: reportId={}: {}",
                    event.reportId(), ex.getMessage(), ex);
        }
    }
}
