package com.group7.backend.service;

import com.group7.backend.dto.response.FollowRecommendationResponse;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.ranking.FollowRanker;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.ScoreResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Follow-recommendation pipeline (#344). Orchestrates the load → score →
 * paginate flow that mirrors {@code MatchingService}: a fixed-size
 * candidate window is fetched from SQL (excluding admins, the viewer,
 * and already-followed users), the ranker scores them in memory using a
 * pre-fetched {@link FollowRecommendationContext}, and the paged page is
 * sliced from the in-memory ranking. Pages beyond the window return
 * empty content; the frontend should treat that as "end of results".
 *
 * <h2>Query budget</h2>
 * Three SQL statements per call regardless of page size:
 * <ol>
 *   <li>Viewer load — {@link UserRepository#findById}.</li>
 *   <li>Viewer's followees — {@link FollowRepository#findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc}
 *       (capped at {@value #MAX_VIEWER_FOLLOWEES} to bound the second-hop query).</li>
 *   <li>Second-hop edges — {@link FollowRepository#findByIdFollowerIdIn}
 *       (skipped when the viewer follows nobody).</li>
 *   <li>Candidate window — {@link UserRepository#findFollowRecommendationCandidates}.</li>
 * </ol>
 * Plus the SUBSELECT collection fetches Hibernate issues to populate
 * each Mentor/Mentee's {@code interests} list — counted separately by
 * Hibernate's collection-fetch counter.
 *
 * <h2>Out-of-scope today (#344 PR description)</h2>
 * <ul>
 *   <li><b>Banned users</b> — the {@code Ban} entity / {@code BanService}
 *       is introduced by the still-open #134/#380. Once merged, add a
 *       {@code NOT EXISTS} sub-clause on the candidate query.</li>
 *   <li><b>Mentee profileVisibility</b> — the field exists on the entity
 *       but is unenforced everywhere on {@code main}; introducing it
 *       only here would be inconsistent. Tracked separately.</li>
 *   <li><b>Recent engagement signal</b> — depends on #348 (FeedPost /
 *       Comment); the ranker carries a TODO marker for the eventual
 *       wiring.</li>
 * </ul>
 */
@Service
public class FollowRecommendationService {

    /**
     * Hard cap on the viewer's followees expanded by the second-hop
     * query. A viewer who follows thousands of users would otherwise
     * push an unbounded {@code IN} list at Postgres; truncating to the
     * most-recent {@value} preserves the strongest-signal subset (per
     * {@link FollowRepository}'s "newest first" sort) while keeping the
     * query budget bounded.
     */
    static final int MAX_VIEWER_FOLLOWEES = 200;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FollowRanker ranker;
    private final int rankingWindow;

    public FollowRecommendationService(UserRepository userRepository,
                                       FollowRepository followRepository,
                                       FollowRanker ranker,
                                       @Value("${app.matching.ranking-window:200}") int rankingWindow) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.ranker = ranker;
        this.rankingWindow = rankingWindow;
    }

    @Transactional(readOnly = true)
    public Page<FollowRecommendationResponse> recommend(Long viewerId, Pageable pageable) {
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + viewerId));

        FollowRecommendationContext ctx = buildContext(viewer);

        List<User> candidates = userRepository.findFollowRecommendationCandidates(
                viewerId, PageRequest.of(0, rankingWindow));

        if (candidates.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<FollowRecommendationResponse> ranked = candidates.stream()
                .map(c -> {
                    ScoreResult sr = ranker.score(c, ctx);
                    return FollowRecommendationResponse.from(c, sr);
                })
                .sorted(Comparator
                        .comparingInt(FollowRecommendationResponse::getScore).reversed()
                        .thenComparing(Comparator
                                .comparingLong(FollowRecommendationResponse::getId).reversed()))
                .toList();

        return slicePage(ranked, pageable);
    }

    private FollowRecommendationContext buildContext(User viewer) {
        Set<String> interestLabels = lowercasedInterests(viewer);

        // Viewer's followees, capped. The repo orders newest-first with
        // a stable id-tiebreaker; truncating the head preserves the
        // most-recent (and likely strongest-signal) subset.
        Pageable followeePage = PageRequest.of(0, MAX_VIEWER_FOLLOWEES);
        List<Follow> myFollows =
                followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(
                        viewer.getId(), followeePage).getContent();
        Set<Long> followeeIds = new HashSet<>(myFollows.size());
        for (Follow f : myFollows) {
            followeeIds.add(f.getId().getFolloweeId());
        }

        Map<Long, Integer> secondHop;
        if (followeeIds.isEmpty()) {
            secondHop = Collections.emptyMap();
        } else {
            secondHop = new HashMap<>();
            List<Follow> hops = followRepository.findByIdFollowerIdIn(followeeIds);
            for (Follow f : hops) {
                Long target = f.getId().getFolloweeId();
                // Don't credit candidates that the viewer is already
                // following or that *are* the viewer.
                if (target.equals(viewer.getId()) || followeeIds.contains(target)) {
                    continue;
                }
                secondHop.merge(target, 1, Integer::sum);
            }
        }

        return new FollowRecommendationContext(viewer.getId(), interestLabels, followeeIds, secondHop);
    }

    private static Set<String> lowercasedInterests(User user) {
        List<String> raw;
        if (user instanceof Mentor mentor) {
            raw = mentor.getInterests();
        } else if (user instanceof Mentee mentee) {
            raw = mentee.getInterests();
        } else {
            return Collections.emptySet();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>(raw.size());
        for (String label : raw) {
            if (label != null) {
                out.add(label.toLowerCase());
            }
        }
        return out;
    }

    /**
     * In-memory page slicer. Mirrors {@code MatchingService}'s private
     * helper; rule-of-two so each service keeps its own copy until a
     * third caller materialises and we extract to {@code support/}.
     */
    private static <T> Page<T> slicePage(List<T> ranked, Pageable pageable) {
        int total = ranked.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(ranked.subList(from, to), pageable, total);
    }
}
