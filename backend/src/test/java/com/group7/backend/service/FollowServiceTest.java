package com.group7.backend.service;

import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.exception.SelfFollowException;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FollowService}'s control flow with mocked
 * repositories. The behaviours under test are:
 * <ol>
 *   <li>self-follow rejection raises {@link SelfFollowException} before any
 *       repository touch;</li>
 *   <li>non-existent followee raises {@link ResourceNotFoundException}
 *       before the upsert;</li>
 *   <li>{@code created=true} on a fresh insert (upsert returns 1);</li>
 *   <li>{@code created=false} on a duplicate (upsert returns 0) without
 *       any exception path;</li>
 *   <li>{@link FollowService#unfollow} delegates to {@code deleteById} and
 *       returns silently regardless of edge presence;</li>
 *   <li>list methods batch-fetch users with a single {@code findAllById}
 *       so the thin-entity design stays N+1-free.</li>
 * </ol>
 *
 * <p>The DB-side semantics (CHECK constraint, FK CASCADE, ON CONFLICT
 * mechanics) are covered by {@code FollowRepositoryTest} against real
 * Postgres; this class stays at the service-layer abstraction.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private FollowService followService;

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long CAROL = 3L;

    // ── follow() ─────────────────────────────────────────────────────────────

    @Test
    void follow_rejectsSelfFollow_withoutTouchingRepositories() {
        assertThatThrownBy(() -> followService.follow(ALICE, ALICE))
                .isInstanceOf(SelfFollowException.class)
                .hasMessageContaining("cannot follow themselves");

        verify(userRepository, never()).existsById(anyLong());
        verify(followRepository, never()).upsertFollow(anyLong(), anyLong());
    }

    @Test
    void follow_returns404_whenFolloweeMissing() {
        when(userRepository.existsById(BOB)).thenReturn(false);

        assertThatThrownBy(() -> followService.follow(ALICE, BOB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(BOB.toString());

        verify(followRepository, never()).upsertFollow(anyLong(), anyLong());
    }

    @Test
    void follow_returnsCreatedTrue_whenUpsertReturnsOne() {
        when(userRepository.existsById(BOB)).thenReturn(true);
        when(followRepository.upsertFollow(ALICE, BOB)).thenReturn(1);

        FollowResult result = followService.follow(ALICE, BOB);

        assertThat(result.followerId()).isEqualTo(ALICE);
        assertThat(result.followeeId()).isEqualTo(BOB);
        assertThat(result.created()).isTrue();
    }

    @Test
    void follow_returnsCreatedFalse_whenUpsertReturnsZero() {
        when(userRepository.existsById(BOB)).thenReturn(true);
        when(followRepository.upsertFollow(ALICE, BOB)).thenReturn(0);

        FollowResult result = followService.follow(ALICE, BOB);

        assertThat(result.created()).isFalse();
        assertThat(result.followerId()).isEqualTo(ALICE);
        assertThat(result.followeeId()).isEqualTo(BOB);
    }

    // ── unfollow() ───────────────────────────────────────────────────────────

    @Test
    void unfollow_delegatesToDeleteById_returnsVoid() {
        followService.unfollow(ALICE, BOB);

        verify(followRepository).deleteById(new FollowId(ALICE, BOB));
    }

    @Test
    void unfollow_doesNotThrow_evenIfRepositoryIsCalledOnMissingEdge() {
        // Spring Data 3.x deleteById is silent on missing; the service does
        // not need a guard, and the controller reflects this with a 204.
        followService.unfollow(ALICE, BOB);  // no exception

        verify(followRepository).deleteById(any(FollowId.class));
    }

    // ── listFollowers / listFollowing ────────────────────────────────────────

    @Test
    void listFollowers_returns404_whenUserMissing() {
        when(userRepository.existsById(ALICE)).thenReturn(false);

        assertThatThrownBy(() -> followService.listFollowers(ALICE, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(followRepository, never())
                .findByIdFolloweeIdOrderByCreatedAtDescIdFollowerIdDesc(anyLong(), any());
    }

    @Test
    void listFollowers_batchFetchesUsersWithSingleFindAllById() {
        Pageable page = PageRequest.of(0, 10);
        Mentee bob = mentee(BOB);
        Mentee carol = mentee(CAROL);
        Page<Follow> follows = new PageImpl<>(List.of(
                follow(BOB, ALICE),
                follow(CAROL, ALICE)
        ), page, 2);

        when(userRepository.existsById(ALICE)).thenReturn(true);
        when(followRepository.findByIdFolloweeIdOrderByCreatedAtDescIdFollowerIdDesc(ALICE, page))
                .thenReturn(follows);
        when(userRepository.findAllById(any())).thenReturn(List.of(bob, carol));

        Page<User> result = followService.listFollowers(ALICE, page);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(User::getId).containsExactly(BOB, CAROL);
        // Single batch lookup — the design move that avoids N+1.
        verify(userRepository).findAllById(any());
    }

    @Test
    void listFollowing_resolvesFolloweeIdsRatherThanFollowerIds() {
        Pageable page = PageRequest.of(0, 10);
        Mentee bob = mentee(BOB);
        Mentee carol = mentee(CAROL);
        Page<Follow> follows = new PageImpl<>(List.of(
                follow(ALICE, BOB),
                follow(ALICE, CAROL)
        ), page, 2);

        when(userRepository.existsById(ALICE)).thenReturn(true);
        when(followRepository.findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(ALICE, page))
                .thenReturn(follows);
        when(userRepository.findAllById(any())).thenReturn(List.of(bob, carol));

        Page<User> result = followService.listFollowing(ALICE, page);

        assertThat(result.getContent()).extracting(User::getId).containsExactly(BOB, CAROL);
    }

    // ── counts ───────────────────────────────────────────────────────────────

    @Test
    void countFollowers_delegatesToCountByIdFolloweeId() {
        when(followRepository.countByIdFolloweeId(ALICE)).thenReturn(7L);

        assertThat(followService.countFollowers(ALICE)).isEqualTo(7L);
    }

    @Test
    void countFollowing_delegatesToCountByIdFollowerId() {
        when(followRepository.countByIdFollowerId(ALICE)).thenReturn(3L);

        assertThat(followService.countFollowing(ALICE)).isEqualTo(3L);
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    @BeforeEach
    void resetState() {
        // @InjectMocks gives a fresh service per test; nothing else to do.
    }

    private static Mentee mentee(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("U" + id);
        m.setLastName("L");
        m.setEmail("u" + id + "@ex.com");
        return m;
    }

    private static Follow follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }
}
