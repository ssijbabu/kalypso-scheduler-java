package io.kalypso.scheduler.it;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kalypso.scheduler.api.v1alpha1.Assignment;
import io.kalypso.scheduler.api.v1alpha1.AssignmentList;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackageList;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.ClusterTypeList;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTargetList;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicy;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicyList;
import io.kalypso.scheduler.api.v1alpha1.Template;
import io.kalypso.scheduler.api.v1alpha1.TemplateList;
import io.kalypso.scheduler.api.v1alpha1.Workload;
import io.kalypso.scheduler.api.v1alpha1.WorkloadList;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistration;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistrationList;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.spec.SchedulingPolicySpec;
import io.kalypso.scheduler.api.v1alpha1.spec.Selector;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateManifest;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateType;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadRegistrationSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadTarget;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify reconciler <em>behaviour</em> — child resource creation,
 * label propagation, stale-resource deletion, status condition updates, and the full
 * WorkloadRegistration → Workload → SchedulingPolicy → Assignment → AssignmentPackage chain.
 *
 * <p>These tests run during the {@code integration-test} Maven phase ({@code mvn verify}),
 * after {@code pre-integration-test} has built the Docker image, applied CRDs, and
 * deployed the operator to the {@code kalypso-java} namespace.
 *
 * <p>Tests are ordered with {@link Order} annotations because a subset share object
 * state across methods (e.g., the stale-DT deletion test reuses the workload created
 * in the DT-creation test).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconcilerIntegrationIT {

    private static final String NS = "kalypso-java";
    private static final String DEPLOYMENT_NAME = "kalypso-scheduler";

    /** Label applied by SchedulingPolicyReconciler to every Assignment it creates. */
    private static final String SCHEDULING_POLICY_LABEL = "scheduler.kalypso.io/schedulingPolicy";

    // ---- Workload reconciler test resources (tests 1-3, ordered) ---
    private static final String WL_NAME     = "r-wl";
    private static final String WL_WS       = "r-ws";
    private static final String WL_TARGET   = "east";
    /** Expected DT name: {ns}-{workloadName}-{targetName} */
    private static final String WL_DT       = "kalypso-java-r-wl-east";

    // ---- SchedulingPolicy reconciler test resources ---------------------------
    private static final String SP_DT       = "r-sp-dt";
    private static final String SP_CT       = "r-sp-ct";
    private static final String SP_POL      = "r-sp-pol";
    private static final String SP_WS       = "r-sp-ws";
    /** Expected Assignment name: {policy}-{dt}-{ct} */
    private static final String SP_ASSIGN   = "r-sp-pol-r-sp-dt-r-sp-ct";

    // ---- AssignmentPackage reconciler test resources --------------------------
    private static final String PKG_VALID   = "r-pkg-valid";
    private static final String PKG_INVALID = "r-pkg-invalid";

    // ---- End-to-end chain test resources --------------------------------------
    private static final String E2E_WL      = "r-e2e";
    private static final String E2E_WS      = "r-e2e-ws";
    private static final String E2E_TARGET  = "east";
    /** Expected DT name: {ns}-{wl}-{target} */
    private static final String E2E_DT      = "kalypso-java-r-e2e-east";
    private static final String E2E_CT      = "r-e2e-ct";
    private static final String E2E_TMPL_R  = "r-e2e-rt";
    private static final String E2E_TMPL_N  = "r-e2e-nt";
    private static final String E2E_POL     = "r-e2e-pol";
    /** Expected Assignment + AssignmentPackage name: {pol}-{dt}-{ct} */
    private static final String E2E_ASSIGN  = "r-e2e-pol-kalypso-java-r-e2e-east-r-e2e-ct";

    private KubernetesClient client;

    @BeforeAll
    void setUpAll() {
        client = new KubernetesClientBuilder().build();
        client.apps().deployments()
                .inNamespace(NS).withName(DEPLOYMENT_NAME)
                .waitUntilReady(2, TimeUnit.MINUTES);
    }

    @AfterAll
    void tearDownAll() {
        // Operator-created resources first (otherwise owner-ref GC may beat us to it)
        deleteQuietly(Assignment.class,        AssignmentList.class,        SP_ASSIGN);
        deleteQuietly(Assignment.class,        AssignmentList.class,        E2E_ASSIGN);
        deleteQuietly(AssignmentPackage.class, AssignmentPackageList.class, E2E_ASSIGN);
        deleteQuietly(DeploymentTarget.class,  DeploymentTargetList.class,  WL_DT);
        deleteQuietly(DeploymentTarget.class,  DeploymentTargetList.class,  E2E_DT);
        // Directly-created test fixtures
        deleteQuietly(AssignmentPackage.class, AssignmentPackageList.class, PKG_VALID);
        deleteQuietly(AssignmentPackage.class, AssignmentPackageList.class, PKG_INVALID);
        deleteQuietly(DeploymentTarget.class,  DeploymentTargetList.class,  SP_DT);
        deleteQuietly(SchedulingPolicy.class,  SchedulingPolicyList.class,  SP_POL);
        deleteQuietly(SchedulingPolicy.class,  SchedulingPolicyList.class,  E2E_POL);
        deleteQuietly(ClusterType.class,       ClusterTypeList.class,       SP_CT);
        deleteQuietly(ClusterType.class,       ClusterTypeList.class,       E2E_CT);
        deleteQuietly(Workload.class,          WorkloadList.class,          WL_NAME);
        deleteQuietly(Workload.class,          WorkloadList.class,          E2E_WL);
        deleteQuietly(WorkloadRegistration.class, WorkloadRegistrationList.class, WL_NAME);
        deleteQuietly(WorkloadRegistration.class, WorkloadRegistrationList.class, E2E_WL);
        deleteQuietly(Template.class,          TemplateList.class,          E2E_TMPL_R);
        deleteQuietly(Template.class,          TemplateList.class,          E2E_TMPL_N);
        if (client != null) client.close();
    }

    // =========================================================================
    // WorkloadReconciler
    // =========================================================================

    /**
     * Verifies that {@code WorkloadReconciler} creates a {@code DeploymentTarget} child
     * resource whose name follows the {@code {ns}-{workload}-{target}} convention, and
     * that the required workspace and workload metadata labels are present.
     *
     * <p>Workspace is resolved from the {@code WorkloadRegistration} with the same name.
     */
    @Test
    @Order(1)
    void testWorkloadReconcilerCreatesDeploymentTargetWithCorrectLabels() {
        client.resources(WorkloadRegistration.class, WorkloadRegistrationList.class)
                .inNamespace(NS)
                .resource(buildWorkloadRegistration(WL_NAME, WL_WS))
                .serverSideApply();

        client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NS)
                .resource(buildWorkload(WL_NAME, WL_TARGET))
                .serverSideApply();

        DeploymentTarget dt = pollForResource(DeploymentTarget.class, DeploymentTargetList.class,
                WL_DT, 30);

        assertNotNull(dt,
                "WorkloadReconciler must create DeploymentTarget '" + WL_DT + "' within 30 s");
        assertEquals(WL_WS, dt.getMetadata().getLabels().get(DeploymentTargetSpec.WORKSPACE_LABEL),
                "DeploymentTarget must carry workspace label resolved from WorkloadRegistration");
        assertEquals(WL_NAME, dt.getMetadata().getLabels().get(DeploymentTargetSpec.WORKLOAD_LABEL),
                "DeploymentTarget must carry workload label");
        assertEquals(WL_TARGET, dt.getSpec().getName(),
                "DeploymentTarget spec.name must equal the target name");
    }

    /**
     * Verifies that {@code WorkloadReconciler} sets {@code Ready=True} on the
     * {@code Workload} status after reconciling its deployment targets.
     */
    @Test
    @Order(2)
    void testWorkloadReconcilerSetsReadyCondition() {
        boolean ready = pollForCondition(
                () -> {
                    Workload wl = client.resources(Workload.class, WorkloadList.class)
                            .inNamespace(NS).withName(WL_NAME).get();
                    return wl != null && wl.getStatus() != null
                            ? wl.getStatus().getConditions() : null;
                },
                "Ready", "True", 30);

        assertTrue(ready,
                "WorkloadReconciler must set Ready=True on Workload '" + WL_NAME + "' within 30 s");
    }

    /**
     * Verifies that removing a target from {@code spec.deploymentTargets} causes the
     * {@code WorkloadReconciler} to delete the corresponding {@code DeploymentTarget}.
     *
     * <p>Depends on test 1 having created the workload; runs after it via {@link Order}.
     */
    @Test
    @Order(3)
    void testWorkloadReconcilerDeletesRemovedDeploymentTarget() {
        // Update the Workload to have no targets
        Workload wl = client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NS).withName(WL_NAME).get();
        assertNotNull(wl, "Workload '" + WL_NAME + "' must exist for the stale-DT deletion test");

        wl.getSpec().setDeploymentTargets(List.of());
        // Clear managedFields — SSA rejects a resource that still carries them from a GET.
        wl.getMetadata().setManagedFields(null);
        client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NS).resource(wl).serverSideApply();

        boolean deleted = pollForAbsence(DeploymentTarget.class, DeploymentTargetList.class,
                WL_DT, 30);

        assertTrue(deleted,
                "WorkloadReconciler must delete stale DeploymentTarget '" + WL_DT + "' within 30 s");
    }

    // =========================================================================
    // SchedulingPolicyReconciler
    // =========================================================================

    /**
     * Verifies that {@code SchedulingPolicyReconciler} creates an {@code Assignment}
     * for every matching (DeploymentTarget, ClusterType) pair, names it
     * {@code {policy}-{dt}-{ct}}, and stamps the {@code SCHEDULING_POLICY_LABEL} label.
     */
    @Test
    @Order(4)
    void testSchedulingPolicyReconcilerCreatesAssignmentForMatchingPair() {
        // DeploymentTarget with workspace label (created directly, not by WorkloadReconciler)
        DeploymentTarget dt = new DeploymentTarget();
        dt.setMetadata(new ObjectMetaBuilder()
                .withName(SP_DT).withNamespace(NS)
                .withLabels(Map.of(
                        DeploymentTargetSpec.WORKSPACE_LABEL, SP_WS,
                        DeploymentTargetSpec.WORKLOAD_LABEL,  "r-sp-wl"))
                .build());
        dt.setSpec(new DeploymentTargetSpec());
        dt.getSpec().setName(SP_DT);
        client.resources(DeploymentTarget.class, DeploymentTargetList.class)
                .inNamespace(NS).resource(dt).serverSideApply();

        ClusterType ct = new ClusterType();
        ct.setMetadata(new ObjectMetaBuilder().withName(SP_CT).withNamespace(NS).build());
        ct.setSpec(new ClusterTypeSpec());
        client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NS).resource(ct).serverSideApply();

        SchedulingPolicy sp = new SchedulingPolicy();
        sp.setMetadata(new ObjectMetaBuilder().withName(SP_POL).withNamespace(NS).build());
        Selector dtSel = new Selector();
        dtSel.setWorkspace(SP_WS);
        SchedulingPolicySpec spSpec = new SchedulingPolicySpec();
        spSpec.setDeploymentTargetSelector(dtSel);
        sp.setSpec(spSpec);
        client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                .inNamespace(NS).resource(sp).serverSideApply();

        Assignment assignment = pollForResource(Assignment.class, AssignmentList.class,
                SP_ASSIGN, 30);

        assertNotNull(assignment,
                "SchedulingPolicyReconciler must create Assignment '" + SP_ASSIGN + "' within 30 s");
        assertEquals(SP_CT,  assignment.getSpec().getClusterType());
        assertEquals(SP_DT,  assignment.getSpec().getDeploymentTarget());
        assertEquals(SP_POL, assignment.getMetadata().getLabels().get(SCHEDULING_POLICY_LABEL),
                "Assignment must carry schedulingPolicy label");
    }

    /**
     * Verifies that {@code SchedulingPolicyReconciler} sets {@code Ready=True} after
     * all Assignments are reconciled.
     */
    @Test
    @Order(5)
    void testSchedulingPolicyReconcilerSetsReadyCondition() {
        boolean ready = pollForCondition(
                () -> {
                    SchedulingPolicy sp = client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                            .inNamespace(NS).withName(SP_POL).get();
                    return sp != null && sp.getStatus() != null
                            ? sp.getStatus().getConditions() : null;
                },
                "Ready", "True", 30);

        assertTrue(ready,
                "SchedulingPolicyReconciler must set Ready=True on SchedulingPolicy within 30 s");
    }

    // =========================================================================
    // AssignmentPackageReconciler
    // =========================================================================

    /**
     * Verifies that {@code AssignmentPackageReconciler} sets {@code Ready=True} when
     * all manifests in an {@code AssignmentPackage} are syntactically valid YAML.
     */
    @Test
    @Order(6)
    void testAssignmentPackageReconcilerSetsReadyTrueForValidManifests() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(
                List.of("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test-reconciler\n"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);
        spec.setNamespaceManifests(
                List.of("apiVersion: v1\nkind: Namespace\nmetadata:\n  name: test-ns\n"));
        spec.setNamespaceManifestsContentType(ContentType.YAML);

        AssignmentPackage pkg = new AssignmentPackage();
        pkg.setMetadata(new ObjectMetaBuilder().withName(PKG_VALID).withNamespace(NS).build());
        pkg.setSpec(spec);
        client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                .inNamespace(NS).resource(pkg).serverSideApply();

        boolean ready = pollForCondition(
                () -> fetchAssignmentPackageConditions(PKG_VALID),
                "Ready", "True", 30);

        assertTrue(ready,
                "AssignmentPackageReconciler must set Ready=True for valid YAML manifests within 30 s");
    }

    /**
     * Verifies that {@code AssignmentPackageReconciler} sets {@code Ready=False} when
     * a manifest contains invalid YAML (unclosed bracket).
     */
    @Test
    @Order(7)
    void testAssignmentPackageReconcilerSetsReadyFalseForInvalidManifests() {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(List.of("key: [unclosed bracket"));
        spec.setReconcilerManifestsContentType(ContentType.YAML);

        AssignmentPackage pkg = new AssignmentPackage();
        pkg.setMetadata(new ObjectMetaBuilder().withName(PKG_INVALID).withNamespace(NS).build());
        pkg.setSpec(spec);
        client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                .inNamespace(NS).resource(pkg).serverSideApply();

        boolean notReady = pollForCondition(
                () -> fetchAssignmentPackageConditions(PKG_INVALID),
                "Ready", "False", 30);

        assertTrue(notReady,
                "AssignmentPackageReconciler must set Ready=False for invalid YAML within 30 s");
    }

    // =========================================================================
    // End-to-end reconciliation chain
    // =========================================================================

    /**
     * End-to-end test that exercises the full reconciliation pipeline:
     *
     * <ol>
     *   <li>{@code WorkloadRegistration} created → workspace available for child DTs</li>
     *   <li>{@code Workload} created → {@code WorkloadReconciler} creates a {@code DeploymentTarget}</li>
     *   <li>{@code SchedulingPolicy} created → {@code SchedulingPolicyReconciler} creates an {@code Assignment}</li>
     *   <li>{@code AssignmentReconciler} renders templates → creates an {@code AssignmentPackage}</li>
     *   <li>{@code AssignmentPackageReconciler} validates manifests → sets {@code Ready=True}</li>
     * </ol>
     *
     * <p>Templates are pre-created with simple Freemarker content that renders to valid YAML
     * using standard template context keys ({@code DeploymentTargetName}, {@code ClusterType},
     * {@code Workspace}). No Flux or GitHub integration is required.
     */
    @Test
    @Order(8)
    void testEndToEndChainFromWorkloadToAssignmentPackage() {
        // Step 0: Pre-create templates with simple Freemarker content
        Template rt = buildTemplate(E2E_TMPL_R, TemplateType.RECONCILER,
                "apiVersion: v1\nkind: ConfigMap\n"
                + "metadata:\n  name: r-${DeploymentTargetName}\n"
                + "data:\n  clusterType: ${ClusterType}\n");
        Template nt = buildTemplate(E2E_TMPL_N, TemplateType.NAMESPACE,
                "apiVersion: v1\nkind: ConfigMap\n"
                + "metadata:\n  name: n-${DeploymentTargetName}\n"
                + "data:\n  workspace: ${Workspace}\n");
        client.resources(Template.class, TemplateList.class)
                .inNamespace(NS).resource(rt).serverSideApply();
        client.resources(Template.class, TemplateList.class)
                .inNamespace(NS).resource(nt).serverSideApply();

        // Step 1: ClusterType referencing templates
        ClusterType ct = new ClusterType();
        ct.setMetadata(new ObjectMetaBuilder().withName(E2E_CT).withNamespace(NS).build());
        ClusterTypeSpec ctSpec = new ClusterTypeSpec();
        ctSpec.setReconciler(E2E_TMPL_R);
        ctSpec.setNamespaceService(E2E_TMPL_N);
        ct.setSpec(ctSpec);
        client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NS).resource(ct).serverSideApply();

        // Step 2: WorkloadRegistration gives the workspace value
        client.resources(WorkloadRegistration.class, WorkloadRegistrationList.class)
                .inNamespace(NS)
                .resource(buildWorkloadRegistration(E2E_WL, E2E_WS))
                .serverSideApply();

        // Step 3: Workload triggers DeploymentTarget creation
        client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NS)
                .resource(buildWorkload(E2E_WL, E2E_TARGET))
                .serverSideApply();

        DeploymentTarget dt = pollForResource(DeploymentTarget.class, DeploymentTargetList.class,
                E2E_DT, 30);
        assertNotNull(dt,
                "WorkloadReconciler must create DeploymentTarget '" + E2E_DT + "' within 30 s");
        assertEquals(E2E_WS, dt.getMetadata().getLabels().get(DeploymentTargetSpec.WORKSPACE_LABEL),
                "DeploymentTarget must carry workspace label");

        // Step 4: SchedulingPolicy triggers Assignment creation
        SchedulingPolicy sp = new SchedulingPolicy();
        sp.setMetadata(new ObjectMetaBuilder().withName(E2E_POL).withNamespace(NS).build());
        Selector dtSel = new Selector();
        dtSel.setWorkspace(E2E_WS);
        SchedulingPolicySpec spSpec = new SchedulingPolicySpec();
        spSpec.setDeploymentTargetSelector(dtSel);
        sp.setSpec(spSpec);
        client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                .inNamespace(NS).resource(sp).serverSideApply();

        Assignment assignment = pollForResource(Assignment.class, AssignmentList.class,
                E2E_ASSIGN, 30);
        assertNotNull(assignment,
                "SchedulingPolicyReconciler must create Assignment '" + E2E_ASSIGN + "' within 30 s");
        assertEquals(E2E_CT, assignment.getSpec().getClusterType());
        assertEquals(E2E_DT, assignment.getSpec().getDeploymentTarget());

        // Step 5: AssignmentReconciler renders templates and creates AssignmentPackage
        AssignmentPackage pkg = pollForResource(AssignmentPackage.class, AssignmentPackageList.class,
                E2E_ASSIGN, 60);
        assertNotNull(pkg,
                "AssignmentReconciler must create AssignmentPackage '" + E2E_ASSIGN + "' within 60 s");

        // Step 6: AssignmentPackageReconciler validates the rendered manifests
        boolean pkgReady = pollForCondition(
                () -> fetchAssignmentPackageConditions(E2E_ASSIGN),
                "Ready", "True", 30);
        assertTrue(pkgReady,
                "AssignmentPackageReconciler must set Ready=True on AssignmentPackage within 30 s");

        // Verify rendered manifest content contains the deployment target name
        AssignmentPackage fetched = client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                .inNamespace(NS).withName(E2E_ASSIGN).get();
        List<String> reconcilerManifests = fetched.getSpec().getReconcilerManifests();
        assertFalse(reconcilerManifests.isEmpty(), "Reconciler manifest must be present after rendering");
        assertTrue(reconcilerManifests.get(0).contains(E2E_DT),
                "Rendered reconciler manifest must contain the deployment target name");

        List<String> namespaceManifests = fetched.getSpec().getNamespaceManifests();
        assertFalse(namespaceManifests.isEmpty(), "Namespace manifest must be present after rendering");
        assertTrue(namespaceManifests.get(0).contains(E2E_DT),
                "Rendered namespace manifest must contain the deployment target name");
    }

    // =========================================================================
    // Polling helpers
    // =========================================================================

    /**
     * Polls once per second until the resource appears or the timeout elapses.
     *
     * @return the resource if found within the timeout, {@code null} otherwise
     */
    private <T extends io.fabric8.kubernetes.api.model.HasMetadata,
             L extends io.fabric8.kubernetes.api.model.KubernetesResourceList<T>>
    T pollForResource(Class<T> type, Class<L> listType, String name, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        while (System.currentTimeMillis() < deadline) {
            T resource = client.resources(type, listType).inNamespace(NS).withName(name).get();
            if (resource != null) return resource;
            sleep(1_000);
        }
        return null;
    }

    /**
     * Polls once per second until the resource is absent or the timeout elapses.
     *
     * @return {@code true} if the resource is absent within the timeout, {@code false} otherwise
     */
    private <T extends io.fabric8.kubernetes.api.model.HasMetadata,
             L extends io.fabric8.kubernetes.api.model.KubernetesResourceList<T>>
    boolean pollForAbsence(Class<T> type, Class<L> listType, String name, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        while (System.currentTimeMillis() < deadline) {
            if (client.resources(type, listType).inNamespace(NS).withName(name).get() == null) {
                return true;
            }
            sleep(1_000);
        }
        return false;
    }

    /**
     * Polls once per second until the desired condition type+status appears or the timeout elapses.
     *
     * @param conditionSupplier returns the current conditions list; may return {@code null}
     * @return {@code true} if the condition is observed within the timeout
     */
    private boolean pollForCondition(Supplier<List<Condition>> conditionSupplier,
                                     String conditionType, String expectedStatus,
                                     int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1_000L);
        while (System.currentTimeMillis() < deadline) {
            List<Condition> conditions = conditionSupplier.get();
            if (conditions != null && conditions.stream()
                    .anyMatch(c -> conditionType.equals(c.getType())
                            && expectedStatus.equals(c.getStatus()))) {
                return true;
            }
            sleep(1_000);
        }
        return false;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // Condition fetchers
    // =========================================================================

    private List<Condition> fetchAssignmentPackageConditions(String name) {
        try {
            AssignmentPackage pkg = client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                    .inNamespace(NS).withName(name).get();
            return pkg != null && pkg.getStatus() != null ? pkg.getStatus().getConditions() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Resource builders
    // =========================================================================

    private WorkloadRegistration buildWorkloadRegistration(String name, String workspace) {
        WorkloadRegistration wreg = new WorkloadRegistration();
        wreg.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(NS).build());
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/test-repo");
        ref.setBranch("main");
        ref.setPath("./");
        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkload(ref);
        spec.setWorkspace(workspace);
        wreg.setSpec(spec);
        return wreg;
    }

    private Workload buildWorkload(String name, String targetName) {
        Workload wl = new Workload();
        wl.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(NS).build());
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/test-repo");
        ref.setBranch("main");
        ref.setPath("./targets/" + targetName);
        WorkloadTarget target = new WorkloadTarget();
        target.setName(targetName);
        target.setManifests(ref);
        WorkloadSpec spec = new WorkloadSpec();
        spec.setDeploymentTargets(List.of(target));
        wl.setSpec(spec);
        return wl;
    }

    private Template buildTemplate(String name, TemplateType type, String freemarkerContent) {
        Template t = new Template();
        t.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(NS).build());
        TemplateManifest manifest = new TemplateManifest();
        manifest.setName("manifest.yaml");
        manifest.setTemplate(freemarkerContent);
        manifest.setContentType(ContentType.YAML);
        TemplateSpec spec = new TemplateSpec();
        spec.setType(type);
        spec.setManifests(List.of(manifest));
        t.setSpec(spec);
        return t;
    }

    private <T extends io.fabric8.kubernetes.api.model.HasMetadata,
             L extends io.fabric8.kubernetes.api.model.KubernetesResourceList<T>>
    void deleteQuietly(Class<T> type, Class<L> listType, String name) {
        try {
            client.resources(type, listType).inNamespace(NS).withName(name).delete();
        } catch (Exception ignored) {
        }
    }
}
