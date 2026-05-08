package com.group7.backend.service;

import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.Report;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.FeedPostRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Report} entities to {@link ReportResponse} DTOs (#135).
 * Centralises the polymorphic {@code targetSummary} dispatch and the
 * reporter / reviewer first-name lookups so the service stays focused
 * on business logic.
 *
 * <p><b>Per-row reads.</b> For v1 scale (≤ 100 reports per admin
 * queue page) one or two extra {@code findById} calls per row is fine.
 * If the admin queue grows past thousands of rows, refactor to a
 * single-pass batch lookup keyed by target_id + target_type (mirrors
 * the {@code FeedPostMapper.toListItems} pattern from #350).
 *
 * <p><b>Description sensitivity.</b> The mapper passes
 * {@code description} through verbatim — but the field is intentionally
 * never logged at any level by application code. See
 * {@code ReportService} for the audit-logging discipline.
 */
@Component
public class ReportMapper {

    private static final int POST_BODY_EXCERPT_LIMIT = 120;

    private final UserRepository userRepository;
    private final MentorshipRepository mentorshipRepository;
    private final FeedPostRepository feedPostRepository;

    public ReportMapper(UserRepository userRepository,
                        MentorshipRepository mentorshipRepository,
                        FeedPostRepository feedPostRepository) {
        this.userRepository = userRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.feedPostRepository = feedPostRepository;
    }

    /**
     * Map a {@link Report} to its DTO. Pass {@code includeTargetSummary
     * = true} only on admin surfaces — it triggers an extra repository
     * read per row to denormalise the target snippet, which the user's-own
     * list does not need.
     */
    public ReportResponse toResponse(Report report, boolean includeTargetSummary) {
        String reporterFirstName = userRepository.findById(report.getReporterId())
                .map(User::getFirstName)
                .orElse(null);
        String reviewedByFirstName = report.getReviewedById() == null ? null
                : userRepository.findById(report.getReviewedById())
                        .map(User::getFirstName)
                        .orElse(null);
        String targetSummary = includeTargetSummary
                ? resolveTargetSummary(report.getTargetType(), report.getTargetId())
                : null;
        return new ReportResponse(
                report.getId(),
                report.getReporterId(),
                reporterFirstName,
                report.getTargetType(),
                report.getTargetId(),
                targetSummary,
                report.getProblemType(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReviewedAt(),
                report.getReviewedById(),
                reviewedByFirstName);
    }

    /**
     * Polymorphic dispatch on {@link ReportTargetType}. Returns a short
     * human-readable label for admin context. Email is intentionally
     * NOT included for {@code USER} targets — first + last name are
     * enough to identify; surfacing email broadens the admin's
     * disclosure surface unnecessarily.
     */
    private String resolveTargetSummary(ReportTargetType type, Long targetId) {
        return switch (type) {
            case USER -> userRepository.findById(targetId)
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("[user deleted]");
            case MENTORSHIP -> mentorshipRepository.findById(targetId)
                    .map(m -> "Mentor: " + m.getMentor().getFirstName()
                            + " / Mentee: " + m.getMentee().getFirstName())
                    .orElse("[mentorship deleted]");
            case POST -> feedPostRepository.findByIdAndDeletedAtIsNull(targetId)
                    .map(p -> excerpt(p.getBody()))
                    .orElse("[post deleted or unavailable]");
        };
    }

    private static String excerpt(String body) {
        if (body == null) return "";
        if (body.length() <= POST_BODY_EXCERPT_LIMIT) return body;
        return body.substring(0, POST_BODY_EXCERPT_LIMIT) + "…";
    }
}
