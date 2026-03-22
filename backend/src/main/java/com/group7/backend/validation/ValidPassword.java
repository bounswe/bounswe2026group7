package com.group7.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "Password must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, and one digit";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
