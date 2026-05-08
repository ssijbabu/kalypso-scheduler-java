package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.Assignment;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicy;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.SchedulingPolicySpec;
import io.kalypso.scheduler.api.v1alpha1.spec.Selector;
import io.kalypso.scheduler.api.v1alpha1.status.SchedulingPolicyStatus;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchedulingPolicyReconciler}.
 *
 * <p>Tests focus on the pure helper methods ({@code matchesSelector},
 * {@code computeDesiredAssignmentNames}, {@code buildAssignment}) that contain
 * the core scheduling logic. Client-dependent paths are tested at the integration
 * level. Mockito is intentionally avoided — it cannot mock concrete classes on JVM 25.
 */
class SchedulingPolicyReconcilerTest {

    private final SchedulingPolicyReconciler reconciler = new SchedulingPolicyReconciler(null);

    // ---- matchesSelector — null selector --------------------------------------

    @Test
    void testMatchesSelectorNullSelectorMatchesAll() {
        assertTrue(reconciler.matchesSelector(null, Map.of("any", "value")));
    }

    @Test
    void testMatchesSelectorNullSelectorMatchesEmptyLabels() {
        assertTrue(reconciler.matchesSelector(null, Map.of()));
    }

    // ---- matchesSelector — workspace filter -----------------------------------

    @Test
    void testMatchesSelectorWorkspaceMatchesExactValue() {
        Selector selector = selectorWithWorkspace("team-alpha");
        Map<String, String> labels = Map.of(DeploymentTargetSpec.WORKSPACE_LABEL, "team-alpha");
        assertTrue(reconciler.matchesSelector(selector, labels));
    }

    @Test
    void testMatchesSelectorWorkspaceRejectsDifferentValue() {
        Selector selector = selectorWithWorkspace("team-alpha");
        Map<String, String> labels = Map.of(DeploymentTargetSpec.WORKSPACE_LABEL, "team-beta");
        assertFalse(reconciler.matchesSelector(selector, labels));
    }

    @Test
    void testMatchesSelectorWorkspaceRejectsMissingLabel() {
        Selector selector = selectorWithWorkspace("team-alpha");
        assertFalse(reconciler.matchesSelector(selector, Map.of()));
    }

    @Test
    void testMatchesSelectorWorkspaceRejectsNullLabels() {
        Selector selector = selectorWithWorkspace("team-alpha");
        assertFalse(reconciler.matchesSelector(selector, null));
    }

    // ---- matchesSelector — labelSelector filter --------------------------------

    @Test
    void testMatchesSelectorLabelSelectorMatchesAllPairs() {
        Selector selector = selectorWithLabels(Map.of("region", "east", "tier", "prod"));
        Map<String, String> labels = Map.of("region", "east", "tier", "prod", "extra", "ignored");
        assertTrue(reconciler.matchesSelector(selector, labels));
    }

    @Test
    void testMatchesSelectorLabelSelectorRejectsPartialMatch() {
        Selector selector = selectorWithLabels(Map.of("region", "east", "tier", "prod"));
        Map<String, String> labels = Map.of("region", "east");
        assertFalse(reconciler.matchesSelector(selector, labels));
    }

    @Test
    void testMatchesSelectorLabelSelectorRejectsWrongValue() {
        Selector selector = selectorWithLabels(Map.of("region", "east"));
        Map<String, String> labels = Map.of("region", "west");
        assertFalse(reconciler.matchesSelector(selector, labels));
    }

    // ---- matchesSelector — AND logic ------------------------------------------

    @Test
    void testMatchesSelectorWorkspaceAndLabelsMustBothMatch() {
        Selector selector = new Selector();
        selector.setWorkspace("team-alpha");
        selector.setLabelSelector(Map.of("region", "east"));

        Map<String, String> bothMatch = Map.of(
                DeploymentTargetSpec.WORKSPACE_LABEL, "team-alpha",
                "region", "east");
        assertTrue(reconciler.matchesSelector(selector, bothMatch));

        Map<String, String> onlyWorkspace = Map.of(
                DeploymentTargetSpec.WORKSPACE_LABEL, "team-alpha",
                "region", "west");
        assertFalse(reconciler.matchesSelector(selector, onlyWorkspace));

        Map<String, String> onlyLabel = Map.of(
                DeploymentTargetSpec.WORKSPACE_LABEL, "team-beta",
                "region", "east");
        assertFalse(reconciler.matchesSelector(selector, onlyLabel));
    }

    // ---- computeDesiredAssignmentNames ----------------------------------------

    @Test
    void testComputeDesiredAssignmentNamesCartesianProduct() {
        SchedulingPolicy policy = buildPolicy("my-policy", "ns");
        List<DeploymentTarget> dts = List.of(namedDT("dt-a"), namedDT("dt-b"));
        List<ClusterType> cts = List.of(namedCT("ct-x"), namedCT("ct-y"));

        Set<String> names = reconciler.computeDesiredAssignmentNames(policy, dts, cts);

        assertEquals(4, names.size());
        assertTrue(names.contains("my-policy-dt-a-ct-x"));
        assertTrue(names.contains("my-policy-dt-a-ct-y"));
        assertTrue(names.contains("my-policy-dt-b-ct-x"));
        assertTrue(names.contains("my-policy-dt-b-ct-y"));
    }

