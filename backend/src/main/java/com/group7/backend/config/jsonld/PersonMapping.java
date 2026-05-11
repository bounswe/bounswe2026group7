package com.group7.backend.config.jsonld;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.UserResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PersonMapping implements JsonLdMapping {

    private final String baseUrl;

    public PersonMapping(AppProperties appProperties) {
        String configured = appProperties.getBaseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    @Override
    public boolean supports(Class<?> bodyType) {
        return UserResponse.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        UserResponse user = (UserResponse) body;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@context", JsonLdContext.SCHEMA_ORG);
        result.put("@type", "Person");
        if (user.getId() != null) {
            result.put("@id", baseUrl + "/api/users/" + user.getId());
        }
        putIfPresent(result, "givenName", user.getFirstName());
        putIfPresent(result, "familyName", user.getLastName());
        putIfPresent(result, "email", user.getEmail());
        putIfPresent(result, "image", user.getProfilePhoto());

        // schema.org/description is intentionally freeform prose for both roles
        // (bio for mentors, backgroundInfo for mentees). Consumers disambiguate
        // Mentor vs Mentee via @type plus role-specific fields.
        if (user instanceof MentorResponse mentor) {
            putIfPresent(result, "description", mentor.getBio());
            putIfPresent(result, "jobTitle", mentor.getField());
            putIfPresent(result, "affiliation", mentor.getAffiliation());

            // knowsAbout includes the mentor's expertise plus every interest
            // and preferred-mentee-skill the mentor has tagged. Each entry is
            // a plain string when no URI is set, or a JSON-LD node with @id
            // when the user picked the value from autocomplete. Deduplication
            // key: identifierUri when present, label otherwise — so {ML, uri=X}
            // and {Machine learning, uri=X} collapse to one entry. The
            // standalone field / preferredMenteeMajor are only added when
            // they carry a URI; without one they remain free-text labels
            // exposed via jobTitle / domain-specific fields, not knowsAbout.
            Map<String, Object> deduped = new LinkedHashMap<>();
            addEntry(deduped, mentor.getExpertise(), mentor.getExpertiseUri());
            addCollection(deduped, mentor.getInterests(), mentor.getInterestUris());
            addCollection(deduped, mentor.getPreferredMenteeSkills(),
                    mentor.getPreferredMenteeSkillUris());
            addEntryIfTagged(deduped, mentor.getField(), mentor.getFieldUri());
            addEntryIfTagged(deduped, mentor.getPreferredMenteeMajor(),
                    mentor.getPreferredMenteeMajorUri());
            if (!deduped.isEmpty()) {
                result.put("knowsAbout", new ArrayList<>(deduped.values()));
            }
        } else if (user instanceof MenteeResponse mentee) {
            putIfPresent(result, "description", mentee.getBackgroundInfo());

            Map<String, Object> deduped = new LinkedHashMap<>();
            addCollection(deduped, mentee.getInterests(), mentee.getInterestUris());
            addCollection(deduped, mentee.getSkills(), mentee.getSkillUris());
            addEntryIfTagged(deduped, mentee.getCareerInterest(),
                    mentee.getCareerInterestUri());
            addEntryIfTagged(deduped, mentee.getMajor(), mentee.getMajorUri());
            if (!deduped.isEmpty()) {
                result.put("knowsAbout", new ArrayList<>(deduped.values()));
            }
        }

        return result;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        map.put(key, value);
    }

    /**
     * Adds a (label, optional URI) pair to the deduplicated knowsAbout map.
     * Dedup key is the URI when present, otherwise the label. The first
     * occurrence wins, so a later entry that would map to the same key is
     * dropped — including the case where the same label appears twice
     * (once tagged, once free-text) since the URI version is added first
     * by the caller's ordering of fields.
     */
    private static void addEntry(Map<String, Object> deduped, String label, String uri) {
        if (label == null || label.isBlank()) {
            return;
        }
        boolean hasUri = uri != null && !uri.isBlank();
        String key = hasUri ? uri : label;
        if (deduped.containsKey(key)) {
            return;
        }
        if (hasUri) {
            Map<String, String> node = new LinkedHashMap<>();
            node.put("@id", uri);
            node.put("name", label);
            deduped.put(key, node);
        } else {
            deduped.put(key, label);
        }
    }

    /**
     * Adds a (label, URI) pair only when the URI is present. Keeps free-text
     * profile fields (mentor.field, mentee.major, mentee.careerInterest,
     * mentor.preferredMenteeMajor) out of knowsAbout when they're untagged,
     * preserving the legacy mapping for those fields. Once a user picks a
     * canonical URI from autocomplete, the field becomes a JSON-LD node and
     * joins the knowsAbout list.
     */
    private static void addEntryIfTagged(Map<String, Object> deduped,
                                         String label, String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        addEntry(deduped, label, uri);
    }

    private static void addCollection(Map<String, Object> deduped,
                                      List<String> labels,
                                      List<String> uris) {
        if (labels == null) {
            return;
        }
        for (int i = 0; i < labels.size(); i++) {
            String uri = (uris != null && i < uris.size()) ? uris.get(i) : null;
            addEntry(deduped, labels.get(i), uri);
        }
    }
}
