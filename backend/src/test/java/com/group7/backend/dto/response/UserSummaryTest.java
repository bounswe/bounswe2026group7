package com.group7.backend.dto.response;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct coverage for the {@link UserSummary#from(User)} role-discrimination
 * branches. The integration / service tests only exercise the Mentor and
 * Mentee paths because {@code FollowService} strips admin entries before
 * mapping; this class pins the Admin and bare-User fallbacks so the
 * defensive code stays correct under refactor.
 */
class UserSummaryTest {

    @Test
    void from_mentor_setsRoleMENTOR() {
        Mentor m = new Mentor();
        m.setId(1L);
        m.setFirstName("Ada");
        m.setLastName("L");

        UserSummary s = UserSummary.from(m);

        assertThat(s.getRole()).isEqualTo("MENTOR");
        assertThat(s.getId()).isEqualTo(1L);
        assertThat(s.getFirstName()).isEqualTo("Ada");
    }

    @Test
    void from_mentee_setsRoleMENTEE() {
        Mentee m = new Mentee();
        m.setId(2L);

        assertThat(UserSummary.from(m).getRole()).isEqualTo("MENTEE");
    }

    @Test
    void from_admin_setsRoleADMIN_evenThoughServiceFiltersThemFirst() {
        // Defence-in-depth: FollowService.resolveOtherSide drops admins
        // before mapping, but if a future caller passes an admin in by
        // mistake the DTO labels them honestly rather than as a generic
        // "USER" fallback.
        Admin a = new Admin();
        a.setId(3L);

        assertThat(UserSummary.from(a).getRole()).isEqualTo("ADMIN");
    }

}
