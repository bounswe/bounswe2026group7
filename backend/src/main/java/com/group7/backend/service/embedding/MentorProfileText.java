package com.group7.backend.service.embedding;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;

/**
 * Compact, null-safe templates for the text that feeds the embedding
 * model. Lives outside the signal hierarchy because multiple consumers
 * need to derive the <em>same</em> string from a {@link Mentor} or
 * {@link Mentee} to produce the same cache key:
 *
 * <ul>
 *   <li>{@code SemanticMatchSignal} embeds these strings during scoring.</li>
 *   <li>{@code MentorScoringPipeline} re-derives them when computing
 *       embedding distances for the slot-5 diverse pick.</li>
 * </ul>
 *
 * <p>Owning the template here (rather than on one signal implementation)
 * keeps the strategy pattern clean: the pipeline no longer reaches into a
 * specific signal's static helper.
 */
public final class MentorProfileText {

    private MentorProfileText() {}

    /** Mentor side: goals → expertise → field. Skips blank/null segments. */
    public static String forMentor(Mentor m) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(m.getMentoringGoals())) sb.append("Mentoring goals: ").append(m.getMentoringGoals()).append(". ");
        if (notBlank(m.getExpertise()))      sb.append("Expertise: ").append(m.getExpertise()).append(". ");
        if (notBlank(m.getField()))          sb.append("Field: ").append(m.getField()).append(".");
        return sb.toString().strip();
    }

    /** Mentee side: goals → career interest → major. Skips blank/null segments. */
    public static String forMentee(Mentee me) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(me.getGoals()))          sb.append("Goals: ").append(me.getGoals()).append(". ");
        if (notBlank(me.getCareerInterest())) sb.append("Career interest: ").append(me.getCareerInterest()).append(". ");
        if (notBlank(me.getMajor()))          sb.append("Major: ").append(me.getMajor()).append(".");
        return sb.toString().strip();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
