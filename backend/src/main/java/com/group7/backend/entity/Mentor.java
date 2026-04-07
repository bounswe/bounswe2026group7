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

    private String bio;

    private String field;

    private String expertise;

    private String affiliation;

    @ElementCollection
    @CollectionTable(name = "mentor_interests", joinColumns = @JoinColumn(name = "mentor_id"))
    @Column(name = "interest")
    @Fetch(FetchMode.SUBSELECT)
    private List<String> interests;

    @Column(nullable = false)
    private Integer maxMenteeCapacity = 0;

    @Column(nullable = false)
    private Integer currentMenteeCount = 0;

    @ElementCollection
    @CollectionTable(name = "mentor_preferred_mentee_skills", joinColumns = @JoinColumn(name = "mentor_id"))
    @Column(name = "skill")
    @Fetch(FetchMode.SUBSELECT)
    private List<String> preferredMenteeSkills;

    private String preferredMenteeMajor;

    private String mentoringGoals;

    private Integer mentorshipDuration;
}
