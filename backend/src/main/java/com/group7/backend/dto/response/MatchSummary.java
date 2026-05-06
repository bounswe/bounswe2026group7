package com.group7.backend.dto.response;

/**
 * Common shape for match-list response DTOs. Both {@link MentorMatchResponse}
 * (mentor's view of a candidate match) and {@link MenteeCandidateResponse}
 * (mentor's view of a candidate mentee) implement this interface so the
 * scheduled match-notification processor (#273) can run a single generic
 * change-detection algorithm against either side without casts.
 *
 * <p>Both methods are already exposed by Lombok's {@code @Getter} on the
 * concrete DTOs — declaring this interface only documents the contract
 * those getters already satisfy.
 */
public interface MatchSummary {

    /** The matched user's id; non-null on a well-formed DTO. */
    Long getId();

    /** The matched user's first name; non-null on a well-formed DTO. */
    String getFirstName();
}
