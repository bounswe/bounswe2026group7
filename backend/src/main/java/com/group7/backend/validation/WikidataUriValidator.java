package com.group7.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class WikidataUriValidator implements ConstraintValidator<ValidWikidataUri, String> {

    static final Pattern WIKIDATA_URI =
            Pattern.compile("^http://www\\.wikidata\\.org/entity/Q[0-9]+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return WIKIDATA_URI.matcher(value).matches();
    }
}
