package com.group7.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Field must be null or a Wikidata entity URI of the form
 * {@code http://www.wikidata.org/entity/Q<digits>}.
 */
@Documented
@Constraint(validatedBy = WikidataUriValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWikidataUri {
    String message() default "Must be a canonical Wikidata URI (http://www.wikidata.org/entity/Q<digits>)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
