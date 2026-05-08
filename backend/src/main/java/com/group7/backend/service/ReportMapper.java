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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@link Report} entities to {@link ReportResponse} DTOs (#135).
 * Centralises the polymorphic {@code targetSummary} dispatch and the
 * reporter / reviewer first-name lookups so the service stays focused
 * on business logic.
 *
 * <p><b>Two mapping paths.</b>
 * <ul>
 *   <li>{@link #toResponse(Report, boolean)} — single-row, used by the
 *       admin detail endpoint ({@code GET /api/admin/reports/{id}}). Up
 *       to three {@code findById} calls per call.</li>
 *   <li>{@link #toResponses(Page, boolean)} — batched, used by both list
 *       endpoints. Pre-loads every reporter / reviewer / target row in
 *       at most four queries (one user-batch, one mentorship-batch, one
 *       post-batch, plus the reporter+reviewer-only user-batch when no
 *       target summary is needed). Mirrors the
 *       {@code FeedPostMapper.toListItems} pattern from #350 to keep
 *       paged reads N+1-free.</li>
 * </ul>
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
     * Single-row mapping. Pass {@code includeTargetSummary = true} only
     * on admin surfaces — it triggers an extra repository read to
     * denormalise the target snippet. Used by the admin detail endpoint.
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
        return buildResponse(report, reporterFirstName, reviewedByFirstName, targetSummary);
    }

    /**
     * Batched page mapping. Pre-loads every reporter / reviewer first
     * name and (when {@code includeTargetSummary}) every target row in a
     * bounded number of queries:
     * <ul>
     *   <li>1× {@code userRepository.findAllById} for reporter + reviewer
     *       + (if needed) USER-target ids — one query.</li>
     *   <li>1× {@code mentorshipRepository.findAllById} for MENTORSHIP
     *       targets — one query, only fired when at least one row's
     *       target is a mentorship.</li>
     *   <li>1× {@code feedPostRepository.findAllByIdInAndDeletedAtIsNull}
     *       for POST targets — one query, only fired when at least one
     *       row's target is a post.</li>
     * </ul>
     * Replaces what would otherwise be up to {@code 3 × pageSize}
     * findById calls with a constant 1–3 batched queries.
     */
    public Page<ReportResponse> toResponses(Page<Report> page, boolean includeTargetSummary) {
        List<Report> rows = page.getContent();
        if (rows.isEmpty()) {
            return page.map(r -> null);
        }
        Map<Long, User> usersById = preloadUsers(rows, includeTargetSummary);
        Map<Long, Mentorship> mentorshipsById = includeTargetSummary
                ? preloadMentorships(rows) : Map.of();
        Map<Long, FeedPost> postsById = includeTargetSummary
                ? preloadPosts(rows) : Map.of();
        return page.map(r -> mapWithCaches(r, includeTargetSummary,
                usersById, mentorshipsById, postsById));
    }

    // ── single-row target dispatch ──────────────────────────────────────────

    /**
     * Polymorphic dispatch on {@link ReportTargetType} for the single-row
     * mapping path. Returns a short human-readable label for admin
     * context. Email is intentionally NOT included for {@code USER}
     * targets — first + last name are enough to identify; surfacing
     * email broadens the admin's disclosure surface unnecessarily.
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

    // ── batched mapping helpers ─────────────────────────────────────────────

    private Map<Long, User> preloadUsers(List<Report> rows, boolean includeTargetSummary) {
        Set<Long> ids = new HashSet<>();
        for (Report r : rows) {
            ids.add(r.getReporterId());
            if (r.getReviewedById() != null) {
                ids.add(r.getReviewedById());
            }
            if (includeTargetSummary && r.getTargetType() == ReportTargetType.USER) {
                ids.add(r.getTargetId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private Map<Long, Mentorship> preloadMentorships(List<Report> rows) {
        Set<Long> ids = rows.stream()
                .filter(r -> r.getTargetType() == ReportTargetType.MENTORSHIP)
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Mentorship> map = new HashMap<>();
        mentorshipRepository.findAllById(ids).forEach(m -> map.put(m.getId(), m));
        return map;
    }

    private Map<Long, FeedPost> preloadPosts(List<Report> rows) {
        Set<Long> ids = rows.stream()
                .filter(r -> r.getTargetType() == ReportTargetType.POST)
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, FeedPost> map = new HashMap<>();
        feedPostRepository.findAllByIdInAndDeletedAtIsNull(ids)
                .forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private ReportResponse mapWithCaches(Report report,
                                         boolean includeTargetSummary,
                                         Map<Long, User> usersById,
                                         Map<Long, Mentorship> mentorshipsById,
                                         Map<Long, FeedPost> postsById) {
        String reporterFirstName = nameFrom(usersById, report.getReporterId());
        String reviewedByFirstName = report.getReviewedById() == null ? null
                : nameFrom(usersById, report.getReviewedById());
        String targetSummary = includeTargetSummary
                ? cachedTargetSummary(report, usersById, mentorshipsById, postsById)
                : null;
        return buildResponse(report, reporterFirstName, reviewedByFirstName, targetSummary);
    }

    private static String nameFrom(Map<Long, User> usersById, Long id) {
        User u = usersById.get(id);
        return u == null ? null : u.getFirstName();
    }

    private static String cachedTargetSummary(Report report,
                                              Map<Long, User> usersById,
                                              Map<Long, Mentorship> mentorshipsById,
                                              Map<Long, FeedPost> postsById) {
        return switch (report.getTargetType()) {
            case USER -> {
                User u = usersById.get(report.getTargetId());
                yield u == null
                        ? "[user deleted]"
                        : u.getFirstName() + " " + u.getLastName();
            }
            case MENTORSHIP -> {
                Mentorship m = mentorshipsById.get(report.getTargetId());
                yield m == null
                        ? "[mentorship deleted]"
                        : "Mentor: " + m.getMentor().getFirstName()
                                + " / Mentee: " + m.getMentee().getFirstName();
            }
            case POST -> {
                FeedPost p = postsById.get(report.getTargetId());
                yield p == null
                        ? "[post deleted or unavailable]"
                        : excerpt(p.getBody());
            }
        };
    }

    // ── shared response constructor + excerpt ───────────────────────────────

    private static ReportResponse buildResponse(Report report,
                                                String reporterFirstName,
                                                String reviewedByFirstName,
                                                String targetSummary) {
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

    private static String excerpt(String body) {
        if (body == null) return "";
        if (body.length() <= POST_BODY_EXCERPT_LIMIT) return body;
        return body.substring(0, POST_BODY_EXCERPT_LIMIT) + "…";
    }
}
