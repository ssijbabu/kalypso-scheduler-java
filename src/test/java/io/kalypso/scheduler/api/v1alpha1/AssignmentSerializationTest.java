package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link Assignment} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class AssignmentSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testAssignmentSpecRoundTripWithAllFields() throws Exception {
        AssignmentSpec spec = new AssignmentSpec();
        spec.setClusterType("large-aks");
        spec.setDeploymentTarget("prod-east-cluster");

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"clusterType\":\"large-aks\""), "clusterType must be serialized");
        assertTrue(json.contains("\"deploymentTarget\":\"prod-east-cluster\""), "deploymentTarget must be serialized");

        AssignmentSpec deserialized = objectMapper.readValue(json, AssignmentSpec.class);
        assertEquals("large-aks", deserialized.getClusterType());
        assertEquals("prod-east-cluster", deserialized.getDeploymentTarget());
    }

    @Test
    void testAssignmentSpecNullFieldsOmittedFromJson() throws Exception {
        AssignmentSpec spec = new AssignmentSpec();
        spec.setClusterType("edge-k3s");

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"deploymentTarget\""), "null deploymentTarget must be omitted");
    }

    @Test
    void testAssignmentSpecDeserializesPartialJson() throws Exception {
        String json = "{\"clusterType\":\"medium-aks\"}";
        AssignmentSpec spec = objectMapper.readValue(json, AssignmentSpec.class);

        assertEquals("medium-aks", spec.getClusterType());
        assertNull(spec.getDeploymentTarget(), "absent deploymentTarget must remain null");
    }

    @Test
    void testAssignmentSpecRoundTripWithOnlyDeploymentTarget() throws Exception {
        AssignmentSpec spec = new AssignmentSpec();
        spec.setDeploymentTarget("staging-cluster");

        String json = objectMapper.writeValueAsString(spec);
        AssignmentSpec deserialized = objectMapper.readValue(json, AssignmentSpec.class);

        assertEquals("staging-cluster", deserialized.getDeploymentTarget());
        assertNull(deserialized.getClusterType(), "absent clusterType must remain null");
    }

    @Test
    void testAssignmentSpecDeserializesFullJson() throws Exception {
        String json = "{\"clusterType\":\"gpu-cluster\",\"deploymentTarget\":\"ml-workload-target\"}";
        AssignmentSpec spec = objectMapper.readValue(json, AssignmentSpec.class);

        assertEquals("gpu-cluster", spec.getClusterType());
        assertEquals("ml-workload-target", spec.getDeploymentTarget());
    }
}
