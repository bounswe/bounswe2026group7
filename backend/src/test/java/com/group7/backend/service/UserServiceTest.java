package com.group7.backend.service;

import com.group7.backend.dto.response.AdminResponse;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.ProfileResponse;
import com.group7.backend.dto.response.UserProfileResponse;
import com.group7.backend.dto.response.UserRatingSummary;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Service-layer Mockito coverage for {@link UserService}. Scoped to the
 * admin-self-view carve-out introduced for issue #553 plus the regression
 * that third-party admin lookups continue to surface 403.
 *
 * <p>Broader UserService behaviour is exercised by
 * {@code ProfileIntegrationTest} against a real Postgres; this slice is
 * the cheaper feedback loop for the targeted change.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MentorRepository mentorRepository;
    @Mock private MenteeRepository menteeRepository;
    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    @Mock private FollowRepository followRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MentorRatingService mentorRatingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    // Manually constructed because UserService now takes a primitive boolean
    // arg for the #570 mask flag — Mockito's @InjectMocks refuses to pick a
    // constructor that requires a primitive it can't auto-supply.
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, mentorRepository, menteeRepository,
                availabilitySlotRepository, menteeAvailabilitySlotRepository,
                followRepository, fileStorageService, mentorRatingService, eventPublisher,
                /*menteeMaskEnabled*/ true);
    }

    @Test
    void getOwnUserProfile_admin_returnsAdminResponseWithRoleADMIN() {
        Admin self = admin(42L, "Root", "Admin", "admin553@local.dev");
        when(userRepository.findById(42L)).thenReturn(Optional.of(self));
        when(followRepository.countByIdFolloweeId(42L)).thenReturn(0L);
        when(followRepository.countByIdFollowerId(42L)).thenReturn(0L);
        when(mentorRatingService.aggregateForMentor(42L))
                .thenReturn(new UserRatingSummary(null, 0L));

        UserProfileResponse result = userService.getOwnUserProfile(42L);

        assertThat(result.getProfile()).isInstanceOf(AdminResponse.class);
        AdminResponse profile = (AdminResponse) result.getProfile();
        assertThat(profile.getId()).isEqualTo(42L);
        assertThat(profile.getFirstName()).isEqualTo("Root");
        assertThat(profile.getLastName()).isEqualTo("Admin");
        assertThat(profile.getEmail()).isEqualTo("admin553@local.dev");
        assertThat(profile.getRole()).isEqualTo("ADMIN");
        assertThat(result.getFollowerCount()).isZero();
        assertThat(result.getFollowingCount()).isZero();
        // Self-view skips the follow-existence probe, so isFollowing is
        // false by construction — never call existsById for a self-view.
        assertThat(result.isFollowing()).isFalse();
    }

    @Test
    void getProfileById_thirdPartyAdminLookup_stillReturns403() {
        // Carve-out regression: the /me path is the only permitted route to
        // an admin's profile. Third-party lookup must still short-circuit
        // in getProfileById with ProfileNotVisibleException before the
        // mapping helper runs.
        Mentor requester = mentor(7L);
        Admin target = admin(42L, "Root", "Admin", "admin@local.dev");
        when(userRepository.findById(7L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(42L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getProfileById(42L, 7L))
                .isInstanceOf(ProfileNotVisibleException.class)
                .hasMessageContaining("Admin");
    }

    // ─── #570 redaction matrix ─────────────────────────────────────────────

    @Test
    void getProfileById_menteeViewsPrivateMentor_returns403() {
        Mentee viewer = mentee(1L);
        Mentor target = mentor(2L);
        target.setProfileVisibility(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getProfileById(2L, 1L))
                .isInstanceOf(ProfileNotVisibleException.class)
                .hasMessageContaining("private");
    }

    @Test
    void getProfileById_mentorViewsPrivateMentor_returns403() {
        Mentor viewer = mentor(1L);
        Mentor target = mentor(2L);
        target.setProfileVisibility(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getProfileById(2L, 1L))
                .isInstanceOf(ProfileNotVisibleException.class);
    }

    @Test
    void getProfileById_mentorViewsPrivateMentee_returns403() {
        Mentor viewer = mentor(1L);
        Mentee target = mentee(2L);
        target.setProfileVisibility(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getProfileById(2L, 1L))
                .isInstanceOf(ProfileNotVisibleException.class);
    }

    @Test
    void getProfileById_adminViewsPrivateMentor_returns200AndUnmasked() {
        Admin viewer = admin(1L, "Root", "Admin", "root@admin");
        Mentor target = mentor(2L);
        target.setFirstName("Mira");
        target.setLastName("Demir");
        target.setProfileVisibility(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ProfileResponse out = userService.getProfileById(2L, 1L);

        assertThat(out).isInstanceOf(MentorResponse.class);
        assertThat(((MentorResponse) out).getLastName()).isEqualTo("Demir");
    }

    @Test
    void getProfileById_mentorViewsVisibleMentee_masksLastNameAndPhoto() {
        // Mask flag is initialised by Spring's @Value; in this unit test
        // construct @InjectMocks won't populate it, so set it manually.
        ReflectionTestUtils.setField(userService, "menteeMaskEnabled", true);

        Mentor viewer = mentor(1L);
        Mentee target = mentee(2L);
        target.setFirstName("Ali");
        target.setLastName("Yilmaz");
        target.setProfilePhoto("https://example.com/photo.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ProfileResponse out = userService.getProfileById(2L, 1L);

        assertThat(out).isInstanceOf(MenteeResponse.class);
        MenteeResponse mr = (MenteeResponse) out;
        assertThat(mr.getFirstName()).isEqualTo("Ali");
        assertThat(mr.getLastName()).isNull();
        assertThat(mr.getProfilePhoto()).isNull();
    }

    @Test
    void getProfileById_mentorViewsMentee_maskFlagOff_noMasking() {
        ReflectionTestUtils.setField(userService, "menteeMaskEnabled", false);

        Mentor viewer = mentor(1L);
        Mentee target = mentee(2L);
        target.setFirstName("Ali");
        target.setLastName("Yilmaz");
        target.setProfilePhoto("https://example.com/photo.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ProfileResponse out = userService.getProfileById(2L, 1L);

        assertThat(out).isInstanceOf(MenteeResponse.class);
        MenteeResponse mr = (MenteeResponse) out;
        assertThat(mr.getLastName()).isEqualTo("Yilmaz");
        assertThat(mr.getProfilePhoto()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void getProfileById_self_evenIfPrivate_returns200() {
        Mentor target = mentor(7L);
        target.setProfileVisibility(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));

        ProfileResponse out = userService.getProfileById(7L, 7L);

        assertThat(out).isInstanceOf(MentorResponse.class);
    }

    @Test
    void getProfileById_menteeViewsOtherMentee_403_visibilityIrrelevant() {
        // The pre-existing 1.1.2.7 gate runs before the new #570 gate, so this
        // returns 403 even when the target's profileVisibility is true.
        Mentee viewer = mentee(1L);
        Mentee target = mentee(2L);
        target.setProfileVisibility(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.getProfileById(2L, 1L))
                .isInstanceOf(ProfileNotVisibleException.class)
                .hasMessageContaining("Mentees cannot view");
    }

    private static Mentee mentee(Long id) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setEmail("mentee" + id + "@local.dev");
        return m;
    }

    private static Admin admin(Long id, String firstName, String lastName, String email) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setEmail(email);
        return admin;
    }

    private static Mentor mentor(Long id) {
        Mentor mentor = new Mentor();
        mentor.setId(id);
        mentor.setEmail("mentor" + id + "@local.dev");
        return mentor;
    }
}
