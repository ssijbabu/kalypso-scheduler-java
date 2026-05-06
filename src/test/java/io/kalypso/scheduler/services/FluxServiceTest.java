package io.kalypso.scheduler.services;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FluxService}.
 *
 * <p>These tests cover the package-private {@link FluxService#buildGitRepository}
 * and {@link FluxService#buildKustomization} methods, which contain all the non-trivial
 * logic: constructing the correct {@code apiVersion}, {@code kind}, metadata, and
 * {@code spec} fields for each Flux resource type.
 *
 * <p>No {@link io.fabric8.kubernetes.client.KubernetesClient} mock is used: the build
 * methods are pure functions that do not call the client. End-to-end verification that
 * built resources are correctly submitted is covered by the integration test.
 *
 * <p>Field expectations are derived directly from the Go operator's {@code flux.go}:
 * {@code secretRef} → {@code RepoSecretName}, {@code prune} → {@code true},
 * {@code interval} → {@code FluxInterval} (10 s).
 */
class FluxServiceTest {

    private static final String TEST_SECRET = "gh-repo-secret";

    private FluxService service;

    @BeforeEach
    void setUp() {
        // client is null — build methods do not invoke it
        service = new FluxService(null, TEST_SECRET);
    }

    /**
     * Verifies that {@link FluxService#buildGitRepository} sets the correct
     * {@code apiVersion} and {@code kind} for the Flux source API (v1).
     */
    @Test
    void testBuildGitRepositoryApiVersionAndKind() {
        GenericKubernetesResource resource = service.buildGitRepository(
                "my-repo", FluxService.DEFAULT_FLUX_NAMESPACE,
                "https://github.com/org/repo", "main", null);

        assertEquals("source.toolkit.fluxcd.io/v1", resource.getApiVersion());
        assertEquals("GitRepository", resource.getKind());
    }

    /**
     * Verifies that {@link FluxService#buildGitRepository} populates {@code spec.url},
     * {@code spec.ref.branch}, {@code spec.interval}, and {@code spec.secretRef.name}
     * from the supplied arguments and configured secret.
     *
     * <p>The {@code secretRef} check mirrors the Go operator:
     * {@code gitRepo.Spec.SecretRef = &meta.LocalObjectReference{Name: RepoSecretName}}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testBuildGitRepositoryPopulatesAllRequiredFields() {
        GenericKubernetesResource resource = service.buildGitRepository(
                "control-plane", FluxService.DEFAULT_FLUX_NAMESPACE,
                "https://github.com/org/control-plane", "release/v2", null);

        Map<String, Object> spec = (Map<String, Object>) resource.getAdditionalProperties().get("spec");
        assertNotNull(spec, "spec must be present");
        assertEquals("https://github.com/org/control-plane", spec.get("url"));
        assertEquals(FluxService.DEFAULT_INTERVAL, spec.get("interval"));

        Map<String, Object> ref = (Map<String, Object>) spec.get("ref");
        assertNotNull(ref, "ref must be present");
        assertEquals("release/v2", ref.get("branch"));
        assertNull(ref.get("commit"), "commit must be absent when not pinning");

        Map<String, Object> secretRef = (Map<String, Object>) spec.get("secretRef");
        assertNotNull(secretRef, "secretRef must be present for private repo auth");
        assertEquals(TEST_SECRET, secretRef.get("name"));
    }

    /**
     * Verifies that a non-empty commit SHA is included in {@code spec.ref.commit}
     * and that an empty string ({@code ""}) is treated the same as {@code null}
     * (commit field absent), matching the Go operator's behaviour of passing {@code ""}
     * when not pinning to a specific SHA.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testBuildGitRepositoryHandlesCommitPinning() {
        GenericKubernetesResource withCommit = service.buildGitRepository(
                "my-repo", FluxService.DEFAULT_FLUX_NAMESPACE,
                "https://github.com/org/repo", "main", "abc123");
        Map<String, Object> refWithCommit = (Map<String, Object>)
                ((Map<String, Object>) withCommit.getAdditionalProperties().get("spec")).get("ref");
        assertEquals("abc123", refWithCommit.get("commit"), "commit SHA must be present");

        GenericKubernetesResource withEmptyCommit = service.buildGitRepository(
                "my-repo", FluxService.DEFAULT_FLUX_NAMESPACE,
                "https://github.com/org/repo", "main", "");
        Map<String, Object> refWithEmpty = (Map<String, Object>)
                ((Map<String, Object>) withEmptyCommit.getAdditionalProperties().get("spec")).get("ref");
        assertNull(refWithEmpty.get("commit"), "empty commit string must be treated as absent");
    }

    /**
     * Verifies that {@link FluxService#buildKustomization} sets the correct
     * {@code apiVersion} and {@code kind} for the Flux kustomize API (v1).
     */
    @Test
    void testBuildKustomizationApiVersionAndKind() {
        GenericKubernetesResource resource = service.buildKustomization(
                "my-kustomization", FluxService.DEFAULT_FLUX_NAMESPACE,
                "kalypso-java", "my-repo", "./");

        assertEquals("kustomize.toolkit.fluxcd.io/v1", resource.getApiVersion());
        assertEquals("Kustomization", resource.getKind());
    }

    /**
     * Verifies that {@link FluxService#buildKustomization} populates all required
     * spec fields, including {@code prune: true} (matches Go's {@code Spec.Prune = true})
     * and that {@code targetNamespace} is distinct from {@code namespace}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testBuildKustomizationPopulatesAllRequiredFields() {
        GenericKubernetesResource resource = service.buildKustomization(
                "control-plane", FluxService.DEFAULT_FLUX_NAMESPACE,
                "kalypso-java", "control-plane", "./environments/prod");

        Map<String, Object> spec = (Map<String, Object>) resource.getAdditionalProperties().get("spec");
        assertNotNull(spec, "spec must be present");
        assertEquals("./environments/prod", spec.get("path"));
        assertEquals(FluxService.DEFAULT_INTERVAL, spec.get("interval"));
        assertEquals(Boolean.TRUE, spec.get("prune"),
                "prune must be true — matches Go operator Spec.Prune = true");
        assertEquals("kalypso-java", spec.get("targetNamespace"),
                "targetNamespace must be the CRD's namespace, not flux-system");

        Map<String, Object> sourceRef = (Map<String, Object>) spec.get("sourceRef");
        assertNotNull(sourceRef, "sourceRef must be present");
        assertEquals("GitRepository", sourceRef.get("kind"));
        assertEquals("control-plane", sourceRef.get("name"));
    }
}
