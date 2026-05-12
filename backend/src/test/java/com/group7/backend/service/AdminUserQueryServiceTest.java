package com.group7.backend.service;

import com.group7.backend.dto.request.AdminUserBanStatusFilter;
import com.group7.backend.dto.request.AdminUserRoleFilter;
import com.group7.backend.dto.response.AdminUserDetailResponse;
import com.group7.backend.dto.response.AdminUserListItem;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito tests for {@link AdminUserQueryService}. Verifies:
 * <ul>
 *   <li>filter-enum → repository-flag mapping (one Boolean.TRUE per role,
 *       rest null; ACTIVE/NONE → TRUE/FALSE for banActive)</li>
 *   <li>keyword normalisation via {@code SearchNormaliser.keyword} (inputs
 *       under 3 alphanumerics fall through as null)</li>
 *   <li>{@code getUserDetail} 404 path</li>
 *   <li>{@code getUserDetail} wire shape for Mentor / Mentee / Admin targets</li>
 * </ul>
 */
class AdminUserQueryServiceTest {

    private UserRepository userRepository;
    private BanService banService;
    private Clock fixedClock;
    private AdminUserQueryService service;

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        banService = mock(BanService.class);
        fixedClock = Clock.fixed(Instant.parse("2026-05-12T10:00:00Z"), ZoneOffset.UTC);
        service = new AdminUserQueryService(userRepository, banService, fixedClock);
    }

    private Page<AdminUserListItem> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private Mentor mentorEntity(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("M");
        m.setLastName("entor");
        m.setEmail("m" + id + "@example.com");
        m.setPasswordHash("$2a$dummy");
        m.setIsEmailVerified(true);
        m.setIsSuspectedBot(false);
        m.setCreatedAt(NOW.minusDays(3));
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        return m;
    }

    private Mentee menteeEntity(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("M");
        m.setLastName("entee");
        m.setEmail("e" + id + "@example.com");
        m.setPasswordHash("$2a$dummy");
        m.setIsEmailVerified(true);
        m.setIsSuspectedBot(false);
        m.setCreatedAt(NOW.minusDays(2));
        m.setProfileVisibility(true);
        return m;
    }

    private Admin adminEntity(Long id) {
        Admin a = new Admin();
        a.setId(id);
        a.setFirstName("A");
        a.setLastName("dmin");
        a.setEmail("a" + id + "@example.com");
        a.setPasswordHash("$2a$dummy");
        a.setIsEmailVerified(true);
        a.setIsSuspectedBot(false);
        a.setCreatedAt(NOW.minusDays(1));
        return a;
    }

    // ── listUsers: filter mapping ──────────────────────────────────────────

    @Test
    void listUsers_noFilters_allRepoFlagsNull() {
        when(userRepository.findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), isNull(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_roleMentor_setsOnlyMentorFlag() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(AdminUserRoleFilter.MENTOR, null, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                eq(Boolean.TRUE), isNull(), isNull(),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_roleMentee_setsOnlyMenteeFlag() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(AdminUserRoleFilter.MENTEE, null, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), eq(Boolean.TRUE), isNull(),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_roleAdmin_setsOnlyAdminFlag() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(AdminUserRoleFilter.ADMIN, null, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), eq(Boolean.TRUE),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_banStatusActive_setsBanActiveTrue() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, AdminUserBanStatusFilter.ACTIVE, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                eq(Boolean.TRUE), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_banStatusNone_setsBanActiveFalse() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, AdminUserBanStatusFilter.NONE, null, PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                eq(Boolean.FALSE), isNull(),
                eq(NOW), any(Pageable.class));
    }

    // ── listUsers: keyword normalisation ──────────────────────────────────

    @Test
    void listUsers_keywordShorterThan3Chars_normalisesToNull() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, "ab", PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_keywordBlank_normalisesToNull() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, "   ", PageRequest.of(0, 20));

        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), isNull(),
                eq(NOW), any(Pageable.class));
    }

    @Test
    void listUsers_keywordLowercasedAndWrappedWithWildcards() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, "  Ayse  ", PageRequest.of(0, 20));

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), keywordCaptor.capture(),
                eq(NOW), any(Pageable.class));
        assertThat(keywordCaptor.getValue()).isEqualTo("%ayse%");
    }

    @Test
    void listUsers_keywordEscapesWildcards() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, "ab%cd_ef", PageRequest.of(0, 20));

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findAdminUsers(
                isNull(), isNull(), isNull(),
                isNull(), keywordCaptor.capture(),
                eq(NOW), any(Pageable.class));
        // Literal % and _ are escape-prefixed; outer % wildcards remain unescaped.
        assertThat(keywordCaptor.getValue()).isEqualTo("%ab|%cd|_ef%");
    }

    // ── getUserDetail: 404 path ───────────────────────────────────────────

    @Test
    void getUserDetail_missing_throwsResourceNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserDetail(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(banService, never()).listBansForUser(any());
    }

    // ── getUserDetail: dispatch on subclass ───────────────────────────────

    @Test
    void getUserDetail_mentor_returnsMentorShapedProfile() {
        Mentor mentor = mentorEntity(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(banService.listBansForUser(1L)).thenReturn(List.of());

        AdminUserDetailResponse response = service.getUserDetail(1L);

        assertThat(response.getProfile().getClass().getSimpleName()).isEqualTo("MentorResponse");
        assertThat(response.getBanHistory()).isEmpty();
        assertThat(response.getSuspectedBot()).isFalse();
        assertThat(response.getSuspectedAt()).isNull();
    }

    @Test
    void getUserDetail_mentee_returnsMenteeShapedProfile() {
        Mentee mentee = menteeEntity(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(mentee));
        when(banService.listBansForUser(2L)).thenReturn(List.of());

        AdminUserDetailResponse response = service.getUserDetail(2L);

        assertThat(response.getProfile().getClass().getSimpleName()).isEqualTo("MenteeResponse");
    }

    @Test
    void getUserDetail_admin_returnsAdminShapedProfile() {
        Admin admin = adminEntity(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(banService.listBansForUser(3L)).thenReturn(List.of());

        AdminUserDetailResponse response = service.getUserDetail(3L);

        assertThat(response.getProfile().getClass().getSimpleName()).isEqualTo("AdminResponse");
    }

    @Test
    void getUserDetail_banHistoryMappedThroughBanService() {
        Mentor mentor = mentorEntity(5L);
        Ban ban = new Ban();
        ban.setId(11L);
        ban.setUser(mentor);
        ban.setReason("test");
        ban.setSource(BanSource.ADMIN);
        ban.setBanCount(1);
        ban.setExpiresAt(NOW.plusDays(7));

        when(userRepository.findById(5L)).thenReturn(Optional.of(mentor));
        when(banService.listBansForUser(5L)).thenReturn(List.of(ban));

        AdminUserDetailResponse response = service.getUserDetail(5L);

        assertThat(response.getBanHistory()).hasSize(1);
        assertThat(response.getBanHistory().get(0).getId()).isEqualTo(11L);
        assertThat(response.getBanHistory().get(0).getReason()).isEqualTo("test");
    }

    @Test
    void getUserDetail_returnsSuspectedBotMetadata() {
        Mentor mentor = mentorEntity(7L);
        mentor.setIsSuspectedBot(true);
        mentor.setSuspectedAt(NOW.minusHours(1));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(banService.listBansForUser(7L)).thenReturn(List.of());

        AdminUserDetailResponse response = service.getUserDetail(7L);

        assertThat(response.getSuspectedBot()).isTrue();
        assertThat(response.getSuspectedAt()).isEqualTo(NOW.minusHours(1));
    }

    // ── listUsers: clock plumbing ─────────────────────────────────────────

    @Test
    void listUsers_passesNowFromInjectedClock() {
        when(userRepository.findAdminUsers(
                any(), any(), any(), any(), any(),
                any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listUsers(null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(userRepository).findAdminUsers(
                any(), any(), any(), any(), any(),
                nowCaptor.capture(), any(Pageable.class));
        assertThat(nowCaptor.getValue()).isEqualTo(NOW);
    }
}
