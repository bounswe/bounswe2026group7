package com.group7.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Field must be null or an ESCO skill URI of the form
 * {@code http://data.europa.eu/esco/skill/<uuid>}. Linked-data identifiers
 * are canonical with {@code http://}, not https.
 */
@Documented
@Constraint(validatedBy = EscoUriValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEscoUri {
    String message() default "Must be a canonical ESCO skill URI (http://data.europa.eu/esco/skill/<uuid>)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
