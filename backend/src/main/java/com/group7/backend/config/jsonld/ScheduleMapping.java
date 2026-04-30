package com.group7.backend.config.jsonld;

import com.group7.backend.dto.response.AvailabilitySlotResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ScheduleMapping implements JsonLdMapping {

    @Override
    public boolean supports(Class<?> bodyType) {
        return AvailabilitySlotResponse.class.isAssignableFrom(bodyType);
    }

    @Override
    public Map<String, Object> apply(Object body) {
        AvailabilitySlotResponse slot = (AvailabilitySlotResponse) body;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@context", JsonLdContext.SCHEMA_ORG);
        result.put("@type", "Schedule");
        // No @id: availability slots are sub-resources of a mentor and have no
        // dedicated GET-by-id endpoint. JSON-LD allows blank nodes here.
        if (slot.getDayOfWeek() != null) {
            result.put("byDay", "https://schema.org/" + capitalize(slot.getDayOfWeek()));
        }
        if (slot.getStartTime() != null) {
            result.put("startTime", slot.getStartTime().toString());
        }
        if (slot.getEndTime() != null) {
            result.put("endTime", slot.getEndTime().toString());
        }
        if (slot.isRecurring()) {
            result.put("repeatFrequency", "P1W");
        }
        return result;
    }

    private static String capitalize(String dayOfWeek) {
        String lower = dayOfWeek.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
