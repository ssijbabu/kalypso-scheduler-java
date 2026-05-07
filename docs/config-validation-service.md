# ConfigValidationService — Developer Guide

## Who this is for

This document explains the `ConfigValidationService` class from scratch. No prior
knowledge of JSON Schema, the Go operator, or schema validation libraries is assumed.
If you can read Java and you know what a key-value map is, you have enough background.

---

## 1. The problem ConfigValidationService solves

When the `AssignmentReconciler` processes an `Assignment`, it gathers configuration
data from Kubernetes `ConfigMap` objects. That data might come from users who typed
values manually — meaning it could be the wrong type, missing required fields, or
contain unexpected keys.

`ConfigValidationService` checks that the gathered config values match the shape
described by the `ConfigSchema` CRD before those values are passed into templates
or applied to clusters.

---

## 2. What is JSON Schema?

JSON Schema is a standard for describing the shape and constraints of a JSON object.
Think of it as a contract for a config map:

```json
{
  "type": "object",
  "required": ["region", "replicas"],
  "properties": {
    "region":   { "type": "string"  },
    "replicas": { "type": "integer" },
    "timeout":  { "type": "number"  },
    "enabled":  { "type": "boolean" }
  }
}
```

This schema says: the config must be an object, must have `region` (a string) and
`replicas` (an integer). The `timeout` and `enabled` fields are optional but, if
present, must be the right type.

The `ConfigSchema` CRD stores a JSON Schema string. The `AssignmentReconciler` passes
that schema along with the config data to `ConfigValidationService.validateValues`.

---

## 3. Type coercion

Config values often come from Kubernetes `ConfigMap` objects. **All ConfigMap values
are strings** — Kubernetes does not have typed ConfigMaps. So a `replicas` value that
should be an integer arrives as the string `"3"`.

Without coercion, the schema validator would reject `"3"` when the schema says
`"type": "integer"`. The Go operator (`scheduler/config_validator.go`) handles this
by converting string values to their schema-declared types before validation.

`ConfigValidationService` applies the same coercion:

| Schema type | String value | Coerced to |
|---|---|---|
| `"integer"` | `"42"` | `42L` (Long) |
| `"number"` | `"3.14"` | `3.14` (Double) |
| `"boolean"` | `"true"` | `true` (Boolean) |
| `"boolean"` | `"false"` | `false` (Boolean) |

Coercion is **best-effort**: if the string cannot be parsed (e.g. `"not-a-number"`
for an integer field), the original string is kept unchanged. The schema validator
then reports the type mismatch as a validation error.

---

## 4. The `validateValues` method — step by step

```java
service.validateValues(configValues, schemaString);
```

**Step 1 — Parse the schema**

```java
JSONObject rawSchema = new JSONObject(schemaString);
Schema schema = SchemaLoader.load(rawSchema);
```

The `SchemaLoader` from `everit-json-schema` parses the JSON Schema string into a
`Schema` object that can validate instances.

**Step 2 — Apply type coercion**

```java
Map<String, Object> coerced = coerceTypes(configValues, rawSchema);
```

Iterates the `properties` in the schema. For each property that exists in
`configValues` and has a string value, attempts to convert it to the schema-declared
type (integer → Long, number → Double, boolean → Boolean). Conversion failures are
silently ignored.

**Step 3 — Validate**

```java
JSONObject jsonValues = new JSONObject(objectMapper.writeValueAsString(coerced));
schema.validate(jsonValues);
```

The coerced values are serialized to a `JSONObject` and validated against the schema.

**Step 4 — Collect and throw**

If validation fails, all individual error messages are collected recursively
(everit-json-schema nests sub-errors) and thrown as `ConfigValidationException`:

```java
catch (ValidationException e) {
    List<String> errors = collectErrors(e);  // recurse into getCausingExceptions()
    throw new ConfigValidationException("Config validation failed: " + errors.size() + " error(s)", errors);
}
```

The caller can retrieve all individual errors via
`ConfigValidationException.getValidationErrors()`.

---

## 5. Usage example

```java
String schema = """
    {
      "type": "object",
      "required": ["region", "replicas"],
      "properties": {
        "region":   { "type": "string"  },
        "replicas": { "type": "integer" }
      }
    }
    """;

// Config values gathered from a ConfigMap (all strings)
Map<String, Object> values = Map.of(
    "region",   "eastus",
    "replicas", "3"        // string "3" — will be coerced to integer 3
);

try {
    configValidationService.validateValues(values, schema);
    // Validation passed — proceed with template rendering
} catch (ConfigValidationException e) {
    // Validation failed — update status.conditions and requeue
    e.getValidationErrors().forEach(err -> logger.error("Validation error: {}", err));
}
```

---

## 6. Go correspondence

| Go (`config_validator.go`) | Java (`ConfigValidationService.java`) |
|---|---|
| `type ConfigValidator interface { ValidateValues(...) }` | `validateValues(values, schema)` |
| `gojsonschema.NewStringSchema(schema)` | `SchemaLoader.load(new JSONObject(schema))` |
| `gojsonschema.Validate(schema, input)` | `schema.validate(jsonValues)` |
| String → int coercion before validation | `coerceTypes(values, rawSchema)` |
| Error list from `result.Errors()` | `collectErrors(ValidationException)` |
| Multi-error return | `ConfigValidationException.getValidationErrors()` |
| `github.com/xeipuuv/gojsonschema` | `com.github.erosb:everit-json-schema:1.14.4` |

---

## 7. Files involved

```
src/main/java/io/kalypso/scheduler/
├── exception/
│   └── ConfigValidationException.java
└── services/
    └── ConfigValidationService.java

src/test/java/io/kalypso/scheduler/
└── services/
    └── ConfigValidationServiceTest.java  10 unit tests
```

---

## 8. Key limitations

- **Top-level properties only**: The coercion logic only looks at the top-level
  `properties` object in the schema. Nested schemas (`allOf`, `oneOf`, nested
  objects) are not traversed for coercion. The schema validator will still check
  nested constraints; only the string-to-type coercion is flat.

- **No array coercion**: If a config value is a JSON array encoded as a string,
  it will not be coerced. Arrays in config values should be passed as actual
  `List<Object>` values, not as strings.
