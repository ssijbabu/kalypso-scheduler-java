package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.ConfigSchemaSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link ConfigSchema} CRD model classes serialize to and from JSON
 * with the field names and structure expected by the Kubernetes API server.
 */
class ConfigSchemaSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testConfigSchemaSpecRoundTripWithAllFields() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "REGION", Map.of("type", "string"),
                        "DB_URL",  Map.of("type", "string")
                )
        );

        ConfigSchemaSpec spec = new ConfigSchemaSpec();
        spec.setClusterType("large");
        spec.setSchema(schema);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"clusterType\":\"large\""), "clusterType must be serialized");
        assertTrue(json.contains("\"schema\""),                "schema must be serialized");
        assertTrue(json.contains("\"type\":\"object\""),       "schema type must be present");

        ConfigSchemaSpec deserialized = objectMapper.readValue(json, ConfigSchemaSpec.class);
        assertEquals("large", deserialized.getClusterType());
        assertNotNull(deserialized.getSchema());

        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = (Map<String, Object>) deserialized.getSchema();
        assertEquals("object", roundTripped.get("type"));
    }

    @Test
    void testConfigSchemaSpecRoundTripWithClusterTypeOnly() throws Exception {
        ConfigSchemaSpec spec = new ConfigSchemaSpec();
        spec.setClusterType("edge");

        String json = objectMapper.writeValueAsString(spec);
        ConfigSchemaSpec deserialized = objectMapper.readValue(json, ConfigSchemaSpec.class);

        assertEquals("edge", deserialized.getClusterType());
        assertNull(deserialized.getSchema(), "unset schema must remain null");
    }

    @Test
    void testConfigSchemaSpecNullFieldsAreExcludedFromJson() throws Exception {
        ConfigSchemaSpec spec = new ConfigSchemaSpec();
        spec.setClusterType("small");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"schema\""), "null schema must be omitted from JSON");
    }

    @Test
    void testConfigSchemaSpecDeserializesPartialJson() throws Exception {
        String json = "{\"clusterType\":\"medium\"}";
        ConfigSchemaSpec spec = objectMapper.readValue(json, ConfigSchemaSpec.class);

        assertEquals("medium", spec.getClusterType());
        assertNull(spec.getSchema());
    }

    @Test
    void testConfigSchemaSpecSchemaSupportsNestedStructure() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("REGION")
        );

        ConfigSchemaSpec spec = new ConfigSchemaSpec();
        spec.setClusterType("large");
        spec.setSchema(schema);

        String json = objectMapper.writeValueAsString(spec);
        ConfigSchemaSpec deserialized = objectMapper.readValue(json, ConfigSchemaSpec.class);

        assertNotNull(deserialized.getSchema());
        @SuppressWarnings("unchecked")
        Map<String, Object> roundTripped = (Map<String, Object>) deserialized.getSchema();
        assertEquals("object", roundTripped.get("type"));
        assertEquals(false,    roundTripped.get("additionalProperties"));
        assertEquals("REGION", ((List<?>) roundTripped.get("required")).get(0));
    }
}
