package com.group7.backend.service.embedding;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentorProfileTextTest {

    @Test
    void mentor_emitsAllSectionsWhenPresent() {
        var m = new Mentor();
        m.setMentoringGoals("g"); m.setExpertise("e"); m.setField("f");
        String text = MentorProfileText.forMentor(m);
        assertThat(text).contains("Mentoring goals: g")
                .contains("Expertise: e")
                .contains("Field: f");
    }

    @Test
    void mentor_skipsBlankAndNullFields() {
        var m = new Mentor();
        m.setExpertise("react");
        m.setField("Software Engineering");
        String text = MentorProfileText.forMentor(m);
        assertThat(text).contains("Expertise: react")
                .contains("Field: Software Engineering")
                .doesNotContain("Mentoring goals");
    }

    @Test
    void mentor_emptyWhenAllFieldsBlank() {
        var m = new Mentor();
        m.setMentoringGoals("");
        m.setExpertise(null);
        m.setField("  ");
        assertThat(MentorProfileText.forMentor(m)).isEmpty();
    }

    @Test
    void mentee_emitsAllSectionsWhenPresent() {
        var me = new Mentee();
        me.setGoals("gg"); me.setCareerInterest("ci"); me.setMajor("ma");
        String text = MentorProfileText.forMentee(me);
        assertThat(text).contains("Goals: gg")
                .contains("Career interest: ci")
                .contains("Major: ma");
    }

    @Test
    void mentee_skipsBlankAndNullFields() {
        var me = new Mentee();
        me.setGoals("learn rust");
        String text = MentorProfileText.forMentee(me);
        assertThat(text).contains("Goals: learn rust")
                .doesNotContain("Major")
                .doesNotContain("Career interest");
    }

    @Test
    void mentee_emptyWhenAllFieldsBlank() {
        var me = new Mentee();
        assertThat(MentorProfileText.forMentee(me)).isEmpty();
    }
}
