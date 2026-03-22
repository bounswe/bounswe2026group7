package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mentees")
@Getter
@Setter
@NoArgsConstructor
public class Mentee extends User {

    @Column(name = "learning_interest")
    private String learningInterest;

    @Column(name = "experience_level")
    private String experienceLevel;
}
