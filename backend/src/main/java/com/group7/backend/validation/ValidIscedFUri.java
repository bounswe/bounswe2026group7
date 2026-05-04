package com.group7.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Field must be null or an ESCO-published ISCED-F URI of the form
 * {@code http://data.europa.eu/esco/isced-f/<4-digit-code>}.
 */
@Documented
@Constraint(validatedBy = IscedFUriValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIscedFUri {
    String message() default "Must be a canonical ISCED-F URI (http://data.europa.eu/esco/isced-f/<4-digit>)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
