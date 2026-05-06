package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.SchedulingPolicySpec;
import io.kalypso.scheduler.api.v1alpha1.spec.Selector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link SchedulingPolicy} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class SchedulingPolicySerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSchedulingPolicySpecRoundTripWithAllFields() throws Exception {
        Selector dtSelector = new Selector();
        dtSelector.setWorkspace("team-alpha");
        dtSelector.setLabelSelector(Map.of("tier", "production", "region", "east-us"));

        Selector ctSelector = new Selector();
        ctSelector.setLabelSelector(Map.of("size", "large"));

        SchedulingPolicySpec spec = new SchedulingPolicySpec();
        spec.setDeploymentTargetSelector(dtSelector);
        spec.setClusterTypeSelector(ctSelector);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"deploymentTargetSelector\""), "deploymentTargetSelector must be serialized");
        assertTrue(json.contains("\"clusterTypeSelector\""), "clusterTypeSelector must be serialized");
        assertTrue(json.contains("\"workspace\":\"team-alpha\""), "workspace must be serialized");
        assertTrue(json.contains("\"labelSelector\""), "labelSelector must be serialized");

        SchedulingPolicySpec deserialized = objectMapper.readValue(json, SchedulingPolicySpec.class);
        assertNotNull(deserialized.getDeploymentTargetSelector());
        assertEquals("team-alpha", deserialized.getDeploymentTargetSelector().getWorkspace());
        assertEquals("production", deserialized.getDeploymentTargetSelector().getLabelSelector().get("tier"));
        assertNotNull(deserialized.getClusterTypeSelector());
        assertEquals("large", deserialized.getClusterTypeSelector().getLabelSelector().get("size"));
    }

    @Test
    void testSchedulingPolicySpecNullSelectorsOmittedFromJson() throws Exception {
        SchedulingPolicySpec spec = new SchedulingPolicySpec();

        String json = objectMapper.writeValueAsString(spec);

        assertFalse(json.contains("\"deploymentTargetSelector\""), "null dtSelector must be omitted");
        assertFalse(json.contains("\"clusterTypeSelector\""), "null ctSelector must be omitted");
    }

    @Test
    void testSelectorRoundTripWithWorkspaceOnly() throws Exception {
        Selector selector = new Selector();
        selector.setWorkspace("team-beta");

        String json = objectMapper.writeValueAsString(selector);
        Selector deserialized = objectMapper.readValue(json, Selector.class);

        assertEquals("team-beta", deserialized.getWorkspace());
        assertNull(deserialized.getLabelSelector(), "absent labelSelector must remain null");
    }

    @Test
    void testSelectorNullWorkspaceIsOmittedFromJson() throws Exception {
        Selector selector = new Selector();
        selector.setLabelSelector(Map.of("env", "prod"));

        String json = objectMapper.writeValueAsString(selector);

        assertFalse(json.contains("\"workspace\""), "null workspace must be omitted");
        assertTrue(json.contains("\"labelSelector\""), "labelSelector must be present");
    }

    @Test
    void testSchedulingPolicySpecDeserializesPartialJson() throws Exception {
        String json = "{\"deploymentTargetSelector\":{\"workspace\":\"ws1\"}}";
        SchedulingPolicySpec spec = objectMapper.readValue(json, SchedulingPolicySpec.class);

        assertNotNull(spec.getDeploymentTargetSelector());
        assertEquals("ws1", spec.getDeploymentTargetSelector().getWorkspace());
        assertNull(spec.getClusterTypeSelector(), "absent clusterTypeSelector must remain null");
    }
}
