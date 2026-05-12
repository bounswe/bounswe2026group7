package com.group7.backend.repository;

import com.group7.backend.dto.response.AdminUserListItem;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.BanSource;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres SQL-fit coverage for
 * {@link UserRepository#findAdminUsers}. JPQL relies on Hibernate's
 * JOINED-inheritance {@code type(u) = ...} discriminator + EXISTS
 * subqueries on the bans table; H2's PostgreSQL compatibility mode does
 * not always mirror Postgres semantics there, so this test pins to the
 * {@code test} profile (Postgres on 5433 by default; CI dry-run uses
 * 5497 via env override).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryAdminListTest {

    @Autowired private UserRepository userRepository;
    @Autowired private BanRepository banRepository;

    @PersistenceContext
    private EntityManager em;

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void cleanDb() {
        banRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private Mentor mentor(String suffix) {
        Mentor m = new Mentor();
        m.setFirstName("First-" + suffix);
        m.setLastName("Last-" + suffix);
        m.setEmail("mentor-" + suffix + "@example.com");
        m.setPasswordHash("$2a$dummy");
        m.setIsEmailVerified(true);
        m.setMaxMenteeCapacity(3);
        m.setCurrentMenteeCount(0);
        return m;
    }

    private Mentee mentee(String suffix) {
        Mentee m = new Mentee();
        m.setFirstName("First-" + suffix);
        m.setLastName("Last-" + suffix);
        m.setEmail("mentee-" + suffix + "@example.com");
        m.setPasswordHash("$2a$dummy");
        m.setIsEmailVerified(true);
        m.setProfileVisibility(true);
        return m;
    }

    private Admin admin(String suffix) {
        Admin a = new Admin();
        a.setFirstName("Adm");
        a.setLastName("In-" + suffix);
        a.setEmail("admin-" + suffix + "@example.com");
        a.setPasswordHash("$2a$dummy");
        a.setIsEmailVerified(true);
        return a;
    }

    private <T extends User> T persist(T user) {
        em.persist(user);
        em.flush();
        return user;
    }

    private Ban persistBan(User user, OffsetDateTime expiresAt, OffsetDateTime liftedAt) {
        Ban b = new Ban();
        b.setUser(user);
        b.setReason("test");
        b.setSource(BanSource.ADMIN);
        b.setBanCount(1);
        b.setExpiresAt(expiresAt);
        b.setLiftedAt(liftedAt);
        em.persist(b);
        em.flush();
        return b;
    }

    // ── tests ─────────────────────────────────────────────────────────────

    @Test
    void noFilters_returnsAllUsersIncludingAdmins() {
        Mentor m1 = persist(mentor("a"));
        Mentee me1 = persist(mentee("b"));
        Admin a1 = persist(admin("c"));

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactlyInAnyOrder(m1.getId(), me1.getId(), a1.getId());
        assertThat(page.getContent()).extracting(AdminUserListItem::getRole)
                .containsExactlyInAnyOrder("MENTOR", "MENTEE", "ADMIN");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void roleFilter_mentor_returnsOnlyMentors() {
        Mentor m1 = persist(mentor("a"));
        persist(mentee("b"));
        persist(admin("c"));

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                Boolean.TRUE, null, null, null, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(m1.getId());
        assertThat(page.getContent()).extracting(AdminUserListItem::getRole)
                .containsExactly("MENTOR");
    }

    @Test
    void roleFilter_mentee_returnsOnlyMentees() {
        persist(mentor("a"));
        Mentee me1 = persist(mentee("b"));
        persist(admin("c"));

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, Boolean.TRUE, null, null, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(me1.getId());
        assertThat(page.getContent()).extracting(AdminUserListItem::getRole)
                .containsExactly("MENTEE");
    }

    @Test
    void roleFilter_admin_returnsOnlyAdmins() {
        persist(mentor("a"));
        persist(mentee("b"));
        Admin a1 = persist(admin("c"));

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, null, Boolean.TRUE, null, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(a1.getId());
        assertThat(page.getContent()).extracting(AdminUserListItem::getRole)
                .containsExactly("ADMIN");
    }

    @Test
    void banStatusActive_excludesExpiredAndLifted_includesOnlyCurrentlyBanned() {
        Mentor freshMentor = persist(mentor("fresh"));

        Mentor activelyBanned = persist(mentor("activelyBanned"));
        persistBan(activelyBanned, NOW.plusDays(7), null);  // active

        Mentor expiredBan = persist(mentor("expiredBan"));
        persistBan(expiredBan, NOW.minusDays(1), null);     // expired

        Mentor liftedBan = persist(mentor("liftedBan"));
        persistBan(liftedBan, NOW.plusDays(7), NOW.minusHours(2)); // lifted

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, null, null, Boolean.TRUE, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(activelyBanned.getId());
        assertThat(page.getContent()).extracting(AdminUserListItem::getBanStatus)
                .containsExactly("ACTIVE");

        // Sanity: when filter is omitted, freshMentor / expiredBan / liftedBan
        // all surface with banStatus=NONE (the EXISTS predicate doesn't see
        // their rows under the active-ban definition).
        Page<AdminUserListItem> all = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(0, 20));
        assertThat(all.getContent()).extracting(
                AdminUserListItem::getId, AdminUserListItem::getBanStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(freshMentor.getId(), "NONE"),
                        org.assertj.core.groups.Tuple.tuple(activelyBanned.getId(), "ACTIVE"),
                        org.assertj.core.groups.Tuple.tuple(expiredBan.getId(), "NONE"),
                        org.assertj.core.groups.Tuple.tuple(liftedBan.getId(), "NONE"));
    }

    @Test
    void banStatusNone_includesLiftedAndExpiredAndUnbanned() {
        Mentor freshMentor = persist(mentor("fresh"));
        Mentor activelyBanned = persist(mentor("activelyBanned"));
        persistBan(activelyBanned, NOW.plusDays(7), null);
        Mentor expiredBan = persist(mentor("expiredBan"));
        persistBan(expiredBan, NOW.minusDays(1), null);
        Mentor liftedBan = persist(mentor("liftedBan"));
        persistBan(liftedBan, NOW.plusDays(7), NOW.minusHours(2));

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, null, null, Boolean.FALSE, null, NOW, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactlyInAnyOrder(
                        freshMentor.getId(), expiredBan.getId(), liftedBan.getId())
                .doesNotContain(activelyBanned.getId());
    }

    @Test
    void keywordFilter_matchesFirstNameLastNameAndEmail() {
        Mentor ayse = persist(mentor("ayse"));
        ayse.setFirstName("Ayse");
        ayse.setLastName("Demir");
        em.flush();

        Mentor ali = persist(mentor("ali"));
        ali.setFirstName("Ali");
        ali.setLastName("Yilmaz");
        em.flush();

        Mentee zeynep = persist(mentee("zeynep"));
        zeynep.setFirstName("Zeynep");
        zeynep.setLastName("Kara");
        em.flush();

        // firstName match
        Page<AdminUserListItem> byFirst = userRepository.findAdminUsers(
                null, null, null, null, "%ayse%", NOW, PageRequest.of(0, 20));
        assertThat(byFirst.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(ayse.getId());

        // lastName match (case-insensitive)
        Page<AdminUserListItem> byLast = userRepository.findAdminUsers(
                null, null, null, null, "%yilmaz%", NOW, PageRequest.of(0, 20));
        assertThat(byLast.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(ali.getId());

        // email match
        Page<AdminUserListItem> byEmail = userRepository.findAdminUsers(
                null, null, null, null, "%zeynep%", NOW, PageRequest.of(0, 20));
        assertThat(byEmail.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(zeynep.getId());
    }

    @Test
    void pagination_secondPageReturnsRemainingUsers() {
        // Seed 5 users
        List<Mentor> seeded = List.of(
                persist(mentor("p1")),
                persist(mentor("p2")),
                persist(mentor("p3")),
                persist(mentor("p4")),
                persist(mentor("p5")));

        Page<AdminUserListItem> first = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(0, 2));
        Page<AdminUserListItem> second = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(1, 2));
        Page<AdminUserListItem> third = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(2, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(2);
        assertThat(third.getContent()).hasSize(1);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);

        // Combined page contents cover the full set with no overlap.
        assertThat(List.of(
                first.getContent().get(0).getId(),
                first.getContent().get(1).getId(),
                second.getContent().get(0).getId(),
                second.getContent().get(1).getId(),
                third.getContent().get(0).getId()))
                .containsExactlyInAnyOrderElementsOf(
                        seeded.stream().map(User::getId).toList());
    }

    @Test
    void combinedFilters_roleAndBanStatusAndKeyword() {
        Mentor ayseBanned = persist(mentor("ayseBanned"));
        ayseBanned.setFirstName("Ayse");
        ayseBanned.setLastName("Banned");
        em.flush();
        persistBan(ayseBanned, NOW.plusDays(3), null);

        Mentor ayseClean = persist(mentor("ayseClean"));
        ayseClean.setFirstName("Ayse");
        ayseClean.setLastName("Clean");
        em.flush();

        Mentee ayseMentee = persist(mentee("ayseMentee"));
        ayseMentee.setFirstName("Ayse");
        ayseMentee.setLastName("Mentee");
        em.flush();
        persistBan(ayseMentee, NOW.plusDays(3), null);

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                Boolean.TRUE, null, null,  // role = MENTOR
                Boolean.TRUE,              // active ban
                "%ayse%",
                NOW,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AdminUserListItem::getId)
                .containsExactly(ayseBanned.getId());
    }

    @Test
    void projection_populatesAllFields() {
        Mentor m = persist(mentor("proj"));
        m.setFirstName("Proj");
        m.setLastName("ection");
        m.setIsSuspectedBot(true);
        em.flush();

        Page<AdminUserListItem> page = userRepository.findAdminUsers(
                null, null, null, null, null, NOW, PageRequest.of(0, 20));

        AdminUserListItem item = page.getContent().get(0);
        assertThat(item.getId()).isEqualTo(m.getId());
        assertThat(item.getFirstName()).isEqualTo("Proj");
        assertThat(item.getLastName()).isEqualTo("ection");
        assertThat(item.getEmail()).isEqualTo(m.getEmail());
        assertThat(item.getRole()).isEqualTo("MENTOR");
        assertThat(item.getBanStatus()).isEqualTo("NONE");
        assertThat(item.getSuspectedBot()).isTrue();
        assertThat(item.getCreatedAt()).isNotNull();
    }
}
