package com.group7.backend.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void valid_props_passValidation() {
        AdminBootstrapProperties props = newProps("admin@example.com", "Sup3rSecurePwd!");
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void blankEmail_failsValidation() {
        AdminBootstrapProperties props = newProps("", "Sup3rSecurePwd!");
        assertThat(violationFields(props)).contains("email");
    }

    @Test
    void malformedEmail_failsValidation() {
        AdminBootstrapProperties props = newProps("not-an-email", "Sup3rSecurePwd!");
        assertThat(violationFields(props)).contains("email");
    }

    @Test
    void nullEmail_failsValidation() {
        AdminBootstrapProperties props = newProps(null, "Sup3rSecurePwd!");
        assertThat(violationFields(props)).contains("email");
    }

    @Test
    void blankPassword_failsValidation() {
        AdminBootstrapProperties props = newProps("admin@example.com", "");
        assertThat(violationFields(props)).contains("password");
    }

    @Test
    void shortPassword_failsValidation() {
        AdminBootstrapProperties props = newProps("admin@example.com", "Short1");
        assertThat(violationFields(props)).contains("password");
    }

    @Test
    void weakPassword_failsValidPasswordRule() {
        // 12+ chars but no uppercase / no digit / no lowercase mix
        AdminBootstrapProperties props = newProps("admin@example.com", "alllowercase");
        assertThat(violationFields(props)).contains("password");
    }

    @Test
    void blankFirstName_failsValidation() {
        AdminBootstrapProperties props = newProps("admin@example.com", "Sup3rSecurePwd!");
        props.setFirstName("");
        assertThat(violationFields(props)).contains("firstName");
    }

    @Test
    void blankLastName_failsValidation() {
        AdminBootstrapProperties props = newProps("admin@example.com", "Sup3rSecurePwd!");
        props.setLastName("");
        assertThat(violationFields(props)).contains("lastName");
    }

    private static AdminBootstrapProperties newProps(String email, String password) {
        AdminBootstrapProperties props = new AdminBootstrapProperties();
        props.setEmail(email);
        props.setPassword(password);
        // firstName / lastName left at defaults ("System" / "Admin")
        return props;
    }

    private static java.util.List<String> violationFields(AdminBootstrapProperties props) {
        Set<ConstraintViolation<AdminBootstrapProperties>> violations = validator.validate(props);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .toList();
    }
}
