package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial profile update payload. Only non-null fields are applied. "
        + "Mentor-specific fields are ignored for mentees and vice versa.")
public class UpdateProfileRequest {

    // ── Common fields ───────────────────────────────────────

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Schema(description = "First name", example = "Bora")
    private String firstName;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Schema(description = "Last name", example = "Sarioglu")
    private String lastName;

    // profilePhoto is set exclusively via POST /api/users/me/photo

    // ── Mentor-specific fields ──────────────────────────────

    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    @Schema(description = "Short bio (mentor)", example = "Experienced software engineer with 10+ years in industry")
    private String bio;

    @Schema(description = "Mentoring field (mentor)", example = "Computer Science")
    private String field;

    @Schema(description = "Expertise summary (mentor)", example = "Backend Development")
    private String expertise;

    @Schema(description = "Affiliation (mentor)", example = "Bogazici University")
    private String affiliation;

    @Schema(description = "Interests (shared by both roles)", example = "[\"AI\", \"Systems\"]")
    private List<String> interests;

    @Min(value = 0, message = "Max mentee capacity must be at least 0")
    @Schema(description = "Maximum number of mentees (mentor). 0 = temporarily not accepting.", example = "3")
    private Integer maxMenteeCapacity;

    @Schema(description = "Preferred mentee skills (mentor)", example = "[\"Java\", \"Python\"]")
    private List<String> preferredMenteeSkills;

    @Schema(description = "Preferred mentee major (mentor)", example = "Computer Engineering")
    private String preferredMenteeMajor;

    @Schema(description = "Mentoring goals (mentor)", example = "Help students with career guidance")
    private String mentoringGoals;

    @Min(value = 1, message = "Mentorship duration must be at least 1")
    @Schema(description = "Mentorship duration in months (mentor)", example = "3")
    private Integer mentorshipDuration;

    // ── Mentee-specific fields ──────────────────────────────

    @Schema(description = "Profile visibility (mentee). false = private.", example = "true")
    private Boolean profileVisibility;

    @Schema(description = "Learning goals (mentee)", example = "Learn backend development")
    private String goals;

    @Schema(description = "Major / field of study (mentee)", example = "Computer Engineering")
    private String major;

    @Schema(description = "Career interest (mentee)", example = "Data Science")
    private String careerInterest;

    @Schema(description = "Skills (mentee)", example = "[\"Python\", \"SQL\"]")
    private List<String> skills;

    @Schema(description = "Meeting frequency preference (mentee)", example = "Weekly")
    private String meetingFreqPref;

    @Schema(description = "Background information (mentee)", example = "2nd year student interested in AI")
    private String backgroundInfo;
}
