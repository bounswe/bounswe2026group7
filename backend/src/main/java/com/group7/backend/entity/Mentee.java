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

    @ElementCollection
    @CollectionTable(name = "mentee_interests", joinColumns = @JoinColumn(name = "mentee_id"))
    @Column(name = "interest")
    private List<String> interests;

    private String careerInterest;

    @ElementCollection
    @CollectionTable(name = "mentee_skills", joinColumns = @JoinColumn(name = "mentee_id"))
    @Column(name = "skill")
    private List<String> skills;

    private String meetingFreqPref;

    private String backgroundInfo;

    @Column(nullable = false)
    private Integer cancelCount = 0;
}
