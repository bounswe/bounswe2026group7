package com.group7.backend.dto.response;

import com.group7.backend.entity.MentorRating;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentor rating submitted by a mentee (#237).")
public class MentorRatingResponse {

    @Schema(description = "Rating id", example = "42")
    private Long id;

    @Schema(description = "Mentorship id this rating belongs to", example = "100")
    private Long mentorshipId;

    @Schema(description = "Rated mentor user id", example = "1")
    private Long mentorId;

    @Schema(description = "Submitting mentee user id", example = "2")
    private Long menteeId;

    @Schema(description = "Score from 1 to 5", example = "5")
    private Integer score;

    @Schema(description = "Optional comment", example = "Great mentor!")
    private String comment;

    @Schema(description = "When the rating was submitted")
    private OffsetDateTime createdAt;

    public static MentorRatingResponse from(MentorRating rating) {
        MentorRatingResponse r = new MentorRatingResponse();
        r.setId(rating.getId());
        r.setMentorshipId(rating.getMentorshipId());
        r.setMentorId(rating.getMentorId());
        r.setMenteeId(rating.getMenteeId());
        r.setScore(rating.getScore());
        r.setComment(rating.getComment());
        r.setCreatedAt(rating.getCreatedAt());
        return r;
    }
}
