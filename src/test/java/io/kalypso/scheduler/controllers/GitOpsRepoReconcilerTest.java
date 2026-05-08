package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.services.GitHubService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GitOpsRepoReconciler}.
 *
 * <p>Tests focus on the pure helper methods: {@link GitOpsRepoReconciler#extractRepoFullName}
 * (static) and {@link GitOpsRepoReconciler#buildFileContents} (package-private, no client
 * needed). Client-dependent methods ({@code allPoliciesReady}, {@code reconcile}) are
 * covered at the integration test level. Mockito is intentionally avoided — it cannot mock
 * concrete classes on JVM 25.
 */
class GitOpsRepoReconcilerTest {

    private final GitOpsRepoReconciler reconciler = new GitOpsRepoReconciler(null, null);

    // ---- extractRepoFullName --------------------------------------------------

    @Test
    void testExtractRepoFullNameStripsHttpsGithubPrefix() {
        assertEquals("org/repo",
                GitOpsRepoReconciler.extractRepoFullName("https://github.com/org/repo"));
    }

    @Test
    void testExtractRepoFullNameStripsGitSuffix() {
        assertEquals("org/repo",
                GitOpsRepoReconciler.extractRepoFullName("https://github.com/org/repo.git"));
    }

    @Test
    void testExtractRepoFullNameHandlesArbitraryHost() {
        assertEquals("org/repo",
                GitOpsRepoReconciler.extractRepoFullName("https://myghe.example.com/org/repo"));
    }

    @Test
    void testExtractRepoFullNameAlreadyShortForm() {
        assertEquals("org/repo",
                GitOpsRepoReconciler.extractRepoFullName("org/repo"));
    }

    @Test
    void testExtractRepoFullNameThrowsOnNull() {
        assertThrows(IllegalArgumentException.class,
                () -> GitOpsRepoReconciler.extractRepoFullName(null));
    }

    @Test
    void testExtractRepoFullNameThrowsOnBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> GitOpsRepoReconciler.extractRepoFullName("   "));
    }

    // ---- buildFileContents ---------------------------------------------------

    @Test
    void testBuildFileContentsBuildsCorrectPathsForYamlPackage() {
        AssignmentPackage pkg = buildPackage("ct-prod", "dt-east",
                List.of("reconciler-content"), ContentType.YAML,
                List.of("namespace-content"), ContentType.YAML,
                null, null);

        Map<String, String> files = reconciler.buildFileContents("clusters", List.of(pkg));

        String reconcilerPath = GitHubService.buildFilePath(
                "clusters", "ct-prod", "dt-east", GitHubService.RECONCILER_NAME + ".yaml");
        String namespacePath = GitHubService.buildFilePath(
                "clusters", "ct-prod", "dt-east", GitHubService.NAMESPACE_NAME + ".yaml");

        assertEquals("reconciler-content", files.get(reconcilerPath));
        assertEquals("namespace-content", files.get(namespacePath));
        assertFalse(files.containsKey(
                GitHubService.buildFilePath("clusters", "ct-prod", "dt-east",
                        GitHubService.CONFIG_NAME + ".yaml")));
    }

    @Test
    void testBuildFileContentsIncludesConfigManifestWhenPresent() {
        AssignmentPackage pkg = buildPackage("ct", "dt",
                List.of("reconciler"), ContentType.YAML,
                List.of("namespace"), ContentType.YAML,
                List.of("config-content"), ContentType.YAML);

        Map<String, String> files = reconciler.buildFileContents("base", List.of(pkg));

        String configPath = GitHubService.buildFilePath(
                "base", "ct", "dt", GitHubService.CONFIG_NAME + ".yaml");
        assertEquals("config-content", files.get(configPath));
    }

    @Test
    void testBuildFileContentsUsesShExtensionForShContent() {
        AssignmentPackage pkg = buildPackage("ct", "dt",
                List.of("#!/bin/bash"), ContentType.SH,
                null, null, null, null);

        Map<String, String> files = reconciler.buildFileContents("base", List.of(pkg));

        String shPath = GitHubService.buildFilePath(
                "base", "ct", "dt", GitHubService.RECONCILER_NAME + ".sh");
        assertTrue(files.containsKey(shPath));
    }

    @Test
    void testBuildFileContentsAggregatesMultiplePackages() {
        AssignmentPackage pkg1 = buildPackage("ct-a", "dt-1",
                List.of("r1"), ContentType.YAML, null, null, null, null);
        AssignmentPackage pkg2 = buildPackage("ct-b", "dt-2",
                List.of("r2"), ContentType.YAML, null, null, null, null);

        Map<String, String> files = reconciler.buildFileContents("base", List.of(pkg1, pkg2));

        assertEquals(2, files.size());
        assertTrue(files.containsKey(GitHubService.buildFilePath(
                "base", "ct-a", "dt-1", GitHubService.RECONCILER_NAME + ".yaml")));
        assertTrue(files.containsKey(GitHubService.buildFilePath(
                "base", "ct-b", "dt-2", GitHubService.RECONCILER_NAME + ".yaml")));
    }

    @Test
    void testBuildFileContentsSkipsPackageWithMissingLabels() {
        AssignmentPackage pkg = buildPackage(null, "dt",
                List.of("content"), ContentType.YAML, null, null, null, null);

        Map<String, String> files = reconciler.buildFileContents("base", List.of(pkg));
        assertTrue(files.isEmpty());
    }

    @Test
    void testBuildFileContentsReturnsEmptyForEmptyPackageList() {
        Map<String, String> files = reconciler.buildFileContents("base", List.of());
        assertTrue(files.isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Builds a minimal {@link AssignmentPackage} with the given cluster type and
     * deployment target labels and optional manifest groups.
     */
    private static AssignmentPackage buildPackage(
            String clusterType, String deploymentTarget,
            List<String> reconcilerManifests, ContentType reconcilerType,
            List<String> namespaceManifests, ContentType namespaceType,
            List<String> configManifests, ContentType configType) {

        ObjectMeta meta = new ObjectMeta();
        meta.setName("pkg");
        if (clusterType != null || deploymentTarget != null) {
            var labels = new java.util.HashMap<String, String>();
            if (clusterType != null)
                labels.put(AssignmentPackageSpec.CLUSTER_TYPE_LABEL, clusterType);
            if (deploymentTarget != null)
                labels.put(AssignmentPackageSpec.DEPLOYMENT_TARGET_LABEL, deploymentTarget);
            meta.setLabels(labels);
        }

        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        if (reconcilerManifests != null) {
            spec.setReconcilerManifests(reconcilerManifests);
            spec.setReconcilerManifestsContentType(reconcilerType);
        }
        if (namespaceManifests != null) {
            spec.setNamespaceManifests(namespaceManifests);
            spec.setNamespaceManifestsContentType(namespaceType);
        }
        if (configManifests != null) {
            spec.setConfigManifests(configManifests);
            spec.setConfigManifestsContentType(configType);
        }

        AssignmentPackage pkg = new AssignmentPackage();
        pkg.setMetadata(meta);
        pkg.setSpec(spec);
        return pkg;
    }
}
