package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.BaseRepo;
import io.kalypso.scheduler.api.v1alpha1.spec.BaseRepoSpec;
import io.kalypso.scheduler.api.v1alpha1.status.BaseRepoStatus;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseRepoReconciler}.
 *
 * <p>Uses manual test doubles (anonymous subclasses of {@link FluxService}) rather than
 * Mockito mocks. Mockito's inline mocker cannot instrument concrete classes that transitively
 * extend {@code java.lang.Object} on JVM 16+ with the module system. This is the same
 * limitation that prevents mocking {@code KubernetesClient}, and it affects all concrete
 * classes on JVM 25.
 */
class BaseRepoReconcilerTest {

    // ---- buildFluxResourceName -----------------------------------------------

    /**
     * Verifies that the Flux resource name is built as
     * {@code "{namespace}-{name}"}, matching Go's
     * {@code name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)}.
     */
    @Test
    void testBuildFluxResourceNameCombinesNamespaceAndName() {
        assertEquals("kalypso-java-my-base-repo",
                BaseRepoReconciler.buildFluxResourceName("kalypso-java", "my-base-repo"));
    }

    /**
     * Verifies that a single-segment namespace still produces the correct Flux name.
     */
    @Test
    void testBuildFluxResourceNameWithSimpleNamespace() {
        assertEquals("default-control-plane",
                BaseRepoReconciler.buildFluxResourceName("default", "control-plane"));
    }

    // ---- reconcile — happy path -----------------------------------------------

    /**
     * Verifies that {@code reconcile()} calls {@link FluxService#createFluxReferenceResources}
     * with the correct arguments derived from the {@code BaseRepo} spec.
     *
     * <p>Key assertions (matching Go operator):
     * <ul>
     *   <li>Flux resource name = {@code "{namespace}-{name}"}.</li>
     *   <li>Flux namespace = {@value FluxService#DEFAULT_FLUX_NAMESPACE}.</li>
     *   <li>{@code targetNamespace} = {@code BaseRepo}'s own namespace.</li>
     *   <li>Repo, branch, path, commit come directly from spec.</li>
     * </ul>
     */
    @Test
    void testReconcileCallsFluxServiceWithCorrectArguments() {
        RecordingFluxService fluxService = new RecordingFluxService();
        BaseRepoReconciler reconciler = new BaseRepoReconciler(fluxService);
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./clusters", "abc123");

        reconciler.reconcile(resource, null);

        assertEquals("kalypso-java-my-repo", fluxService.createName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, fluxService.createNamespace);
        assertEquals("kalypso-java", fluxService.createTargetNamespace);
        assertEquals("https://github.com/org/repo", fluxService.createUrl);
        assertEquals("main", fluxService.createBranch);
        assertEquals("./clusters", fluxService.createPath);
        assertEquals("abc123", fluxService.createCommit);
    }

