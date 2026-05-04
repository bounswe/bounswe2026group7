package com.group7.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class EscoUriValidator implements ConstraintValidator<ValidEscoUri, String> {

    static final Pattern ESCO_SKILL_URI =
            Pattern.compile("^http://data\\.europa\\.eu/esco/skill/[0-9a-fA-F-]+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return ESCO_SKILL_URI.matcher(value).matches();
    }
}
