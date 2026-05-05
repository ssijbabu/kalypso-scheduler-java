package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterConfigType;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link ClusterType} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class ClusterTypeSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testClusterConfigTypeSerializesToLowercaseJson() throws Exception {
        assertEquals("\"configmap\"", objectMapper.writeValueAsString(ClusterConfigType.CONFIGMAP));
        assertEquals("\"envfile\"",   objectMapper.writeValueAsString(ClusterConfigType.ENVFILE));
    }

    @Test
    void testClusterConfigTypeDeserializesFromLowercaseJson() throws Exception {
        assertEquals(ClusterConfigType.CONFIGMAP, objectMapper.readValue("\"configmap\"", ClusterConfigType.class));
        assertEquals(ClusterConfigType.ENVFILE,   objectMapper.readValue("\"envfile\"",   ClusterConfigType.class));
    }

    @Test
    void testClusterTypeSpecRoundTripWithAllFields() throws Exception {
        ClusterTypeSpec spec = new ClusterTypeSpec();
        spec.setReconciler("my-reconciler-template");
        spec.setNamespaceService("my-namespace-template");
        spec.setConfigType(ClusterConfigType.CONFIGMAP);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"reconciler\":\"my-reconciler-template\""),    "reconciler must be serialized");
        assertTrue(json.contains("\"namespaceService\":\"my-namespace-template\""), "namespaceService must use camelCase key");
        assertTrue(json.contains("\"configType\":\"configmap\""),                  "configType must serialize as lowercase");

        ClusterTypeSpec deserialized = objectMapper.readValue(json, ClusterTypeSpec.class);
        assertEquals("my-reconciler-template",  deserialized.getReconciler());
        assertEquals("my-namespace-template",   deserialized.getNamespaceService());
        assertEquals(ClusterConfigType.CONFIGMAP, deserialized.getConfigType());
    }

    @Test
    void testClusterTypeSpecRoundTripWithEnvfileConfigType() throws Exception {
        ClusterTypeSpec spec = new ClusterTypeSpec();
        spec.setReconciler("edge-reconciler");
        spec.setConfigType(ClusterConfigType.ENVFILE);

        String json = objectMapper.writeValueAsString(spec);
        ClusterTypeSpec deserialized = objectMapper.readValue(json, ClusterTypeSpec.class);

        assertEquals(ClusterConfigType.ENVFILE, deserialized.getConfigType());
        assertNull(deserialized.getNamespaceService(), "unset optional field must remain null");
    }

    @Test
    void testClusterTypeSpecNullFieldsAreExcludedFromJson() throws Exception {
        // NON_NULL inclusion means unset fields must not appear in serialized output
        ClusterTypeSpec spec = new ClusterTypeSpec();
        spec.setReconciler("reconciler-only");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("namespaceService"), "null namespaceService must be omitted");
        assertFalse(json.contains("configType"),       "null configType must be omitted");
    }

    @Test
    void testClusterTypeSpecDeserializesPartialJson() throws Exception {
        String json = "{\"reconciler\":\"edge-template\",\"configType\":\"envfile\"}";
        ClusterTypeSpec spec = objectMapper.readValue(json, ClusterTypeSpec.class);

        assertEquals("edge-template",          spec.getReconciler());
        assertEquals(ClusterConfigType.ENVFILE, spec.getConfigType());
        assertNull(spec.getNamespaceService());
    }
}
