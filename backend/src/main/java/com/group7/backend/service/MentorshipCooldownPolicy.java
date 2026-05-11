package com.group7.backend.service;

import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.repository.MentorshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Enforces a cool-down window before the same mentor–mentee pair can
 * re-match after a previous mentorship terminates (#133).
 *
 * <p>Active mentorships are still gated by {@code mentee.activeMentorId};
 * this policy adds a second gate for pairs whose mentorship has ALREADY
 * terminated, blocking immediate re-requests / re-acceptances. The window
 * is configured by {@code app.mentorship.cooldown.duration} (ISO-8601
 * Duration, default 7 days).
 */
@Service
public class MentorshipCooldownPolicy {

    private static final Logger log = LoggerFactory.getLogger(MentorshipCooldownPolicy.class);

    private final MentorshipRepository mentorshipRepository;
    private final Clock clock;
    private final Duration cooldown;

    public MentorshipCooldownPolicy(MentorshipRepository mentorshipRepository,
                                    Clock clock,
                                    @Value("${app.mentorship.cooldown.duration:PT168H}") Duration cooldown) {
        this.mentorshipRepository = mentorshipRepository;
        this.clock = clock;
        this.cooldown = cooldown;
    }

    /**
     * @throws MentorshipRequestException if the pair terminated a prior mentorship
     *         less than {@link #cooldown} ago. The message names the date the
     *         pair becomes eligible again so callers can surface it directly.
     */
    @Transactional(readOnly = true)
    public void assertNotInCooldown(Long mentorId, Long menteeId) {
        OffsetDateTime lastTerminated = mentorshipRepository
                .findLastTerminatedAtForPair(mentorId, menteeId)
                .orElse(null);
        if (lastTerminated == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime eligibleAt = lastTerminated.plus(cooldown);
        if (eligibleAt.isAfter(now)) {
            log.warn("Mentorship cool-down blocked: mentorId={}, menteeId={}, "
                            + "lastTerminated={}, eligibleAt={}",
                    mentorId, menteeId, lastTerminated, eligibleAt);
            throw new MentorshipRequestException(
                    "This mentor and mentee can re-match after " + eligibleAt
                            + " (cool-down after the previous mentorship ended).");
        }
    }
}
