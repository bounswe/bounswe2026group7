package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentor profile payload")
public class MentorProfileRequest {
    @Schema(description = "Short bio")
    private String bio;

    @Schema(description = "Mentoring field")
    private String field;

    @Schema(description = "Expertise summary")
    private String expertise;

    @Schema(description = "Affiliation (university/company)")
    private String affiliation;

    @Schema(description = "Interests")
    private List<String> interests;

    @Schema(description = "Maximum number of mentees")
    private Integer maxMenteeCapacity;

    @Schema(description = "Preferred mentee skills")
    private List<String> preferredMenteeSkills;

    @Schema(description = "Preferred mentee major")
    private String preferredMenteeMajor;

    @Schema(description = "Mentoring goals")
    private String mentoringGoals;

    @Schema(description = "Mentorship duration (e.g., weeks/months)")
    private Integer mentorshipDuration;
}
