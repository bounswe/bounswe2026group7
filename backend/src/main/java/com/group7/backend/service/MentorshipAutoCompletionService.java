package com.group7.backend.service;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipAuditLog;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.MentorshipAuditLogRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Auto-terminates ACTIVE mentorships whose {@code endDate} has passed
 * (#237). Each row goes through the same cleanup + audit + notification
 * machinery as a mentor /end, but with {@code terminatedByUserId = null}
 * to denote a system-initiated transition.
 *
 * <p>Invoked by {@link com.group7.backend.scheduler.MentorshipAutoCompletionScheduler}
 * (and directly from tests). Each row is processed in its own
 * transaction (REQUIRES_NEW) so a single failure can't poison the rest of
 * the sweep — mirrors the {@code BanExpiryScheduler} resilience pattern.
 */
@Service
public class MentorshipAutoCompletionService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipAutoCompletionService.class);

    private final MentorshipRepository mentorshipRepository;
    private final MentorshipAuditLogRepository mentorshipAuditLogRepository;
    private final MentorshipCleanupService mentorshipCleanupService;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;
    private final TransactionTemplate perRowTx;

    public MentorshipAutoCompletionService(MentorshipRepository mentorshipRepository,
                                           MentorshipAuditLogRepository mentorshipAuditLogRepository,
                                           MentorshipCleanupService mentorshipCleanupService,
                                           NotificationEventPublisher notificationEventPublisher,
                                           Clock clock,
                                           PlatformTransactionManager transactionManager) {
        this.mentorshipRepository = mentorshipRepository;
        this.mentorshipAuditLogRepository = mentorshipAuditLogRepository;
        this.mentorshipCleanupService = mentorshipCleanupService;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
        // Per-row REQUIRES_NEW transaction. Keeps the sweep resilient — one
        // row's failure rolls back only its own work; subsequent rows still
        // process. Goes through TransactionTemplate so we don't depend on
        // Spring AOP proxying a self-call (which silently bypasses
        // @Transactional and was the cause of the original integration-test
        // regression).
        this.perRowTx = new TransactionTemplate(transactionManager);
        this.perRowTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sweep: load expired ACTIVE mentorships and complete each one. Returns
     * the number of rows successfully processed (failures are logged and
     * skipped). The query runs once outside any transaction so each row's
     * processing can commit independently.
     */
    public int autoCompleteExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Mentorship> expired = mentorshipRepository.findActiveExpiredAt(MentorshipStatus.ACTIVE, now);
        if (expired.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (Mentorship m : expired) {
            try {
                perRowTx.executeWithoutResult(status -> completeOne(m.getId(), now));
                processed++;
            } catch (RuntimeException ex) {
                log.warn("Auto-completion failed for mentorshipId={}: {}",
                        m.getId(), ex.getMessage());
            }
        }
        return processed;
    }

    /**
     * Per-row processing. Invoked inside a {@code REQUIRES_NEW} transaction
     * by {@link #autoCompleteExpired()} via {@link #perRowTx} so a failure
     * rolls back only this row.
     */
    public void completeOne(Long mentorshipId, OffsetDateTime now) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElse(null);
        if (mentorship == null || mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            // Race: someone else terminated it between the sweep query and now.
            return;
        }

        Mentor mentor = mentorship.getMentor();
        Mentee mentee = mentorship.getMentee();

        mentorship.setStatus(MentorshipStatus.COMPLETED);
        mentorship.setTerminatedAt(now);
        mentorship.setTerminatedByUserId(null);

        if (Objects.equals(mentee.getActiveMentorId(), mentor.getId())) {
            mentee.setActiveMentorId(null);
        }
        if (mentor.getCurrentMenteeCount() > 0) {
            mentor.setCurrentMenteeCount(mentor.getCurrentMenteeCount() - 1);
        }

        mentorshipCleanupService.cleanupChildren(mentorshipId);
        mentorshipRepository.save(mentorship);

        mentorshipAuditLogRepository.save(MentorshipAuditLog.of(
                mentorshipId, MentorshipStatus.ACTIVE, MentorshipStatus.COMPLETED,
                null, "Auto-completed at end_date"));

        notificationEventPublisher.publishMentorshipAutoCompleted(
                mentor.getId(), mentee.getFirstName());
        notificationEventPublisher.publishMentorshipAutoCompleted(
                mentee.getId(), mentor.getFirstName());

        log.info("Mentorship auto-completed: mentorshipId={}, mentorId={}, menteeId={}",
                mentorshipId, mentor.getId(), mentee.getId());
    }
}
