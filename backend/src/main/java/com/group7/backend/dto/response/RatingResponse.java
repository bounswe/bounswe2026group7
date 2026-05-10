package com.group7.backend.dto.response;

import com.group7.backend.entity.MentorshipRating;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentorship rating record")
public class RatingResponse {

    @Schema(description = "Rating ID", example = "1")
    private Long id;

    @Schema(description = "Mentorship ID", example = "10")
    private Long mentorshipId;

    @Schema(description = "Rater user ID", example = "7")
    private Long raterUserId;

    @Schema(description = "Rated user ID", example = "42")
    private Long ratedUserId;

    @Schema(description = "Rating in stars (1-5)", example = "5")
    private int stars;

    @Schema(description = "Optional free-form comment")
    private String comment;

    @Schema(description = "Submission time")
    private OffsetDateTime createdAt;

    public static RatingResponse from(MentorshipRating r) {
        RatingResponse out = new RatingResponse();
        out.setId(r.getId());
        out.setMentorshipId(r.getMentorship().getId());
        out.setRaterUserId(r.getRater().getId());
        out.setRatedUserId(r.getRated().getId());
        out.setStars(r.getStars());
        out.setComment(r.getComment());
        out.setCreatedAt(r.getCreatedAt());
        return out;
    }
}
