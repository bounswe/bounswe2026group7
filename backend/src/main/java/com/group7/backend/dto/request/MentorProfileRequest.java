package com.group7.backend.dto.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileRequest {
    private String bio;
    private String field;
    private String expertise;
    private String affiliation;
    private List<String> interests;
    private Integer maxMenteeCapacity;
    private List<String> preferredMenteeSkills;
    private String preferredMenteeMajor;
    private String mentoringGoals;
    private Integer mentorshipDuration;
}
