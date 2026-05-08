package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistration;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadRegistrationSpec;
import io.kalypso.scheduler.api.v1alpha1.status.WorkloadRegistrationStatus;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkloadRegistrationReconciler}.
 *
 * <p>Uses manual {@link FluxService} test doubles — Mockito cannot mock concrete
 * classes on JVM 25 due to module system restrictions.
 */
class WorkloadRegistrationReconcilerTest {

    // ---- buildFluxResourceName -----------------------------------------------

    @Test
    void testBuildFluxResourceNameCombinesNamespaceAndName() {
        assertEquals("kalypso-java-my-registration",
                WorkloadRegistrationReconciler.buildFluxResourceName("kalypso-java", "my-registration"));
    }

    // ---- reconcile — happy path -----------------------------------------------

    @Test
    void testReconcileCallsFluxServiceWithCorrectArguments() {
        RecordingFluxService flux = new RecordingFluxService();
        WorkloadRegistrationReconciler reconciler = new WorkloadRegistrationReconciler(flux);
        WorkloadRegistration resource = buildResource("kalypso-java", "my-reg",
                "https://github.com/org/wl", "main", "./apps/myapp");

        reconciler.reconcile(resource, null);

        assertEquals("kalypso-java-my-reg", flux.createName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, flux.createNamespace);
        assertEquals("kalypso-java", flux.createTargetNamespace);
        assertEquals("https://github.com/org/wl", flux.createUrl);
        assertEquals("main", flux.createBranch);
        assertEquals("./apps/myapp", flux.createPath);
        assertNull(flux.createCommit);
    }

    @Test
    void testReconcileSetsReadyTrueOnSuccess() {
        WorkloadRegistrationReconciler reconciler =
                new WorkloadRegistrationReconciler(new RecordingFluxService());
        WorkloadRegistration resource = buildResource("ns", "reg",
                "https://github.com/org/wl", "main", "./");

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready);
        assertEquals(StatusConditionHelper.STATUS_TRUE, ready.getStatus());
        assertEquals("FluxResourcesCreated", ready.getReason());
    }

    @Test
    void testReconcileReturnsPatchStatus() {
        WorkloadRegistrationReconciler reconciler =
                new WorkloadRegistrationReconciler(new RecordingFluxService());
        WorkloadRegistration resource = buildResource("ns", "reg",
                "https://github.com/org/wl", "main", "./");

        UpdateControl<WorkloadRegistration> result = reconciler.reconcile(resource, null);

        assertTrue(result.isPatchStatus());
    }

    // ---- reconcile — error path -----------------------------------------------

    @Test
    void testReconcileSetsReadyFalseOnFluxError() {
        FluxService throwingFlux = new FluxService(null) {
            @Override
            public void createFluxReferenceResources(String name, String namespace,
                    String targetNamespace, String url, String branch, String path, String commit) {
                throw new RuntimeException("Flux unavailable");
            }
        };
        WorkloadRegistrationReconciler reconciler = new WorkloadRegistrationReconciler(throwingFlux);
        WorkloadRegistration resource = buildResource("ns", "reg",
                "https://github.com/org/wl", "main", "./");

        reconciler.reconcile(resource, null);

        Condition ready = findCondition(resource.getStatus().getConditions(),
                StatusConditionHelper.CONDITION_TYPE_READY);
        assertNotNull(ready);
        assertEquals(StatusConditionHelper.STATUS_FALSE, ready.getStatus());
        assertEquals("FluxError", ready.getReason());
    }

    // ---- cleanup ---------------------------------------------------------------

    @Test
    void testCleanupDeletesFluxResourcesAndReturnsDefaultDelete() {
        RecordingFluxService flux = new RecordingFluxService();
        WorkloadRegistrationReconciler reconciler = new WorkloadRegistrationReconciler(flux);
        WorkloadRegistration resource = buildResource("kalypso-java", "my-reg",
                "https://github.com/org/wl", "main", "./");

        DeleteControl result = reconciler.cleanup(resource, null);

        assertEquals("kalypso-java-my-reg", flux.deleteName);
        assertEquals(FluxService.DEFAULT_FLUX_NAMESPACE, flux.deleteNamespace);
        assertTrue(result.isRemoveFinalizer());
    }

    @Test
    void testCleanupReturnsDefaultDeleteEvenOnFluxError() {
        FluxService throwingFlux = new FluxService(null) {
            @Override
            public void deleteFluxReferenceResources(String name, String namespace) {
                throw new RuntimeException("Flux unavailable");
            }
        };
        WorkloadRegistrationReconciler reconciler = new WorkloadRegistrationReconciler(throwingFlux);
        WorkloadRegistration resource = buildResource("ns", "reg",
                "https://github.com/org/wl", "main", "./");

        DeleteControl result = reconciler.cleanup(resource, null);
        assertTrue(result.isRemoveFinalizer());
    }

    // ---- helpers ---------------------------------------------------------------

    private static WorkloadRegistration buildResource(String namespace, String name,
                                                       String repo, String branch, String path) {
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkload(ref);
        spec.setWorkspace("team-alpha");

        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(namespace);
        meta.setName(name);

        WorkloadRegistration resource = new WorkloadRegistration();
        resource.setMetadata(meta);
        resource.setSpec(spec);
        resource.setStatus(new WorkloadRegistrationStatus());
        return resource;
    }

    private static Condition findCondition(List<Condition> conditions, String type) {
        return conditions.stream().filter(c -> type.equals(c.getType())).findFirst().orElse(null);
    }

    private static class RecordingFluxService extends FluxService {
        String createName, createNamespace, createTargetNamespace,
               createUrl, createBranch, createPath, createCommit;
        String deleteName, deleteNamespace;

        RecordingFluxService() { super(null); }

        @Override
        public void createFluxReferenceResources(String name, String namespace,
                String targetNamespace, String url, String branch, String path, String commit) {
            this.createName = name; this.createNamespace = namespace;
            this.createTargetNamespace = targetNamespace; this.createUrl = url;
            this.createBranch = branch; this.createPath = path; this.createCommit = commit;
        }

        @Override
        public void deleteFluxReferenceResources(String name, String namespace) {
            this.deleteName = name; this.deleteNamespace = namespace;
        }
    }
}
