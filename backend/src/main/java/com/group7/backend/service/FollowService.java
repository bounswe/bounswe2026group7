package com.group7.backend.service;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SelfFollowException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service layer for the directional follow graph (#343).
 *
 * <p>The two state-changing methods ({@link #follow}, {@link #unfollow}) are
 * idempotent at the API level. {@link #follow} delegates to a native
 * {@code INSERT ... ON CONFLICT DO NOTHING} that returns {@code 1} on a
 * fresh insert and {@code 0} when the edge already existed; the
 * {@link FollowResult#created} flag carries that distinction up to the
 * controller, which maps it to HTTP {@code 201} vs {@code 200}.
 * {@link #unfollow} uses Spring Data {@code deleteById}, which is silent
 * when the row doesn't exist (Spring Data 3.x guarantee), so duplicate
 * unfollows are no-ops without throwing.
 *
 * <p>The list methods batch the user lookup with a single
 * {@code findAllById} per page to avoid the N+1 trap that the thin
 * {@code Follow} entity (no {@code @ManyToOne} refs) was designed to
 * sidestep. Page clamping is the controller's responsibility — see
 * {@code PageableSupport.clampPageable}; the service receives an
 * already-bounded {@link Pageable}.
 *
 * <p>Banned-user policy: not enforced here. The spec is silent on whether
 * a banned user can follow or appear in others' follow lists; the default
 * for v1 is to allow both. When the ban surface (#280) lands, gate at
 * either the {@link #follow} entry or the list-mapping step.
 */
@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    /**
     * Idempotent follow. Self-follow → {@link SelfFollowException} (mapped
     * to 400 by {@code GlobalExceptionHandler}); non-existent followee →
     * {@link ResourceNotFoundException} (mapped to 404). On success, the
     * returned {@link FollowResult#created} flag is {@code true} for a
     * fresh insert and {@code false} for an idempotent re-follow.
     */
    @Transactional
    public FollowResult follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new SelfFollowException("A user cannot follow themselves");
        }
        if (!userRepository.existsById(followeeId)) {
            throw new ResourceNotFoundException("User not found with id: " + followeeId);
        }
        int inserted = followRepository.upsertFollow(followerId, followeeId);
        return new FollowResult(followerId, followeeId, inserted == 1);
    }

    /**
     * Idempotent unfollow. No-op when the edge doesn't exist (Spring Data
     * {@code deleteById} is silent on missing). Method returns {@code void};
     * the controller responds with {@code 204} either way.
     */
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        followRepository.deleteById(new FollowId(followerId, followeeId));
    }

    /**
     * Paged list of users following {@code userId}, ordered by recency.
     *
     * <p>Mirrors the privacy gate of
     * {@link UserService#getProfileById(Long, Long)}: an admin's follow
     * graph is never exposed; a mentee cannot view another mentee's follow
     * graph (mentee→mentee enumeration would otherwise sneak past the
     * profile-detail and search rules). 404 propagates if either user id
     * does not resolve, so a request for "non-existent-user/followers"
     * doesn't silently return an empty page.
     *
     * <p>Defence-in-depth: if any admin row leaks into the graph (no
     * production flow puts one there, but {@code follow()} doesn't filter
     * by role), that admin entry is dropped from the surfaced page so
     * admin presence is never observable through this surface.
     */
    @Transactional(readOnly = true)
    public Page<User> listFollowers(Long userId, Long requesterId, Pageable pageable) {
        checkVisibility(userId, requesterId);
        Page<Follow> follows = followRepository
                .findByIdFolloweeIdOrderByCreatedAtDescIdFollowerIdDesc(userId, pageable);
        return resolveOtherSide(follows, f -> f.getId().getFollowerId());
    }

    /**
     * Paged list of users that {@code userId} is following, ordered by
     * recency. Same 404 + privacy semantics as {@link #listFollowers}.
     */
    @Transactional(readOnly = true)
    public Page<User> listFollowing(Long userId, Long requesterId, Pageable pageable) {
        checkVisibility(userId, requesterId);
        Page<Follow> follows = followRepository
                .findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(userId, pageable);
        return resolveOtherSide(follows, f -> f.getId().getFolloweeId());
    }

    public long countFollowers(Long userId) {
        return followRepository.countByIdFolloweeId(userId);
    }

    public long countFollowing(Long userId) {
        return followRepository.countByIdFollowerId(userId);
    }

    /**
     * Raises 404 if either user id doesn't resolve and applies the same
     * privacy rules as {@link UserService#getProfileById}: admin's graph
     * is never exposed; a mentee cannot view another mentee's graph.
     */
    private void checkVisibility(Long targetId, Long requesterId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + targetId));
        if (target instanceof Admin) {
            throw new ProfileNotVisibleException("Admin profile is not visible");
        }
        // The requester is the authenticated principal — we expect this lookup
        // to succeed; surface 404 if it doesn't (matches getProfileById's shape).
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requesterId));
        if (requester instanceof Mentee && target instanceof Mentee
                && !targetId.equals(requesterId)) {
            throw new ProfileNotVisibleException("Mentees cannot view other mentees' follow graph");
        }
    }

    /**
     * Maps a paged list of {@link Follow} edges to the {@link User} on the
     * other side, using a single {@code findAllById} for the whole page.
     * This is the move that keeps the thin-entity design free of N+1 reads.
     *
     * <p>Admin entries are stripped from the result content as
     * defence-in-depth: production flows do not place admins in the graph,
     * but if one ever appears, surfacing it here would expose admin
     * presence — inconsistent with {@link UserService#getProfileById}'s
     * outright admin block. Filtered entries reduce the visible page size
     * without re-paginating; the existing pagination contract already
     * tolerates short pages.
     */
    private Page<User> resolveOtherSide(Page<Follow> follows, Function<Follow, Long> otherSide) {
        Set<Long> ids = follows.getContent().stream()
                .map(otherSide)
                .collect(Collectors.toSet());
        Map<Long, User> usersById = userRepository.findAllById(ids).stream()
                .filter(u -> !(u instanceof Admin))
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // Drop nulls (admin entries filtered above) so the controller's
        // UserSummary::from never sees a null. totalElements stays as the
        // raw follow-row count — under-full pages are an accepted shape
        // (last page is always under-full anyway).
        List<User> resolved = follows.getContent().stream()
                .map(f -> usersById.get(otherSide.apply(f)))
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(resolved, follows.getPageable(), follows.getTotalElements());
    }
}
