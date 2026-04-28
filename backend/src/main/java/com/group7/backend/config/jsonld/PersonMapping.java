package com.group7.backend.config.jsonld;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.MenteeResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.dto.response.UserResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        // (bio for mentors, backgroundInfo for mentees). schema.org treats
        // description as a generic self-description property regardless of
        // role; consumers disambiguate Mentor vs Mentee via @type plus
        // role-specific fields (jobTitle, affiliation for mentors). Wrapping
        // in Role / OrganizationRole is a future enhancement that depends on
        // organization modeling and is out of scope for Wave 1.
        if (user instanceof MentorResponse mentor) {
            putIfPresent(result, "description", mentor.getBio());
            putIfPresent(result, "jobTitle", mentor.getField());
            putIfPresent(result, "affiliation", mentor.getAffiliation());

            LinkedHashSet<String> knowsAbout = new LinkedHashSet<>();
            addNonBlank(knowsAbout, mentor.getExpertise());
            addNonBlank(knowsAbout, mentor.getInterests());
            if (!knowsAbout.isEmpty()) {
                result.put("knowsAbout", new ArrayList<>(knowsAbout));
            }
        } else if (user instanceof MenteeResponse mentee) {
            putIfPresent(result, "description", mentee.getBackgroundInfo());

            LinkedHashSet<String> knowsAbout = new LinkedHashSet<>();
            addNonBlank(knowsAbout, mentee.getInterests());
            addNonBlank(knowsAbout, mentee.getSkills());
            if (!knowsAbout.isEmpty()) {
                result.put("knowsAbout", new ArrayList<>(knowsAbout));
            }
            // mentee.major and mentee.careerInterest do not yet have a clean
            // schema.org mapping in our domain (memberOf would imply membership
            // in an organisation; knowsAbout would imply mastery rather than
            // interest). Both are deferred until the ESCO/ISCED-F migration in
            // Wave 3a (#137) introduces typed identifiers for fields of study.
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

    private static void addNonBlank(LinkedHashSet<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    private static void addNonBlank(LinkedHashSet<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addNonBlank(target, value);
        }
    }
}
