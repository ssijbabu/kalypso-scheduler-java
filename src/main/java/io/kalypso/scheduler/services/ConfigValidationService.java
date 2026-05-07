package io.kalypso.scheduler.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.exception.ConfigValidationException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a free-form config values map against a JSON Schema string.
 *
 * <p>This is the Java equivalent of the Go operator's {@code scheduler/config_validator.go}
 * {@code ConfigValidator} interface and its {@code ValidateValues} method.
 *
 * <h3>Type coercion</h3>
 * Like the Go implementation (which uses {@code gojsonschema} with coercion), this
 * service converts string values to their expected numeric or boolean types before
 * validation when the JSON Schema declares a non-string type for that property:
 * <ul>
 *   <li>{@code "integer"} — {@code "42"} is coerced to {@code 42L}</li>
 *   <li>{@code "number"}  — {@code "3.14"} is coerced to {@code 3.14}</li>
 *   <li>{@code "boolean"} — {@code "true"} / {@code "false"} is coerced to a boolean</li>
 * </ul>
 * Coercion failures are silently ignored; the original string value is kept and the
 * schema validation will report the type mismatch.
 *
 * <h3>Validation errors</h3>
 * All individual violations are collected and surfaced via
 * {@link ConfigValidationException#getValidationErrors()}, rather than reporting
 * only the first error.
 *
 * <p>Usage example:
 * <pre>
 *     configValidationService.validateValues(
 *         Map.of("replicas", "3", "region", "eastus"),
 *         "{\"type\":\"object\",\"properties\":{\"replicas\":{\"type\":\"integer\"},\"region\":{\"type\":\"string\"}}}"
 *     );
 * </pre>
 */
public class ConfigValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigValidationService.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructs a {@code ConfigValidationService} with a default Jackson mapper.
     */
    public ConfigValidationService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates {@code values} against the provided JSON Schema string.
     *
     * <p>Mirrors Go's {@code ValidateValues(ctx, values, schema)} in
     * {@code scheduler/config_validator.go}. Type coercion is applied before
     * validation so that string representations of numbers and booleans pass
     * schema checks for their target types.
     *
     * @param values free-form config values to validate (typically from a ConfigMap)
     * @param schema JSON Schema string describing the expected shape and types
     * @throws ConfigValidationException if any schema constraint is violated;
     *         {@link ConfigValidationException#getValidationErrors()} contains
     *         the individual violation messages
     * @throws IllegalArgumentException  if {@code schema} is not valid JSON
     */
    public void validateValues(Map<String, Object> values, String schema) {
        logger.debug("Validating {} config values against schema", values.size());

        JSONObject rawSchema = new JSONObject(schema);
        Schema schemaObj = SchemaLoader.load(rawSchema);

        Map<String, Object> coerced = coerceTypes(values, rawSchema);
        JSONObject jsonValues;
        try {
            jsonValues = new JSONObject(objectMapper.writeValueAsString(coerced));
        } catch (JsonProcessingException e) {
            throw new ConfigValidationException(
                    "Failed to serialize config values for validation: " + e.getMessage(),
                    Collections.emptyList());
        }

        try {
            schemaObj.validate(jsonValues);
        } catch (ValidationException e) {
            List<String> errors = collectErrors(e);
            logger.debug("Config validation failed with {} error(s)", errors.size());
            throw new ConfigValidationException(
                    "Config validation failed: " + errors.size() + " error(s)", errors);
        }

        logger.debug("Config validation passed");
    }

    /**
     * Applies type coercion to string values based on the JSON Schema {@code properties}.
     *
     * <p>Matches the Go implementation's pre-validation coercion of string values
     * to integer, number, and boolean types when the schema declares them as such.
     *
     * @param values    original values map
     * @param rawSchema parsed JSON Schema object
     * @return new map with coerced values; original map is not mutated
     */
    Map<String, Object> coerceTypes(Map<String, Object> values, JSONObject rawSchema) {
        JSONObject properties = rawSchema.optJSONObject("properties");
        if (properties == null) {
            return values;
        }

        Map<String, Object> result = new HashMap<>(values);
        for (String key : properties.keySet()) {
            if (!result.containsKey(key)) continue;
            Object value = result.get(key);
            if (!(value instanceof String stringValue)) continue;

            JSONObject propSchema = properties.optJSONObject(key);
            if (propSchema == null) continue;
            String type = propSchema.optString("type", "");

            switch (type) {
                case "integer" -> {
                    try { result.put(key, Long.parseLong(stringValue)); }
                    catch (NumberFormatException ignored) {}
                }
                case "number" -> {
                    try { result.put(key, Double.parseDouble(stringValue)); }
                    catch (NumberFormatException ignored) {}
                }
                case "boolean" -> {
                    if ("true".equalsIgnoreCase(stringValue)) result.put(key, Boolean.TRUE);
                    else if ("false".equalsIgnoreCase(stringValue)) result.put(key, Boolean.FALSE);
                }
                default -> { /* leave as-is */ }
            }
        }
        return result;
    }

    private List<String> collectErrors(ValidationException e) {
        if (e.getCausingExceptions().isEmpty()) {
            return Collections.singletonList(e.getMessage());
        }
        List<String> errors = new ArrayList<>();
        for (ValidationException cause : e.getCausingExceptions()) {
            errors.addAll(collectErrors(cause));
        }
        return errors;
    }
}
