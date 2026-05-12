package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "mentees")
@Getter
@Setter
@NoArgsConstructor
public class Mentee extends User {

    @Column(nullable = false)
    private Boolean profileVisibility = true;

    private String goals;

    private String major;
    private String majorUri;

    @ElementCollection
    @CollectionTable(name = "mentee_interests", joinColumns = @JoinColumn(name = "mentee_id"))
    @AttributeOverride(name = "label", column = @Column(name = "interest", nullable = false))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<TaggedTerm> interests;

    private String careerInterest;
    private String careerInterestUri;

    @ElementCollection
    @CollectionTable(name = "mentee_skills", joinColumns = @JoinColumn(name = "mentee_id"))
    @AttributeOverride(name = "label", column = @Column(name = "skill", nullable = false))
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<TaggedTerm> skills;

    private String meetingFreqPref;

    private String backgroundInfo;

    private String affiliation;

    @Column(nullable = false)
    private Integer cancelCount = 0;

    private Long activeMentorId;

    // See Mentor for the legacy-vs-entries accessor pattern.

    public List<TaggedTerm> getInterestEntries() {
        return interests;
    }

    public void setInterestEntries(List<TaggedTerm> entries) {
        this.interests = entries;
    }

    public List<String> getInterests() {
        return interests == null
                ? null
                : interests.stream().map(TaggedTerm::getLabel).toList();
    }

    public void setInterests(List<String> labels) {
        this.interests = TaggedTermLists.combine(labels, null);
    }

    public List<String> getInterestUris() {
        return interests == null
                ? null
                : interests.stream().map(TaggedTerm::getIdentifierUri).toList();
    }

    public List<TaggedTerm> getSkillEntries() {
        return skills;
    }

    public void setSkillEntries(List<TaggedTerm> entries) {
        this.skills = entries;
    }

    public List<String> getSkills() {
        return skills == null
                ? null
                : skills.stream().map(TaggedTerm::getLabel).toList();
    }

    public void setSkills(List<String> labels) {
        this.skills = TaggedTermLists.combine(labels, null);
    }

    public List<String> getSkillUris() {
        return skills == null
                ? null
                : skills.stream().map(TaggedTerm::getIdentifierUri).toList();
    }
}
