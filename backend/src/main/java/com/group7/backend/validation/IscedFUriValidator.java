package com.group7.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class IscedFUriValidator implements ConstraintValidator<ValidIscedFUri, String> {

    static final Pattern ISCED_F_URI =
            Pattern.compile("^http://data\\.europa\\.eu/esco/isced-f/[0-9]{4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return ISCED_F_URI.matcher(value).matches();
    }
}
