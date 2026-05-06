package io.kalypso.scheduler.api.v1alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link AssignmentPackage} CRD model classes serialize to and from JSON
 * with the field names expected by the Kubernetes API server.
 */
class AssignmentPackageSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testAssignmentPackageSpecRoundTripWithAllFields() throws Exception {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("apiVersion: kustomize.toolkit.fluxcd.io/v1\nkind: Kustomization"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);
        spec.setNamespaceManifests(List.of("apiVersion: v1\nkind: Namespace"));
        spec.setNamespaceManifestsContentType(ContentType.YAML);
        spec.setConfigManifests(List.of("#!/bin/bash\necho ok"));
        spec.setConfigManifestsContentType(ContentType.SH);

        String json = objectMapper.writeValueAsString(spec);

        assertTrue(json.contains("\"reconcilerManifests\""), "reconcilerManifests must be serialized");
        assertTrue(json.contains("\"reconcilerManifestsContentType\":\"yaml\""), "reconcilerManifestsContentType must serialize as lowercase yaml");
        assertTrue(json.contains("\"namespaceManifests\""), "namespaceManifests must be serialized");
        assertTrue(json.contains("\"configManifestsContentType\":\"sh\""), "configManifestsContentType must serialize as lowercase sh");

        AssignmentPackageSpec deserialized = objectMapper.readValue(json, AssignmentPackageSpec.class);
        assertEquals(1, deserialized.getReconcilerManifests().size());
        assertEquals(ContentType.YAML, deserialized.getReconcilerManifestsContentType());
        assertEquals(ContentType.SH, deserialized.getConfigManifestsContentType());
        assertEquals(1, deserialized.getConfigManifests().size());
    }

    @Test
    void testAssignmentPackageSpecEmptyListsSerializeAsEmptyArrays() throws Exception {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();

        String json = objectMapper.writeValueAsString(spec);
        AssignmentPackageSpec deserialized = objectMapper.readValue(json, AssignmentPackageSpec.class);

        assertNotNull(deserialized.getReconcilerManifests());
        assertTrue(deserialized.getReconcilerManifests().isEmpty(), "reconcilerManifests must be empty");
        assertNotNull(deserialized.getNamespaceManifests());
        assertTrue(deserialized.getNamespaceManifests().isEmpty(), "namespaceManifests must be empty");
        assertNotNull(deserialized.getConfigManifests());
        assertTrue(deserialized.getConfigManifests().isEmpty(), "configManifests must be empty");
    }

    @Test
    void testAssignmentPackageSpecNullListSetterProducesEmptyList() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(null);
        spec.setNamespaceManifests(null);
        spec.setConfigManifests(null);

        assertNotNull(spec.getReconcilerManifests());
        assertNotNull(spec.getNamespaceManifests());
        assertNotNull(spec.getConfigManifests());
        assertTrue(spec.getReconcilerManifests().isEmpty());
        assertTrue(spec.getNamespaceManifests().isEmpty());
        assertTrue(spec.getConfigManifests().isEmpty());
    }

    @Test
    void testAssignmentPackageLabelConstants() {
        assertEquals("scheduler.kalypso.io/clusterType",      AssignmentPackageSpec.CLUSTER_TYPE_LABEL);
        assertEquals("scheduler.kalypso.io/deploymentTarget", AssignmentPackageSpec.DEPLOYMENT_TARGET_LABEL);
    }

    @Test
    void testAssignmentPackageSpecMultipleManifestsInEachList() throws Exception {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("manifest-one", "manifest-two", "manifest-three"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);

        String json = objectMapper.writeValueAsString(spec);
        AssignmentPackageSpec deserialized = objectMapper.readValue(json, AssignmentPackageSpec.class);

        assertEquals(3, deserialized.getReconcilerManifests().size());
        assertEquals("manifest-one",   deserialized.getReconcilerManifests().get(0));
        assertEquals("manifest-two",   deserialized.getReconcilerManifests().get(1));
        assertEquals("manifest-three", deserialized.getReconcilerManifests().get(2));
    }
}
