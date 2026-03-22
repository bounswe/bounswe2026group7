package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mentors")
@Getter
@Setter
@NoArgsConstructor
public class Mentor extends User {

    private String expertise;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;
}
