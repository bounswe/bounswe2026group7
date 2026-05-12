package com.group7.backend.service;

import com.group7.backend.dto.response.MatchSummary;
import com.group7.backend.entity.LastMatchNotification;
import com.group7.backend.repository.LastMatchNotificationRepository;
import com.group7.backend.repository.MenteeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Per-user transactional unit for the scheduled match-found notification path
 * (#273). Driven by {@code MatchNotificationScheduler}: for each eligible
 * mentee the scheduler invokes {@link #processMentee(Long)}, which re-ranks
 * the mentee's top mentors, compares the current top match's id to the
 * persisted dedup state, and publishes a {@code MATCH_FOUND} event only when
 * the value changed.
 *
 * <p><b>System component.</b> Bypasses controller-layer authorization
 * deliberately — the scheduler is not invoked in any authenticated user's
 * context. Authorization checks belong on the controllers; this code path
 * has access to all eligible users by design.
 *
 * <p><b>Why a separate bean from the scheduler.</b> Spring's default proxy-
 * based AOP ignores {@code @Transactional} when methods are called via
 * self-invocation. Putting the {@code REQUIRES_NEW} per-user transaction on
 * a separate bean (this one) and having the scheduler invoke it across the
 * proxy boundary is what makes the propagation hint actually take effect.
 *
 * <p><b>Failure isolation.</b> {@link #processMentee} runs in its own
 * {@code REQUIRES_NEW} transaction. If processing user A throws, A's
 * transaction rolls back (state row not updated, AFTER_COMMIT skipped, no
 * notification persisted), and the scheduler's per-user try/catch logs and
 * continues with user B. The next scheduler tick will retry A naturally
 * (state is unchanged from A's perspective).
 *
 * <p><b>Layered duplicate protection</b> against multi-instance and crash
 * scenarios:
 * <ol>
 *   <li>State-row equality check below — primary gate, skips publish when
 *       the top match is unchanged since the last notification.</li>
 *   <li>{@code @Version} on {@link LastMatchNotification} — concurrent
 *       UPDATE from two instances → loser throws
 *       {@code OptimisticLockException}, transaction rolls back,
 *       AFTER_COMMIT skips. PK uniqueness handles the symmetric INSERT race.</li>
 *   <li>{@code NotificationEventListener}'s 24h body-string dedup — final
 *       safety net for any duplicate that slips past the first two layers
 *       (e.g., a same-firstName collision across distinct counterparts).</li>
 * </ol>
 *
 * <p><b>Implementation note.</b> The change-detection algorithm lives in the
 * generic {@link #processUser}; {@code processMentee} is a thin adapter that
 * supplies the load / eligibility / rank functions. The generic shape is
 * retained because it keeps any future addition (metrics, tracing,
 * alternative dedup) confined to a single place.
 */
@Service
public class MatchNotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationProcessor.class);

    private final MatchingService matchingService;
    private final MenteeRepository menteeRepository;
    private final LastMatchNotificationRepository stateRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public MatchNotificationProcessor(MatchingService matchingService,
                                      MenteeRepository menteeRepository,
                                      LastMatchNotificationRepository stateRepository,
                                      NotificationEventPublisher notificationEventPublisher,
                                      Clock clock) {
        this.matchingService = matchingService;
        this.menteeRepository = menteeRepository;
        this.stateRepository = stateRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    /**
     * Re-rank top mentors for a single mentee and publish a notification only
     * when the top mentor's id differs from the previously notified one.
     *
     * @return {@code true} iff a notification event was published (used by the
     *         scheduler for the per-run summary log).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processMentee(Long menteeId) {
        return processUser(
                menteeId,
                "mentor",
                menteeRepository::findById,
                m -> m.getActiveMentorId() == null,
                m -> matchingService.rankMentorsFor(m, null));
    }

    /**
     * Generic change-detection: load the user, re-check eligibility (race
     * window since the scheduler's snapshot), rank, compare top match's id
     * against the persisted state, and fire-and-upsert if it changed.
     *
     * @param userId       recipient id (mentee, in the only current caller).
     * @param matchLabel   human-readable role of the matched user ({@code "mentor"}),
     *                     used in the null-id error log.
     * @param loader       entity loader keyed by {@code userId}.
     * @param isEligible   second-chance eligibility predicate (race re-check).
     * @param ranker       pure rank function returning a list whose first element is the top match.
     */
    private <T> boolean processUser(
            Long userId,
            String matchLabel,
            Function<Long, Optional<T>> loader,
            Predicate<T> isEligible,
            Function<T, List<? extends MatchSummary>> ranker) {

        // Race re-check against the eligibility-list snapshot: the user may
        // have been deleted, or had eligibility revoked, between the
        // scheduler's eligibility query and this transaction.
        T user = loader.apply(userId).orElse(null);
        if (user == null || !isEligible.test(user)) {
            return false;
        }

        List<? extends MatchSummary> ranked = ranker.apply(user);
        if (ranked.isEmpty()) {
            return false;
        }

        MatchSummary top = ranked.get(0);
        Long topId = top.getId();
        if (topId == null) {
            // The DTO factory (MentorMatchResponse.from) explicitly sets id
            // from the entity. A null here means the ranker contract is
            // broken — surface loudly so monitoring catches it, and skip so
            // the rest of the batch keeps moving.
            log.error("Top {} for user {} has null id; skipping (DTO mapping bug)", matchLabel, userId);
            return false;
        }

        // Single load: `row` serves both the change-detection check and the
        // upsert mutation. {@code Objects.equals(null, X)} returns false for
        // a fresh row (notifiedMatchUserId is null), so first-time recipients
        // fall through to the publish path.
        LastMatchNotification row = stateRepository.findById(userId)
                .orElseGet(LastMatchNotification::new);
        if (Objects.equals(row.getNotifiedMatchUserId(), topId)) {
            return false;  // top match unchanged since last notification
        }

        notificationEventPublisher.publishMatchFound(userId, top.getFirstName());
        row.setUserId(userId);
        row.setNotifiedMatchUserId(topId);
        row.setSentAt(OffsetDateTime.now(clock));
        stateRepository.save(row);
        return true;
    }
}
