package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.Workload;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadTarget;
import io.kalypso.scheduler.api.v1alpha1.status.WorkloadStatus;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkloadReconciler}.
 *
 * <p>Uses subclass overrides to avoid calling the Kubernetes API — Mockito cannot mock
 * concrete classes on JVM 25 due to module system restrictions.
 */
class WorkloadReconcilerTest {

    // ---- buildDeploymentTargetName -------------------------------------------

    @Test
    void testBuildDeploymentTargetNameCombinesPrefixAndTarget() {
        assertEquals("ns-workload-prod",
                WorkloadReconciler.buildDeploymentTargetName("ns-workload", "prod"));
    }

    @Test
    void testBuildDeploymentTargetNameHandlesSingleSegments() {
        assertEquals("a-b",
                WorkloadReconciler.buildDeploymentTargetName("a", "b"));
    }

    // ---- buildDeploymentTarget -----------------------------------------------

    @Test
    void testBuildDeploymentTargetSetsWorkspaceAndWorkloadLabels() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null);
        DeploymentTarget dt = reconciler.buildDeploymentTarget(
                "ns-wl-prod", "ns", "wl", "uid-123", "team-alpha", targetOf("prod"));

        var labels = dt.getMetadata().getLabels();
        assertEquals("team-alpha", labels.get(DeploymentTargetSpec.WORKSPACE_LABEL));
        assertEquals("wl", labels.get(DeploymentTargetSpec.WORKLOAD_LABEL));
    }

    @Test
    void testBuildDeploymentTargetSetsOwnerReference() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null);
        DeploymentTarget dt = reconciler.buildDeploymentTarget(
                "ns-wl-prod", "ns", "wl", "uid-123", "team-alpha", targetOf("prod"));

        assertEquals(1, dt.getMetadata().getOwnerReferences().size());
        var ref = dt.getMetadata().getOwnerReferences().get(0);
        assertEquals("Workload", ref.getKind());
        assertEquals("wl", ref.getName());
        assertEquals("uid-123", ref.getUid());
        assertTrue(ref.getController());
        assertTrue(ref.getBlockOwnerDeletion());
    }

    @Test
    void testBuildDeploymentTargetSetsSpecNameAndEmptyEnvironment() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null);
        DeploymentTarget dt = reconciler.buildDeploymentTarget(
                "ns-wl-staging", "ns", "wl", "", "ws", targetOf("staging"));

        assertEquals("staging", dt.getSpec().getName());
        assertEquals("", dt.getSpec().getEnvironment());
    }

    @Test
    void testBuildDeploymentTargetSetsNamespaceAndName() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null);
        DeploymentTarget dt = reconciler.buildDeploymentTarget(
                "my-dt-name", "my-ns", "wl", "", "ws", targetOf("t"));

        assertEquals("my-dt-name", dt.getMetadata().getName());
        assertEquals("my-ns", dt.getMetadata().getNamespace());
    }

    // ---- reconcile — happy path -----------------------------------------------

    @Test
    void testReconcileSetsReadyTrueOnSuccess() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null) {
            @Override
            void reconcileDeploymentTargets(Workload resource) { /* no-op */ }
        };
        Workload workload = buildWorkload("ns", "wl");

        reconciler.reconcile(workload, null);

        var conditions = workload.getStatus().getConditions();
        assertFalse(conditions.isEmpty());
        assertEquals("True", conditions.get(0).getStatus());
        assertEquals("DeploymentTargetsReconciled", conditions.get(0).getReason());
    }

    @Test
    void testReconcileReturnsPatchStatus() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null) {
            @Override
            void reconcileDeploymentTargets(Workload resource) {}
        };

        UpdateControl<Workload> result = reconciler.reconcile(buildWorkload("ns", "wl"), null);
        assertTrue(result.isPatchStatus());
    }

    // ---- reconcile — error path -----------------------------------------------

    @Test
    void testReconcileSetsReadyFalseOnError() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null) {
            @Override
            void reconcileDeploymentTargets(Workload resource) {
                throw new RuntimeException("k8s unavailable");
            }
        };
        Workload workload = buildWorkload("ns", "wl");

        reconciler.reconcile(workload, null);

        var conditions = workload.getStatus().getConditions();
        assertFalse(conditions.isEmpty());
        assertEquals("False", conditions.get(0).getStatus());
        assertEquals("ReconcileError", conditions.get(0).getReason());
    }

    // ---- cleanup ---------------------------------------------------------------

    @Test
    void testCleanupReturnsRemoveFinalizer() {
        WorkloadReconciler reconciler = new WorkloadReconciler(null) {
            @Override
            public DeleteControl cleanup(Workload resource,
                    io.javaoperatorsdk.operator.api.reconciler.Context<Workload> context) {
                return DeleteControl.defaultDelete();
            }
        };
        DeleteControl result = reconciler.cleanup(buildWorkload("ns", "wl"), null);
        assertTrue(result.isRemoveFinalizer());
    }

    // ---- helpers ---------------------------------------------------------------

    private static WorkloadTarget targetOf(String name) {
        WorkloadTarget t = new WorkloadTarget();
        t.setName(name);
        return t;
    }

    private static Workload buildWorkload(String namespace, String name) {
        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(namespace);
        meta.setName(name);
        meta.setUid("uid-wl");

        WorkloadSpec spec = new WorkloadSpec();
        spec.setDeploymentTargets(List.of());

        Workload workload = new Workload();
        workload.setMetadata(meta);
        workload.setSpec(spec);
        workload.setStatus(new WorkloadStatus());
        return workload;
    }
}