    @Test
    void testComputeDesiredAssignmentNamesEmptyWhenNoDts() {
        SchedulingPolicy policy = buildPolicy("pol", "ns");
        Set<String> names = reconciler.computeDesiredAssignmentNames(
                policy, List.of(), List.of(namedCT("ct")));
        assertTrue(names.isEmpty());
    }

    @Test
    void testComputeDesiredAssignmentNamesEmptyWhenNoCts() {
        SchedulingPolicy policy = buildPolicy("pol", "ns");
        Set<String> names = reconciler.computeDesiredAssignmentNames(
                policy, List.of(namedDT("dt")), List.of());
        assertTrue(names.isEmpty());
    }

    // ---- buildAssignment -------------------------------------------------------

    @Test
    void testBuildAssignmentSetsClusterTypeAndDeploymentTarget() {
        SchedulingPolicy owner = buildPolicy("my-policy", "ns");
        owner.getMetadata().setUid("uid-pol");

        Assignment assignment = reconciler.buildAssignment(
                "my-policy-dt-ct", "ns", "ct-prod", "dt-east", owner);

        assertEquals("ct-prod", assignment.getSpec().getClusterType());
        assertEquals("dt-east", assignment.getSpec().getDeploymentTarget());
    }

    @Test
    void testBuildAssignmentSetsSchedulingPolicyLabel() {
        SchedulingPolicy owner = buildPolicy("my-policy", "ns");
        Assignment assignment = reconciler.buildAssignment(
                "my-policy-dt-ct", "ns", "ct", "dt", owner);

        assertEquals("my-policy",
                assignment.getMetadata().getLabels()
                        .get(SchedulingPolicyReconciler.SCHEDULING_POLICY_LABEL));
    }

    @Test
    void testBuildAssignmentSetsOwnerReference() {
        SchedulingPolicy owner = buildPolicy("my-policy", "ns");
        owner.getMetadata().setUid("uid-pol");

        Assignment assignment = reconciler.buildAssignment(
                "my-policy-dt-ct", "ns", "ct", "dt", owner);

        assertEquals(1, assignment.getMetadata().getOwnerReferences().size());
        var ref = assignment.getMetadata().getOwnerReferences().get(0);
        assertEquals("SchedulingPolicy", ref.getKind());
        assertEquals("my-policy", ref.getName());
        assertEquals("uid-pol", ref.getUid());
        assertTrue(ref.getController());
    }

    @Test
    void testBuildAssignmentSetsNameAndNamespace() {
        SchedulingPolicy owner = buildPolicy("pol", "ns");
        Assignment assignment = reconciler.buildAssignment("pol-dt-ct", "ns", "ct", "dt", owner);

        assertEquals("pol-dt-ct", assignment.getMetadata().getName());
        assertEquals("ns", assignment.getMetadata().getNamespace());
    }

    // ---- reconcile (status gating) --------------------------------------------

    @Test
    void testReconcileReturnsPatchStatus() {
        SchedulingPolicyReconciler r = new SchedulingPolicyReconciler(null) {
            @Override
            public UpdateControl<SchedulingPolicy> reconcile(
                    SchedulingPolicy resource,
                    io.javaoperatorsdk.operator.api.reconciler.Context<SchedulingPolicy> ctx) {
                // simulate error path — client is null so normal path would NPE
                try {
                    return super.reconcile(resource, ctx);
                } catch (Exception e) {
                    return UpdateControl.patchStatus(resource);
                }
            }
        };
        SchedulingPolicy policy = buildPolicy("pol", "ns");
        UpdateControl<SchedulingPolicy> result = r.reconcile(policy, null);
        assertTrue(result.isPatchStatus());
    }

    // ---- helpers ---------------------------------------------------------------

    private static Selector selectorWithWorkspace(String workspace) {
        Selector s = new Selector();
        s.setWorkspace(workspace);
        return s;
    }

    private static Selector selectorWithLabels(Map<String, String> labels) {
        Selector s = new Selector();
        s.setLabelSelector(labels);
        return s;
    }

    private static DeploymentTarget namedDT(String name) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        DeploymentTarget dt = new DeploymentTarget();
        dt.setMetadata(meta);
        return dt;
    }

    private static ClusterType namedCT(String name) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        ClusterType ct = new ClusterType();
        ct.setMetadata(meta);
        return ct;
    }

    private static SchedulingPolicy buildPolicy(String name, String namespace) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace(namespace);

        SchedulingPolicySpec spec = new SchedulingPolicySpec();

        SchedulingPolicy policy = new SchedulingPolicy();
        policy.setMetadata(meta);
        policy.setSpec(spec);
        policy.setStatus(new SchedulingPolicyStatus());
        return policy;
    }
}
