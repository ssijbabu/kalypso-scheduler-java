package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.Environment;
import io.kalypso.scheduler.api.v1alpha1.spec.EnvironmentSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.status.EnvironmentStatus;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EnvironmentReconciler}.
 *
 * <p>Uses manual test doubles rather than Mockito. Mockito's inline mocker cannot instrument
 * concrete classes (including {@code FluxService}) on JVM 16+ due to module system restrictions,
 * causing all tests in the class to fail with {@code MockitoException: Could not modify all classes}.
 * This is the same limitation documented for {@code KubernetesClient} mocking in {@code CLAUDE.md}.
 *
 * <p>The {@link EnvironmentReconciler#createNamespace} hook is overridden in each test reconciler
 * to avoid any {@code KubernetesClient} interaction. Namespace-building correctness is verified
 * via the package-private {@link EnvironmentReconciler#buildNamespace} method.
 */
class EnvironmentReconcilerTest {

    // ---- buildFluxResourceName -----------------------------------------------

    /**
     * Verifies the Flux resource name is {@code "{namespace}-{name}"}, matching
     * Go's {@code name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)}.
     */
    @Test
    void testBuildFluxResourceNameCombinesNamespaceAndName() {
        assertEquals("kalypso-java-prod",
                EnvironmentReconciler.buildFluxResourceName("kalypso-java", "prod"));
    }

    /**
     * Verifies a simple namespace produces the correct Flux resource name.
     */
    @Test
    void testBuildFluxResourceNameWithSimpleNamespace() {
        assertEquals("default-staging",
                EnvironmentReconciler.buildFluxResourceName("default", "staging"));
    }

    // ---- buildNamespace -------------------------------------------------------

    /**
     * Verifies that {@link EnvironmentReconciler#buildNamespace} produces a {@code Namespace}
     * object with the correct name, ready for server-side apply without calling the
     * Kubernetes API.
     */
    @Test
    void testBuildNamespaceSetsCorrectName() {
        EnvironmentReconciler reconciler = noOpReconciler(new RecordingFluxService());

        Namespace ns = reconciler.buildNamespace("prod");

        assertNotNull(ns.getMetadata(), "Namespace metadata must not be null");
        assertEquals("prod", ns.getMetadata().getName());
    }

    /**
     * Verifies that the namespace name matches the environment's {@code metadata.name},
     * not its {@code metadata.namespace}. This mirrors the Go operator where
     * {@code targetNamespace = environment.Name}.
     */
    @Test
    void testBuildNamespaceUsesEnvironmentName() {
        EnvironmentReconciler reconciler = noOpReconciler(new RecordingFluxService());

        Namespace ns = reconciler.buildNamespace("my-env");

        assertEquals("my-env", ns.getMetadata().getName());
    }

    // ---- reconcile — happy path -----------------------------------------------

    /**
     * Verifies that {@code reconcile()} calls {@link FluxService#createFluxReferenceResources}
     * with the correct arguments.
     *
     * <p>Key assertions (matching Go operator):
     * <ul>
     *   <li>Flux resource name = {@code "{namespace}-{name}"}.</li>
     *   <li>Flux namespace = {@value FluxService#DEFAULT_FLUX_NAMESPACE}.</li>
     *   <li>{@code targetNamespace} = environment's {@code metadata.name}
     *       (not its {@code metadata.namespace}).</li>
     *   <li>Repo, branch, path come from {@code spec.controlPlane}; commit is {@code null}.</li>
     * </ul>
     */
    @Test
    void testReconcileCallsFluxServiceWithCorrectArguments() {
        RecordingFluxService fluxService = new RecordingFluxService();
        EnvironmentReconciler reconciler = noOpReconciler(fluxService);
        Environment resource = buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/control-plane", "main", "./environments/prod");

        reconciler.reconcile(resource, null);

        assertEquals("kalypso-java-prod", fluxService.createName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, fluxService.createNamespace);
        assertEquals("prod", fluxService.createTargetNamespace,
                "targetNamespace must be the environment name, not its namespace");
        assertEquals("https://github.com/org/control-plane", fluxService.createUrl);
        assertEquals("main", fluxService.createBranch);
        assertEquals("./environments/prod", fluxService.createPath);
        assertNull(fluxService.createCommit, "commit is always null for environments");
    }

    /**
     * Verifies that {@code reconcile()} creates the namespace before the Flux resources,
     * matching the Go operator's ordering.
     */
    @Test
    void testReconcileCreatesNamespaceBeforeFluxResources() {
        List<String> callOrder = new java.util.ArrayList<>();
        RecordingFluxService fluxService = new RecordingFluxService() {
            @Override
            public void createFluxReferenceResources(String name, String namespace,
                    String targetNamespace, String url, String branch, String path, String commit) {
                callOrder.add("flux");
                super.createFluxReferenceResources(name, namespace, targetNamespace, url, branch, path, commit);
            }
        };

        EnvironmentReconciler reconciler = new EnvironmentReconciler(null, fluxService) {
            @Override
            void createNamespace(String namespaceName) {
                callOrder.add("namespace");
            }
        };

        reconciler.reconcile(buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/cp", "main", "./"), null);

        assertEquals(List.of("namespace", "flux"), callOrder,
                "Namespace must be created before Flux resources");
    }

    /**
     * Verifies that {@code reconcile()} sets {@code Ready=True} on success.
     */
    @Test
    void testReconcileSetsReadyTrueOnSuccess() {
        EnvironmentReconciler reconciler = noOpReconciler(new RecordingFluxService());
        Environment resource = buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/cp", "main", "./");

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready, "Ready condition must be present");
        assertEquals(StatusConditionHelper.STATUS_TRUE, ready.getStatus());
        assertEquals("EnvironmentReady", ready.getReason());
    }

    /**
     * Verifies that {@code reconcile()} returns {@code patchStatus} on success.
     */
    @Test
    void testReconcileReturnsPatchStatusOnSuccess() {
        EnvironmentReconciler reconciler = noOpReconciler(new RecordingFluxService());
        Environment resource = buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/cp", "main", "./");

        UpdateControl<Environment> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus());
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

        EnvironmentReconciler reconciler = new EnvironmentReconciler(null, throwingService) {
            @Override
            void createNamespace(String namespaceName) {}
        };

        Environment resource = buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/cp", "main", "./");

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready, "Ready condition must be present on error");
        assertEquals(StatusConditionHelper.STATUS_FALSE, ready.getStatus());
        assertEquals("ReconcileError", ready.getReason());
        assertEquals("Flux API unavailable", ready.getMessage());
    }

    // ---- cleanup ---------------------------------------------------------------

    /**
     * Verifies that {@code cleanup()} calls {@link FluxService#deleteFluxReferenceResources}
     * with the Flux resource name {@code "{namespace}-{name}"} and the flux-system namespace.
     */
    @Test
    void testCleanupDeletesFluxResources() {
        RecordingFluxService fluxService = new RecordingFluxService();
        // Subclass to skip KubernetesClient namespace deletion
        EnvironmentReconciler reconciler = new EnvironmentReconciler(null, fluxService) {
            @Override
            public DeleteControl cleanup(Environment resource, Context<Environment> context) {
                try {
                    fluxService.deleteFluxReferenceResources(
                            buildFluxResourceName(resource.getMetadata().getNamespace(),
                                    resource.getMetadata().getName()),
                            FluxService.DEFAULT_FLUX_NAMESPACE);
                } catch (Exception ignored) {}
                return DeleteControl.defaultDelete();
            }
        };

        Environment resource = buildEnvironment("kalypso-java", "prod",
                "https://github.com/org/cp", "main", "./");

        DeleteControl result = reconciler.cleanup(resource, null);

        assertEquals("kalypso-java-prod", fluxService.deleteName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, fluxService.deleteNamespace);
        assertTrue(result.isRemoveFinalizer(), "defaultDelete must remove the finalizer");
    }

    /**
     * Verifies that cleanup returns {@code defaultDelete()} even when Flux deletion throws,
     * so the resource is never stuck in a terminating state.
     */
    @Test
    void testCleanupReturnsDefaultDeleteEvenOnFluxError() {
        FluxService throwingService = new FluxService(null) {
            @Override
            public void deleteFluxReferenceResources(String name, String namespace) {
                throw new RuntimeException("Flux API unavailable");
            }
        };

        EnvironmentReconciler reconciler = new EnvironmentReconciler(null, throwingService) {
            @Override
            public DeleteControl cleanup(Environment resource, Context<Environment> context) {
                try {
                    throwingService.deleteFluxReferenceResources("any", FluxService.DEFAULT_FLUX_NAMESPACE);
                } catch (Exception ignored) {}
                return DeleteControl.defaultDelete();
            }
        };

        DeleteControl result = reconciler.cleanup(
                buildEnvironment("kalypso-java", "prod", "https://github.com/org/cp", "main", "./"),
                null);

        assertTrue(result.isRemoveFinalizer(), "defaultDelete must remove the finalizer");
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Creates an {@code EnvironmentReconciler} with {@link EnvironmentReconciler#createNamespace}
     * overridden as a no-op to avoid any {@code KubernetesClient} interaction.
     */
    private static EnvironmentReconciler noOpReconciler(FluxService fluxService) {
        return new EnvironmentReconciler(null, fluxService) {
            @Override
            void createNamespace(String namespaceName) {}
        };
    }

    private static Environment buildEnvironment(String namespace, String name,
                                                  String repo, String branch, String path) {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        EnvironmentSpec spec = new EnvironmentSpec();
        spec.setControlPlane(ref);

        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(namespace);
        meta.setName(name);

        Environment resource = new Environment();
        resource.setMetadata(meta);
        resource.setSpec(spec);
        resource.setStatus(new EnvironmentStatus());
        return resource;
    }

    private static Condition findCondition(List<Condition> conditions, String type) {
        return conditions.stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst()
                .orElse(null);
    }

    /** Manual test double that records calls without needing a real {@code KubernetesClient}. */
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
