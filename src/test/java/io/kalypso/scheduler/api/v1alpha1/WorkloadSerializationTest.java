package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link Workload} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class WorkloadSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testWorkloadSpecRoundTripWithMultipleDeploymentTargets() throws Exception {
        RepositoryReference ref1 = new RepositoryReference();
        ref1.setRepo("https://github.com/org/wl");
        ref1.setBranch("main");
        ref1.setPath("./targets/east");

        WorkloadTarget t1 = new WorkloadTarget();
        t1.setName("prod-east");
        t1.setManifests(ref1);

        RepositoryReference ref2 = new RepositoryReference();
        ref2.setRepo("https://github.com/org/wl");
        ref2.setBranch("main");
        ref2.setPath("./targets/west");

        WorkloadTarget t2 = new WorkloadTarget();
        t2.setName("prod-west");
        t2.setManifests(ref2);

        WorkloadSpec spec = new WorkloadSpec();
        spec.setDeploymentTargets(List.of(t1, t2));

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"deploymentTargets\""), "deploymentTargets must be serialized");
        assertTrue(json.contains("\"name\":\"prod-east\""), "first target name must be present");
        assertTrue(json.contains("\"name\":\"prod-west\""), "second target name must be present");

        WorkloadSpec deserialized = objectMapper.readValue(json, WorkloadSpec.class);
        assertEquals(2, deserialized.getDeploymentTargets().size());
        assertEquals("prod-east", deserialized.getDeploymentTargets().get(0).getName());
        assertEquals("./targets/east", deserialized.getDeploymentTargets().get(0).getManifests().getPath());
        assertEquals("prod-west", deserialized.getDeploymentTargets().get(1).getName());
    }

    @Test
    void testWorkloadSpecEmptyDeploymentTargetsSerializesAsEmptyArray() throws Exception {
        WorkloadSpec spec = new WorkloadSpec();

        String json = objectMapper.writeValueAsString(spec);
        WorkloadSpec deserialized = objectMapper.readValue(json, WorkloadSpec.class);

        assertNotNull(deserialized.getDeploymentTargets(), "deploymentTargets must never be null");
        assertTrue(deserialized.getDeploymentTargets().isEmpty(), "empty list must deserialize as empty");
    }

    @Test
    void testWorkloadSpecSetNullDeploymentTargetsProducesEmptyList() {
        WorkloadSpec spec = new WorkloadSpec();
        spec.setDeploymentTargets(null);

        assertNotNull(spec.getDeploymentTargets(), "null setter must produce empty list");
        assertTrue(spec.getDeploymentTargets().isEmpty());
    }

    @Test
    void testWorkloadTargetRoundTripWithAllFields() throws Exception {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/wl");
        ref.setBranch("v2");
        ref.setPath("./staging");

        WorkloadTarget target = new WorkloadTarget();
        target.setName("staging-cluster");
        target.setManifests(ref);

        String json = objectMapper.writeValueAsString(target);
        WorkloadTarget deserialized = objectMapper.readValue(json, WorkloadTarget.class);

        assertEquals("staging-cluster", deserialized.getName());
        assertNotNull(deserialized.getManifests());
        assertEquals("./staging", deserialized.getManifests().getPath());
    }

    @Test
    void testWorkloadTargetNullManifestsIsExcludedFromJson() throws Exception {
        WorkloadTarget target = new WorkloadTarget();
        target.setName("no-manifests-target");

        String json = objectMapper.writeValueAsString(target);

        assertFalse(json.contains("\"manifests\""), "null manifests must be omitted");
    }
}