    /**
     * Verifies that {@code reconcile()} sets {@code Ready=True} on success.
     */
    @Test
    void testReconcileSetsReadyTrueOnSuccess() {
        BaseRepoReconciler reconciler = new BaseRepoReconciler(new RecordingFluxService());
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready, "Ready condition must be present");
        assertEquals(StatusConditionHelper.STATUS_TRUE, ready.getStatus());
        assertEquals("FluxResourcesCreated", ready.getReason());
    }

    /**
     * Verifies that {@code reconcile()} returns {@code UpdateControl.patchStatus} on success.
     */
    @Test
    void testReconcileReturnsPatchStatusOnSuccess() {
        BaseRepoReconciler reconciler = new BaseRepoReconciler(new RecordingFluxService());
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        UpdateControl<BaseRepo> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus(), "Must return patchStatus to persist conditions");
    }

    // ---- reconcile — error path -----------------------------------------------

    /**
     * Verifies that when {@link FluxService#createFluxReferenceResources} throws,
     * {@code reconcile()} sets {@code Ready=False} with the error message.
     */
    @Test
    void testReconcileSetsReadyFalseOnFluxError() {
        FluxService throwingService = new FluxService(null) {
            @Override
            public void createFluxReferenceResources(String name, String namespace,
                    String targetNamespace, String url, String branch, String path, String commit) {
                throw new RuntimeException("Flux API unavailable");
            }
        };

        BaseRepoReconciler reconciler = new BaseRepoReconciler(throwingService);
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready, "Ready condition must be present on error");
        assertEquals(StatusConditionHelper.STATUS_FALSE, ready.getStatus());
        assertEquals("FluxError", ready.getReason());
        assertEquals("Flux API unavailable", ready.getMessage());
    }

    /**
     * Verifies that {@code reconcile()} still returns {@code patchStatus} on error so that
     * the {@code Ready=False} condition is persisted to the Kubernetes API.
     */
    @Test
    void testReconcileReturnsPatchStatusOnError() {
        FluxService throwingService = new FluxService(null) {
            @Override
            public void createFluxReferenceResources(String name, String namespace,
                    String targetNamespace, String url, String branch, String path, String commit) {
                throw new RuntimeException("error");
            }
        };

        BaseRepoReconciler reconciler = new BaseRepoReconciler(throwingService);
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        UpdateControl<BaseRepo> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus());
    }

    // ---- cleanup ---------------------------------------------------------------

    /**
     * Verifies that {@code cleanup()} calls {@link FluxService#deleteFluxReferenceResources}
     * with the correct Flux resource name and flux-system namespace.
     */
    @Test
    void testCleanupDeletesFluxResources() {
        RecordingFluxService fluxService = new RecordingFluxService();
        BaseRepoReconciler reconciler = new BaseRepoReconciler(fluxService);
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        DeleteControl result = reconciler.cleanup(resource, null);

        assertEquals("kalypso-java-my-repo", fluxService.deleteName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, fluxService.deleteNamespace);
        assertTrue(result.isRemoveFinalizer(), "defaultDelete must remove the finalizer");
    }

    /**
     * Verifies that {@code cleanup()} still returns {@code defaultDelete()} even when
     * the Flux deletion throws, so the finalizer is always removed.
     */
    @Test
    void testCleanupReturnsDefaultDeleteEvenOnFluxError() {
        FluxService throwingService = new FluxService(null) {
            @Override
            public void deleteFluxReferenceResources(String name, String namespace) {
                throw new RuntimeException("Flux API unavailable");
            }
        };

        BaseRepoReconciler reconciler = new BaseRepoReconciler(throwingService);
        BaseRepo resource = buildBaseRepo("kalypso-java", "my-repo",
                "https://github.com/org/repo", "main", "./", null);

        DeleteControl result = reconciler.cleanup(resource, null);

        assertTrue(result.isRemoveFinalizer(), "defaultDelete must remove the finalizer");
    }

    // ---- helpers ---------------------------------------------------------------

    private static BaseRepo buildBaseRepo(String namespace, String name,
                                           String repo, String branch,
                                           String path, String commit) {
        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo(repo);
        spec.setBranch(branch);
        spec.setPath(path);
        spec.setCommit(commit);

        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(namespace);
        meta.setName(name);

        BaseRepo resource = new BaseRepo();
        resource.setMetadata(meta);
        resource.setSpec(spec);
        resource.setStatus(new BaseRepoStatus());
        return resource;
    }

    private static Condition findCondition(List<Condition> conditions, String type) {
        return conditions.stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst()
                .orElse(null);
    }

    /** Manual test double that records calls to {@link FluxService} without needing a client. */
    private static class RecordingFluxService extends FluxService {
        String createName;
        String createNamespace;
        String createTargetNamespace;
        String createUrl;
        String createBranch;
        String createPath;
        String createCommit;
        String deleteName;
        String deleteNamespace;

        RecordingFluxService() {
            super(null);
        }

        @Override
        public void createFluxReferenceResources(String name, String namespace,
                String targetNamespace, String url, String branch, String path, String commit) {
            this.createName = name;
            this.createNamespace = namespace;
            this.createTargetNamespace = targetNamespace;
            this.createUrl = url;
            this.createBranch = branch;
            this.createPath = path;
            this.createCommit = commit;
        }

        @Override
        public void deleteFluxReferenceResources(String name, String namespace) {
            this.deleteName = name;
            this.deleteNamespace = namespace;
        }
    }
}
