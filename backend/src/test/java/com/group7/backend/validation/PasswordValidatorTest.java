package com.group7.backend.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordValidator();
    }

    @Test
    void validPassword() {
        assertTrue(validator.isValid("Password1", null));
    }

    @Test
    void nullPassword() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void tooShort() {
        assertFalse(validator.isValid("Pass1", null));
    }

    @Test
    void noUppercase() {
        assertFalse(validator.isValid("password1", null));
    }

    @Test
    void noLowercase() {
        assertFalse(validator.isValid("PASSWORD1", null));
    }

    @Test
    void noDigit() {
        assertFalse(validator.isValid("Password", null));
    }

    @Test
    void exactlyEightChars() {
        assertTrue(validator.isValid("Passwor1", null));
    }

    @Test
    void sevenCharsWithAllCriteria() {
        assertFalse(validator.isValid("Passw1a", null));
    }
}
