package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.List;

@Entity
@Table(name = "mentors")
@Getter
@Setter
@NoArgsConstructor
public class Mentor extends User {

    /**
     * Public visibility flag mirroring {@link Mentee#getProfileVisibility()}.
     * Backed by V54 ({@code mentors.profile_visibility NOT NULL DEFAULT TRUE});
     * existing rows default to {@code true}, so public-by-default mentors keep
     * their pre-#570 behaviour. When set to {@code false}, the privacy gate in
     * {@code UserService.getProfileById} returns 403 to every viewer except
     * the owner and admins, and the list/search/matching endpoints exclude
     * the mentor row at SQL level via the {@code :bypassVisibility} parameter.
     */
    @Column(nullable = false)
    private Boolean profileVisibility = true;

    private String bio;

    private String field;
    private String fieldUri;

    private String expertise;
    private String expertiseUri;

    private String affiliation;

    @ElementCollection
    @CollectionTable(name = "mentor_interests", joinColumns = @JoinColumn(name = "mentor_id"))
    @AttributeOverride(name = "label", column = @Column(name = "interest", nullable = false))
    @Fetch(FetchMode.SUBSELECT)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<TaggedTerm> interests;

    @Column(nullable = false)
    private Integer maxMenteeCapacity = 0;

    @Column(nullable = false)
    private Integer currentMenteeCount = 0;

    @ElementCollection
    @CollectionTable(name = "mentor_preferred_mentee_skills", joinColumns = @JoinColumn(name = "mentor_id"))
    @AttributeOverride(name = "label", column = @Column(name = "skill", nullable = false))
    @Fetch(FetchMode.SUBSELECT)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<TaggedTerm> preferredMenteeSkills;

    private String preferredMenteeMajor;
    private String preferredMenteeMajorUri;

    private String mentoringGoals;

    private Integer mentorshipDuration;

    // Element-collection accessors. The underlying storage is List<TaggedTerm>
    // (label + optional canonical URI). Two access shapes are exposed:
    //   getXxxEntries() — full TaggedTerm list, used by URI-aware code paths.
    //   getXxx()        — labels only, kept for legacy callers and BeanUtils
    //                     property-name copying into label-only response DTOs.

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

    public List<TaggedTerm> getPreferredMenteeSkillEntries() {
        return preferredMenteeSkills;
    }

    public void setPreferredMenteeSkillEntries(List<TaggedTerm> entries) {
        this.preferredMenteeSkills = entries;
    }

    public List<String> getPreferredMenteeSkills() {
        return preferredMenteeSkills == null
                ? null
                : preferredMenteeSkills.stream().map(TaggedTerm::getLabel).toList();
    }

    public void setPreferredMenteeSkills(List<String> labels) {
        this.preferredMenteeSkills = TaggedTermLists.combine(labels, null);
    }

    public List<String> getPreferredMenteeSkillUris() {
        return preferredMenteeSkills == null
                ? null
                : preferredMenteeSkills.stream().map(TaggedTerm::getIdentifierUri).toList();
    }
}
