package io.kalypso.scheduler.controllers;

import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AssignmentPackageReconciler}.
 *
 * <p>All tested methods are pure in-memory validation — no Kubernetes client needed.
 */
class AssignmentPackageReconcilerTest {

    private final AssignmentPackageReconciler reconciler = new AssignmentPackageReconciler();

    // ---- validateGroup — null / empty inputs ----------------------------------

    @Test
    void testValidateGroupNullManifestsDoesNotThrow() {
        assertDoesNotThrow(() ->
                reconciler.validateGroup("g", null, ContentType.YAML));
    }

    @Test
    void testValidateGroupEmptyManifestsDoesNotThrow() {
        assertDoesNotThrow(() ->
                reconciler.validateGroup("g", List.of(), ContentType.YAML));
    }

    // ---- validateGroup — YAML content type -----------------------------------

    @Test
    void testValidateGroupValidYamlDoesNotThrow() {
        String yaml = "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: my-ns\n";
        assertDoesNotThrow(() ->
                reconciler.validateGroup("reconcilerManifests", List.of(yaml), ContentType.YAML));
    }

    @Test
    void testValidateGroupInvalidYamlThrows() {
        String badYaml = "key: [unclosed bracket";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reconciler.validateGroup("reconcilerManifests", List.of(badYaml), ContentType.YAML));
        assertTrue(ex.getMessage().contains("reconcilerManifests[0]"));
        assertTrue(ex.getMessage().contains("not valid YAML"));
    }

    @Test
    void testValidateGroupNullContentTypeDefaultsToYamlParsing() {
        String yaml = "key: value\n";
        assertDoesNotThrow(() ->
                reconciler.validateGroup("g", List.of(yaml), null));
    }

    @Test
    void testValidateGroupEmptyManifestStringThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reconciler.validateGroup("namespaceManifests", List.of(""), ContentType.YAML));
        assertTrue(ex.getMessage().contains("namespaceManifests[0] is empty"));
    }

    @Test
    void testValidateGroupBlankManifestStringThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reconciler.validateGroup("g", List.of("   "), ContentType.YAML));
        assertTrue(ex.getMessage().contains("g[0] is empty"));
    }

    // ---- validateGroup — SH content type -------------------------------------

    @Test
    void testValidateGroupShContentAllowsNonYaml() {
        String script = "#!/bin/bash\necho hello\n";
        assertDoesNotThrow(() ->
                reconciler.validateGroup("reconcilerManifests", List.of(script), ContentType.SH));
    }

    @Test
    void testValidateGroupShContentRejectsEmpty() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reconciler.validateGroup("reconcilerManifests", List.of(""), ContentType.SH));
        assertTrue(ex.getMessage().contains("reconcilerManifests[0] is empty"));
    }

    // ---- validateGroup — multiple manifests error index ----------------------

    @Test
    void testValidateGroupReportsCorrectIndexOnSecondManifest() {
        String good = "key: value";
        String bad = "key: [unclosed";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                reconciler.validateGroup("g", List.of(good, bad), ContentType.YAML));
        assertTrue(ex.getMessage().contains("g[1]"));
    }

    // ---- validateManifests — full spec ---------------------------------------

    @Test
    void testValidateManifestsValidSpecDoesNotThrow() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("kind: Kustomization\n"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);
        spec.setNamespaceManifests(List.of("kind: Namespace\n"));
        spec.setNamespaceManifestsContentType(ContentType.YAML);

        assertDoesNotThrow(() -> reconciler.validateManifests(spec));
    }

    @Test
    void testValidateManifestsThrowsOnInvalidReconcilerManifest() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("bad: [yaml"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);

        assertThrows(IllegalArgumentException.class,
                () -> reconciler.validateManifests(spec));
    }

    @Test
    void testValidateManifestsThrowsOnInvalidNamespaceManifest() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("key: value"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);
        spec.setNamespaceManifests(List.of("bad: [yaml"));
        spec.setNamespaceManifestsContentType(ContentType.YAML);

        assertThrows(IllegalArgumentException.class,
                () -> reconciler.validateManifests(spec));
    }

    @Test
    void testValidateManifestsEmptyGroupsDoNotThrow() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        // all lists remain empty (default)
        assertDoesNotThrow(() -> reconciler.validateManifests(spec));
    }
}
