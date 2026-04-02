package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentor profile response")
public final class MentorResponse extends UserResponse implements ProfileResponse {

    @Schema(description = "Short bio", example = "Experienced software engineer with 10+ years in industry")
    private String bio;

    @Schema(description = "Mentoring field", example = "Computer Science")
    private String field;

    @Schema(description = "Expertise summary", example = "Backend Development")
    private String expertise;

    @Schema(description = "Affiliation (university/company)", example = "Bogazici University")
    private String affiliation;

    @Schema(description = "List of interests", example = "[\"AI\", \"Systems\", \"Databases\"]")
    private List<String> interests;

    @Schema(description = "Maximum number of mentees", example = "3")
    private Integer maxMenteeCapacity;

    @Schema(description = "Current number of active mentees", example = "1")
    private Integer currentMenteeCount;

    @Schema(description = "Preferred mentee skills", example = "[\"Java\", \"Python\"]")
    private List<String> preferredMenteeSkills;

    @Schema(description = "Preferred mentee major", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @Schema(description = "Mentoring goals", example = "Help students with career guidance")
    private String mentoringGoals;

    @Schema(description = "Mentorship duration in months", example = "3")
    private Integer mentorshipDuration;

    public static MentorResponse from(Mentor mentor) {
        MentorResponse response = new MentorResponse();
        BeanUtils.copyProperties(mentor, response);
        response.setRole("MENTOR");
        return response;
    }
}
