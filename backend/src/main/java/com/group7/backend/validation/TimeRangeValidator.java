package com.group7.backend.validation;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimeRangeValidator implements ConstraintValidator<ValidTimeRange, AvailabilitySlotRequest> {

    @Override
    public boolean isValid(AvailabilitySlotRequest request, ConstraintValidatorContext context) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            return true; // @NotNull handles null checks
        }
        return request.getEndTime().isAfter(request.getStartTime());
    }
}
