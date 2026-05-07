package io.kalypso.scheduler.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when config values fail JSON Schema validation.
 *
 * <p>Carries the list of individual validation error messages produced by
 * everit-json-schema, mirroring the multi-error behaviour of the Go operator's
 * {@code scheduler/config_validator.go} {@code ValidateValues} method.
 */
public class ConfigValidationException extends RuntimeException {

    private final List<String> validationErrors;

    /**
     * @param message          summary of the validation failure
     * @param validationErrors individual error messages from the schema validator
     */
    public ConfigValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = Collections.unmodifiableList(validationErrors);
    }

    /**
     * Returns the individual validation error messages.
     *
     * @return unmodifiable list of validation errors; never {@code null}
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
