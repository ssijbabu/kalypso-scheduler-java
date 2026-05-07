package io.kalypso.scheduler.services;

import io.kalypso.scheduler.exception.ConfigValidationException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigValidationService}.
 *
 * <p>Covers: valid value sets, missing required fields, type coercion
 * (string → integer, number, boolean), and invalid-type detection.
 *
 * <p>Field expectations mirror the Go operator's {@code ValidateValues} behaviour
 * in {@code scheduler/config_validator.go}: strings are coerced to numeric and
 * boolean types before validation so that ConfigMap-sourced string values pass
 * schema checks for their target types.
 */
class ConfigValidationServiceTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "required": ["region", "replicas"],
              "properties": {
                "region":   { "type": "string"  },
                "replicas": { "type": "integer" },
                "ratio":    { "type": "number"  },
                "enabled":  { "type": "boolean" }
              }
            }
            """;

    private ConfigValidationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigValidationService();
    }

    /**
     * Verifies that a fully valid config map passes without throwing.
     */
    @Test
    void testValidateValuesSuccess() {
        assertDoesNotThrow(() -> service.validateValues(
                Map.of("region", "eastus", "replicas", 3),
                SCHEMA));
    }

    /**
     * Verifies that a missing required field causes {@link ConfigValidationException}.
     */
    @Test
    void testValidateValuesMissingRequired() {
        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> service.validateValues(
                        Map.of("region", "eastus"), // "replicas" is missing
                        SCHEMA));
        assertFalse(ex.getValidationErrors().isEmpty(),
                "Validation errors must be non-empty for a required-field violation");
    }

    /**
     * Verifies that a string {@code "42"} is coerced to integer {@code 42} so it
     * passes an {@code "integer"} schema check.
     *
     * <p>Mirrors the Go operator's pre-validation type coercion that allows
     * ConfigMap string values to satisfy numeric schema constraints.
     */
    @Test
    void testValidateValuesStringToIntegerCoercion() {
        // "replicas" is a string "3" — must be coerced to int before validation
        assertDoesNotThrow(() -> service.validateValues(
                Map.of("region", "westus", "replicas", "3"),
                SCHEMA));
    }

    /**
     * Verifies that a string {@code "3.14"} is coerced to a double and passes
     * a {@code "number"} schema check.
     */
    @Test
    void testValidateValuesStringToNumberCoercion() {
        assertDoesNotThrow(() -> service.validateValues(
                Map.of("region", "westus", "replicas", 1, "ratio", "0.75"),
                SCHEMA));
    }

    /**
     * Verifies that string {@code "true"} / {@code "false"} are coerced to boolean.
     */
    @Test
    void testValidateValuesStringToBooleanCoercion() {
        assertDoesNotThrow(() -> service.validateValues(
                Map.of("region", "westus", "replicas", 2, "enabled", "true"),
                SCHEMA));
    }

    /**
     * Verifies that a value of the wrong type (e.g. a non-numeric string for an integer field)
     * fails validation and surfaces at least one error.
     */
    @Test
    void testValidateValuesWrongTypeFails() {
        ConfigValidationException ex = assertThrows(ConfigValidationException.class,
                () -> service.validateValues(
                        Map.of("region", "eastus", "replicas", "not-a-number"),
                        SCHEMA));
        assertFalse(ex.getValidationErrors().isEmpty(),
                "Validation errors must be non-empty for a type mismatch");
    }

    // -------------------------------------------------------------------------
    // coerceTypes — package-private, tests the helper directly
    // -------------------------------------------------------------------------

    /**
     * Verifies that coerceTypes converts string integers to Long.
     */
    @Test
    void testCoerceTypesIntegerConversion() {
        JSONObject rawSchema = new JSONObject(SCHEMA);
        Map<String, Object> result = service.coerceTypes(
                Map.of("replicas", "5"), rawSchema);
        assertEquals(5L, result.get("replicas"), "String '5' must become Long 5");
    }

    /**
     * Verifies that coerceTypes converts string floats to Double.
     */
    @Test
    void testCoerceTypesNumberConversion() {
        JSONObject rawSchema = new JSONObject(SCHEMA);
        Map<String, Object> result = service.coerceTypes(
                Map.of("region", "eastus", "replicas", 1, "ratio", "2.5"), rawSchema);
        assertEquals(2.5, (double) result.get("ratio"), 1e-9, "String '2.5' must become Double 2.5");
    }

    /**
     * Verifies that coerceTypes converts "true" / "false" strings to Boolean.
     */
    @Test
    void testCoerceTypesBooleanConversion() {
        JSONObject rawSchema = new JSONObject(SCHEMA);
        Map<String, Object> result = service.coerceTypes(
                Map.of("enabled", "false"), rawSchema);
        assertEquals(Boolean.FALSE, result.get("enabled"),
                "String 'false' must become Boolean.FALSE");
    }

    /**
     * Verifies that coerceTypes leaves values unchanged when the conversion fails
     * (e.g. a non-numeric string in an integer field).
     */
    @Test
    void testCoerceTypesUnconvertibleValueLeftAsIs() {
        JSONObject rawSchema = new JSONObject(SCHEMA);
        Map<String, Object> result = service.coerceTypes(
                Map.of("replicas", "not-a-number"), rawSchema);
        assertEquals("not-a-number", result.get("replicas"),
                "Unconvertible string must remain unchanged");
    }
}
