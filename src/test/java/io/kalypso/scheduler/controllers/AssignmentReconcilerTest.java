package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.model.TemplateContext;
import io.kalypso.scheduler.services.ConfigValidationService;
import io.kalypso.scheduler.services.TemplateProcessingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AssignmentReconciler}.
 *
 * <p>Focuses on {@link AssignmentReconciler#buildTemplateContext}, which is the
 * core pure-function step with no Kubernetes API dependencies. Client-dependent
 * paths ({@code gatherConfigData}, {@code validateConfigData},
 * {@code buildAssignmentPackage}) are covered at the integration test level.
 * Mockito is intentionally avoided — it cannot mock concrete classes on JVM 25.
 */
class AssignmentReconcilerTest {

    private final AssignmentReconciler reconciler =
            new AssignmentReconciler(null, new TemplateProcessingService(),
                    new ConfigValidationService());

    // ---- buildTemplateContext — workspace / workload from DT labels -----------

    @Test
    void testBuildTemplateContextReadsWorkspaceFromDtLabel() {
        ClusterType ct = namedCT("small");
        DeploymentTarget dt = buildDT("dt-east", "prod", Map.of(
                DeploymentTargetSpec.WORKSPACE_LABEL, "team-alpha",
                DeploymentTargetSpec.WORKLOAD_LABEL, "my-workload"));

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("team-alpha", ctx.getWorkspace());
    }

    @Test
    void testBuildTemplateContextReadsWorkloadFromDtLabel() {
        ClusterType ct = namedCT("small");
        DeploymentTarget dt = buildDT("dt-east", "prod", Map.of(
                DeploymentTargetSpec.WORKSPACE_LABEL, "ws",
                DeploymentTargetSpec.WORKLOAD_LABEL, "my-workload"));

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("my-workload", ctx.getWorkload());
    }

    @Test
    void testBuildTemplateContextDefaultsWorkspaceAndWorkloadToEmptyWhenMissing() {
        ClusterType ct = namedCT("small");
        DeploymentTarget dt = buildDT("dt-east", "prod", Map.of());

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("", ctx.getWorkspace());
        assertEquals("", ctx.getWorkload());
    }

    // ---- buildTemplateContext — namespace derivation -------------------------

    @Test
    void testBuildTemplateContextDerivesNamespaceFromEnvironmentClusterTypeDt() {
        ClusterType ct = namedCT("large");
        DeploymentTarget dt = buildDT("dt-west", "staging", Map.of());

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        // namespace = {environment}-{clusterTypeName}-{dtName}
        assertEquals("staging-large-dt-west", ctx.getNamespace());
    }

    // ---- buildTemplateContext — cluster type and deployment target name -------

    @Test
    void testBuildTemplateContextSetsClusterTypeName() {
        ClusterType ct = namedCT("azure-arc");
        DeploymentTarget dt = buildDT("dt-1", "env", Map.of());

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("azure-arc", ctx.getClusterType());
    }

    @Test
    void testBuildTemplateContextSetsDeploymentTargetName() {
        ClusterType ct = namedCT("ct");
        DeploymentTarget dt = buildDT("my-dt", "env", Map.of());

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("my-dt", ctx.getDeploymentTargetName());
    }

    // ---- buildTemplateContext — configData passthrough -----------------------

    @Test
    void testBuildTemplateContextPassesConfigDataThrough() {
        ClusterType ct = namedCT("ct");
        DeploymentTarget dt = buildDT("dt", "env", Map.of());
        Map<String, Object> config = Map.of("region", "eastus", "tier", "prod");

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, config);
        assertEquals("eastus", ctx.getConfigData().get("region"));
        assertEquals("prod", ctx.getConfigData().get("tier"));
    }

    // ---- buildTemplateContext — manifests repo reference ---------------------

    @Test
    void testBuildTemplateContextReadsManifestsFromDtSpec() {
        ClusterType ct = namedCT("ct");
        RepositoryReference ref = new RepositoryReference();
        ref.setRepo("https://github.com/org/wl");
        ref.setBranch("main");
        ref.setPath("./apps");
        DeploymentTarget dt = buildDT("dt", "env", Map.of());
        dt.getSpec().setManifests(ref);

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertEquals("https://github.com/org/wl", ctx.getManifests().get("repo"));
        assertEquals("main", ctx.getManifests().get("branch"));
        assertEquals("./apps", ctx.getManifests().get("path"));
    }

    @Test
    void testBuildTemplateContextHandlesNullManifests() {
        ClusterType ct = namedCT("ct");
        DeploymentTarget dt = buildDT("dt", "env", Map.of());
        dt.getSpec().setManifests(null);

        TemplateContext ctx = reconciler.buildTemplateContext(ct, dt, Map.of());
        assertTrue(ctx.getManifests().isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------

    private static ClusterType namedCT(String name) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);

        ClusterType ct = new ClusterType();
        ct.setMetadata(meta);
        ct.setSpec(new ClusterTypeSpec());
        return ct;
    }

    private static DeploymentTarget buildDT(String name, String environment,
                                             Map<String, String> metadataLabels) {
        ObjectMeta meta = new ObjectMeta();
        meta.setName(name);
        meta.setNamespace("ns");
        meta.setLabels(metadataLabels);

        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName(name);
        spec.setEnvironment(environment);

        DeploymentTarget dt = new DeploymentTarget();
        dt.setMetadata(meta);
        dt.setSpec(spec);
        return dt;
    }
}
