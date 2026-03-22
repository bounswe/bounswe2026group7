package com.group7.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MentorResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String profilePhoto;
    private Boolean isEmailVerified;
    private LocalDateTime createdAt;
    private String bio;
    private String field;
    private String expertise;
    private String affiliation;
    private List<String> interests;
    private Integer maxMenteeCapacity;
    private Integer currentMenteeCount;
    private List<String> preferredMenteeSkills;
    private String preferredMenteeMajor;
    private String mentoringGoals;
    private Integer mentorshipDuration;
}
