package com.group7.backend.service;

import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.CancelMentorshipRequest;
import com.group7.backend.dto.request.EndMentorshipRequest;
import com.group7.backend.dto.request.ExtendMentorshipRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipAuditLogResponse;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipAuditLogRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MentorshipService {

    private static final Logger log = LoggerFactory.getLogger(MentorshipService.class);
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(1, 3, 6);

    private final MentorshipRepository mentorshipRepository;
    private final MentorshipRequestRepository mentorshipRequestRepository;
    private final MentorshipAuditLogRepository mentorshipAuditLogRepository;
    private final MentorshipCleanupService mentorshipCleanupService;
    private final MentorshipCooldownPolicy mentorshipCooldownPolicy;
    private final NotificationEventPublisher notificationEventPublisher;
    private final BanService banService;
    private final Clock clock;

    public MentorshipService(MentorshipRepository mentorshipRepository,
                             MentorshipRequestRepository mentorshipRequestRepository,
                             MentorshipAuditLogRepository mentorshipAuditLogRepository,
                             MentorshipCleanupService mentorshipCleanupService,
                             MentorshipCooldownPolicy mentorshipCooldownPolicy,
                             NotificationEventPublisher notificationEventPublisher,
                             BanService banService,
                             Clock clock) {
        this.mentorshipRepository = mentorshipRepository;
        this.mentorshipRequestRepository = mentorshipRequestRepository;
        this.mentorshipAuditLogRepository = mentorshipAuditLogRepository;
        this.mentorshipCleanupService = mentorshipCleanupService;
        this.mentorshipCooldownPolicy = mentorshipCooldownPolicy;
        this.notificationEventPublisher = notificationEventPublisher;
        this.banService = banService;
        this.clock = clock;
    }

    @Transactional
    public MentorshipResponse acceptRequest(Long mentorId, Long requestId, AcceptRequestRequest dto) {
        MentorshipRequest request = mentorshipRequestRepository.findById(requestId)
                .filter(r -> r.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found"));

        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            log.warn("Mentorship acceptance rejected: requestId={} is not pending", requestId);
            throw new MentorshipRequestException("Request is no longer pending");
        }

        Mentee mentee = request.getMentee();
        Mentor mentor = request.getMentor();

        if (mentee.getActiveMentorId() != null) {
            log.warn("Mentorship acceptance rejected: menteeId={} already has active mentor", mentee.getId());
            throw new MentorshipRequestException("Mentee already has an active mentor");
        }

        if (mentor.getCurrentMenteeCount() >= mentor.getMaxMenteeCapacity()) {
            log.warn("Mentorship acceptance rejected: mentorId={} is at capacity", mentor.getId());
            throw new MentorshipRequestException("You have reached your maximum mentee capacity");
        }

        if (!ALLOWED_DURATIONS.contains(dto.getDuration())) {
            log.warn("Mentorship acceptance rejected: invalid duration={} for requestId={}", dto.getDuration(), requestId);
            throw new MentorshipRequestException("Duration must be 1, 3, or 6 months");
        }

        // Cool-down (#133): block re-acceptance if this pair just terminated.
        // Throws MentorshipRequestException; caller surfaces 409.
        mentorshipCooldownPolicy.assertNotInCooldown(mentor.getId(), mentee.getId());

        request.setStatus(MentorshipRequestStatus.ACCEPTED);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Mentorship mentorship = new Mentorship();
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setRequest(request);
        mentorship.setStartDate(now);
        mentorship.setEndDate(now.plusMonths(dto.getDuration()));
        mentorship.setDuration(dto.getDuration());

        mentee.setActiveMentorId(mentorId);
        mentor.setCurrentMenteeCount(mentor.getCurrentMenteeCount() + 1);

        mentorshipRequestRepository.cancelOtherPendingRequests(mentee.getId(), requestId);

        Mentorship saved = mentorshipRepository.save(mentorship);
        mentorshipAuditLogRepository.save(MentorshipAuditLog.of(
                saved.getId(), null, MentorshipStatus.ACTIVE, mentorId, null));
        log.info("Mentorship accepted: mentorshipId={}, mentorId={}, menteeId={}, requestId={}",
            saved.getId(), mentor.getId(), mentee.getId(), requestId);
        notificationEventPublisher.publishRequestAccepted(mentee.getId(), mentor.getFirstName());
        return MentorshipResponse.from(saved);
    }

    @Transactional
    public void rejectRequest(Long mentorId, Long requestId) {
        MentorshipRequest request = mentorshipRequestRepository.findById(requestId)
                .filter(r -> r.getMentor().getId().equals(mentorId))
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship request not found"));

        if (request.getStatus() != MentorshipRequestStatus.PENDING) {
            log.warn("Mentorship rejection rejected: requestId={} is not pending", requestId);
            throw new MentorshipRequestException("Request is no longer pending");
        }

        request.setStatus(MentorshipRequestStatus.REJECTED);
        log.info("Mentorship request rejected: requestId={}, mentorId={}", requestId, mentorId);
        notificationEventPublisher.publishRequestRejected(request.getMentee().getId(), request.getMentor().getFirstName());
    }

    @Transactional(readOnly = true)
    public List<MentorshipResponse> getActiveMentorships(Long userId) {
        return mentorshipRepository.findByUserIdAndStatus(userId, MentorshipStatus.ACTIVE).stream()
                .map(MentorshipResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorshipResponse getMentorship(Long userId, Long mentorshipId) {
        Mentorship mentorship = findForParticipant(userId, mentorshipId);
        return MentorshipResponse.from(mentorship);
    }

    @Transactional
    public MentorshipResponse setSharedGoal(Long userId, Long mentorshipId, SharedGoalRequest dto) {
        Mentorship mentorship = findForParticipant(userId, mentorshipId);

        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            log.warn("Shared goal update rejected: mentorshipId={} not active", mentorshipId);
            throw new MentorshipRequestException("Mentorship is not active");
        }

        mentorship.setSharedGoal(dto.getSharedGoal());
        Mentorship saved = mentorshipRepository.save(mentorship);
        log.info("Shared goal updated: mentorshipId={}, updatedByUserId={}", mentorshipId, userId);
        return MentorshipResponse.from(saved);
    }

    /**
     * Mentee cancels an active mentorship (#133, scoped down by #237). Mentor
     * uses {@link #endMentorship} for graceful termination instead. Children
     * (meetings, tasks, milestones, conversation/messages) are deleted by
     * {@link MentorshipCleanupService}; the mentorship row itself is kept
     * with {@code status = CANCELLED} so cool-down lookups can find the
     * termination. The cancellation is recorded against {@link BanService}
     * so the existing auto-ban escalation (#134) ramps up for repeat
     * offenders.
     *
     * @throws ResourceNotFoundException if the user is not a participant
     * @throws AccessDeniedException     if the user is the mentor (403)
     * @throws MentorshipRequestException if the mentorship is not currently ACTIVE (409)
     */
    @Transactional
    public MentorshipResponse cancelMentorship(Long actorUserId,
                                               Long mentorshipId,
                                               CancelMentorshipRequest dto) {
        Mentorship mentorship = findForParticipant(actorUserId, mentorshipId);

        Mentor mentor = mentorship.getMentor();
        Mentee mentee = mentorship.getMentee();
        if (!mentee.getId().equals(actorUserId)) {
            log.warn("Cancel rejected: mentorshipId={} attempted by non-mentee userId={}",
                    mentorshipId, actorUserId);
            throw new AccessDeniedException("Only the mentee can cancel a mentorship; mentors should use /end");
        }

        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            log.warn("Cancel rejected: mentorshipId={} is in status {}", mentorshipId, mentorship.getStatus());
            throw new MentorshipRequestException("Mentorship is not active");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        mentorship.setStatus(MentorshipStatus.CANCELLED);
        mentorship.setTerminatedAt(now);
        mentorship.setTerminatedByUserId(actorUserId);
        mentorship.setCancellationReason(dto.getReason());

        // Free the mentee's slot only if this mentorship is the active one.
        // Guards against a stale activeMentorId that points elsewhere.
        if (Objects.equals(mentee.getActiveMentorId(), mentor.getId())) {
            mentee.setActiveMentorId(null);
        }
        if (mentor.getCurrentMenteeCount() > 0) {
            mentor.setCurrentMenteeCount(mentor.getCurrentMenteeCount() - 1);
        }

        mentorshipCleanupService.cleanupChildren(mentorshipId);

        Mentorship saved = mentorshipRepository.save(mentorship);

        mentorshipAuditLogRepository.save(MentorshipAuditLog.of(
                saved.getId(), MentorshipStatus.ACTIVE, MentorshipStatus.CANCELLED,
                actorUserId, dto.getReason()));

        // Hook into the auto-ban system (#134). Mentee-driven cancellation of
        // an active mentorship counts as a violation, the same as cancelling
        // a pending request, and feeds the same escalating-ban policy.
        banService.recordCancellation(actorUserId, "Cancelled active mentorship: " + dto.getReason());

        notificationEventPublisher.publishMentorshipCancelled(
                mentor.getId(), mentee.getFirstName(), dto.getReason());

        log.info("Mentorship cancelled by mentee: mentorshipId={}, menteeId={}, mentorId={}",
                mentorshipId, actorUserId, mentor.getId());
        return MentorshipResponse.from(saved);
    }

    /**
     * Mentor ends an active mentorship gracefully (#237). Sets
     * {@code status = COMPLETED}, stamps {@code endDate = now}, cleans up
     * children, audits the transition, and notifies the mentee. Reason is
     * optional (mentor wrap-up note, not a violation — the mentor is not
     * subject to ban escalation).
     */
    @Transactional
    public MentorshipResponse endMentorship(Long mentorUserId,
                                            Long mentorshipId,
                                            EndMentorshipRequest dto) {
        Mentorship mentorship = findForParticipant(mentorUserId, mentorshipId);

        Mentor mentor = mentorship.getMentor();
        Mentee mentee = mentorship.getMentee();
        if (!mentor.getId().equals(mentorUserId)) {
            log.warn("End rejected: mentorshipId={} attempted by non-mentor userId={}",
                    mentorshipId, mentorUserId);
            throw new AccessDeniedException("Only the mentor can end a mentorship; mentees should use /cancel");
        }

        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            log.warn("End rejected: mentorshipId={} is in status {}", mentorshipId, mentorship.getStatus());
            throw new MentorshipRequestException("Mentorship is not active");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        mentorship.setEndDate(now);
        mentorship.setTerminatedAt(now);
        mentorship.setTerminatedByUserId(mentorUserId);
        mentorship.setCancellationReason(dto.getReason());

        if (Objects.equals(mentee.getActiveMentorId(), mentor.getId())) {
            mentee.setActiveMentorId(null);
        }
        if (mentor.getCurrentMenteeCount() > 0) {
            mentor.setCurrentMenteeCount(mentor.getCurrentMenteeCount() - 1);
        }

        mentorshipCleanupService.cleanupChildren(mentorshipId);

        Mentorship saved = mentorshipRepository.save(mentorship);

        mentorshipAuditLogRepository.save(MentorshipAuditLog.of(
                saved.getId(), MentorshipStatus.ACTIVE, MentorshipStatus.COMPLETED,
                mentorUserId, dto.getReason()));

        notificationEventPublisher.publishMentorshipEnded(
                mentee.getId(), mentor.getFirstName(), dto.getReason());

        log.info("Mentorship ended by mentor: mentorshipId={}, mentorId={}, menteeId={}",
                mentorshipId, mentorUserId, mentee.getId());
        return MentorshipResponse.from(saved);
    }

    /**
     * Mentor extends the duration of an active mentorship (#237). Pushes
     * {@code endDate} forward by 1, 3, or 6 months and increments
     * {@code duration} accordingly. Records the lifecycle event in the audit
     * log even though the status doesn't change.
     */
    @Transactional
    public MentorshipResponse extendMentorship(Long mentorUserId,
                                               Long mentorshipId,
                                               ExtendMentorshipRequest dto) {
        Mentorship mentorship = findForParticipant(mentorUserId, mentorshipId);

        Mentor mentor = mentorship.getMentor();
        Mentee mentee = mentorship.getMentee();
        if (!mentor.getId().equals(mentorUserId)) {
            log.warn("Extend rejected: mentorshipId={} attempted by non-mentor userId={}",
                    mentorshipId, mentorUserId);
            throw new AccessDeniedException("Only the mentor can extend a mentorship");
        }

        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            log.warn("Extend rejected: mentorshipId={} is in status {}", mentorshipId, mentorship.getStatus());
            throw new MentorshipRequestException("Mentorship is not active");
        }

        int additional = dto.getAdditionalMonths();
        if (!ALLOWED_DURATIONS.contains(additional)) {
            // DTO @AssertTrue covers this at the controller boundary, but
            // service-side defence-in-depth catches non-validated callers.
            throw new MentorshipRequestException("additionalMonths must be 1, 3, or 6");
        }

        OffsetDateTime newEndDate = mentorship.getEndDate().plusMonths(additional);
        mentorship.setEndDate(newEndDate);
        mentorship.setDuration(mentorship.getDuration() + additional);

        Mentorship saved = mentorshipRepository.save(mentorship);

        mentorshipAuditLogRepository.save(MentorshipAuditLog.of(
                saved.getId(), MentorshipStatus.ACTIVE, MentorshipStatus.ACTIVE,
                mentorUserId, "Extended by " + additional + " month(s); new end date " + newEndDate));

        notificationEventPublisher.publishMentorshipExtended(
                mentee.getId(), mentor.getFirstName(), additional, newEndDate);

        log.info("Mentorship extended: mentorshipId={}, mentorId={}, additionalMonths={}, newEndDate={}",
                mentorshipId, mentorUserId, additional, newEndDate);
        return MentorshipResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MentorshipAuditLogResponse> getAuditTrail(Long userId, Long mentorshipId) {
        // Participant gate: throws 404 if userId is not the mentor or mentee.
        findForParticipant(userId, mentorshipId);
        return mentorshipAuditLogRepository
                .findByMentorshipIdOrderByCreatedAtAsc(mentorshipId).stream()
                .map(MentorshipAuditLogResponse::from)
                .toList();
    }

    /**
     * Package-private so other services in this package (e.g. MentorshipProgressService)
     * can reuse the participant-filter lookup without duplicating it. Filters on the FK
     * columns directly via the repository so we don't trigger LAZY loads on
     * {@code mentor} / {@code mentee} just to compare ids. Do not narrow back to private
     * without checking other callers.
     */
    Mentorship findForParticipant(Long userId, Long mentorshipId) {
        return mentorshipRepository.findByIdAndParticipant(mentorshipId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));
    }
}
