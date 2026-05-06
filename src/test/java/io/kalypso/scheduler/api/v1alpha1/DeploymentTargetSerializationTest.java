package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link DeploymentTarget} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class DeploymentTargetSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testDeploymentTargetSpecRoundTripWithAllFields() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/infra");
        ref.setBranch("main");
        ref.setPath("./clusters/prod-east");

        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName("prod-east-cluster");
        spec.setEnvironment("prod");
        spec.setLabels(Map.of("region", "east-us", "tier", "production"));
        spec.setManifests(ref);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"name\":\"prod-east-cluster\""), "name must be serialized");
        assertTrue(json.contains("\"environment\":\"prod\""), "environment must be serialized");
        assertTrue(json.contains("\"labels\""), "labels must be serialized");
        assertTrue(json.contains("\"manifests\""), "manifests must be serialized");

        DeploymentTargetSpec deserialized = objectMapper.readValue(json, DeploymentTargetSpec.class);
        assertEquals("prod-east-cluster", deserialized.getName());
        assertEquals("prod", deserialized.getEnvironment());
        assertEquals("east-us", deserialized.getLabels().get("region"));
        assertEquals("production", deserialized.getLabels().get("tier"));
        assertEquals("./clusters/prod-east", deserialized.getManifests().getPath());
    }

    @Test
    void testDeploymentTargetSpecNullFieldsOmittedFromJson() throws Exception {
        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName("minimal-target");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"labels\""), "null labels must be omitted");
        assertFalse(json.contains("\"environment\""), "null environment must be omitted");
        assertFalse(json.contains("\"manifests\""), "null manifests must be omitted");
    }

    @Test
    void testDeploymentTargetSpecDeserializesPartialJson() throws Exception {
        String json = "{\"name\":\"edge-cluster\",\"environment\":\"dev\"}";
        DeploymentTargetSpec spec = objectMapper.readValue(json, DeploymentTargetSpec.class);

        assertEquals("edge-cluster", spec.getName());
        assertEquals("dev", spec.getEnvironment());
        assertNull(spec.getLabels(), "absent labels must remain null");
        assertNull(spec.getManifests(), "absent manifests must remain null");
    }

    @Test
    void testDeploymentTargetSpecLabelConstants() {
        assertEquals("workload.scheduler.kalypso.io/workspace", DeploymentTargetSpec.WORKSPACE_LABEL);
        assertEquals("workload.scheduler.kalypso.io/workload",  DeploymentTargetSpec.WORKLOAD_LABEL);
    }

    @Test
    void testDeploymentTargetSpecLabelsMapRoundTrip() throws Exception {
        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName("labeled-target");
        spec.setLabels(Map.of("zone", "a", "purpose", "edge"));

        String json = objectMapper.writeValueAsString(spec);
        DeploymentTargetSpec deserialized = objectMapper.readValue(json, DeploymentTargetSpec.class);

        assertNotNull(deserialized.getLabels());
        assertEquals("a",    deserialized.getLabels().get("zone"));
        assertEquals("edge", deserialized.getLabels().get("purpose"));
    }
}
