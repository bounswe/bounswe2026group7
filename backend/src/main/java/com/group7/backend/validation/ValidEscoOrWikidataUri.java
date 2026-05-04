package com.group7.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Field must be null, a canonical ESCO skill URI, or a canonical Wikidata
 * entity URI. Used on interest collections, which mix professional skills
 * (ESCO) and personal hobbies (Wikidata).
 */
@Documented
@Constraint(validatedBy = EscoOrWikidataUriValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEscoOrWikidataUri {
    String message() default "Must be a canonical ESCO skill URI or Wikidata entity URI";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
