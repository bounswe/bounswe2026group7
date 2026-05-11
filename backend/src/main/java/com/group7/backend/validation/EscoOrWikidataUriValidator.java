package com.group7.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EscoOrWikidataUriValidator
        implements ConstraintValidator<ValidEscoOrWikidataUri, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return EscoUriValidator.ESCO_SKILL_URI.matcher(value).matches()
                || WikidataUriValidator.WIKIDATA_URI.matcher(value).matches();
    }
}
