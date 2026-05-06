package io.kalypso.scheduler.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kalypso.scheduler.api.v1alpha1.Assignment;
import io.kalypso.scheduler.api.v1alpha1.AssignmentList;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackageList;
import io.kalypso.scheduler.api.v1alpha1.BaseRepo;
import io.kalypso.scheduler.api.v1alpha1.BaseRepoList;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.ClusterTypeList;
import io.kalypso.scheduler.api.v1alpha1.ConfigSchema;
import io.kalypso.scheduler.api.v1alpha1.ConfigSchemaList;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTargetList;
import io.kalypso.scheduler.api.v1alpha1.Environment;
import io.kalypso.scheduler.api.v1alpha1.EnvironmentList;
import io.kalypso.scheduler.api.v1alpha1.GitOpsRepo;
import io.kalypso.scheduler.api.v1alpha1.GitOpsRepoList;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicy;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicyList;
import io.kalypso.scheduler.api.v1alpha1.Template;
import io.kalypso.scheduler.api.v1alpha1.TemplateList;
import io.kalypso.scheduler.api.v1alpha1.Workload;
import io.kalypso.scheduler.api.v1alpha1.WorkloadList;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistration;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistrationList;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.BaseRepoSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterConfigType;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ConfigSchemaSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.EnvironmentSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.GitOpsRepoSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.RepositoryReference;
import io.kalypso.scheduler.api.v1alpha1.spec.SchedulingPolicySpec;
import io.kalypso.scheduler.api.v1alpha1.spec.Selector;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateManifest;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateType;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadRegistrationSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadTarget;
import io.kalypso.scheduler.services.FluxService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that run against the operator deployed to the local Docker Desktop cluster.
 *
 * <p>These tests are executed during the {@code integration-test} Maven phase ({@code mvn verify}).
 * The {@code pre-integration-test} phase (configured in {@code pom.xml}) is responsible for:
 * <ol>
 *   <li>Building the Docker image ({@code kalypso-scheduler:latest})</li>
 *   <li>Applying the generated CRD schemas to the cluster</li>
 *   <li>Deploying the operator into the {@code kalypso-java} namespace</li>
 *   <li>Waiting for the Deployment to reach the Ready state</li>
 * </ol>
 *
 * <p>Integration tests are mandatory and must never be skipped with {@code -DskipITs}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperatorIntegrationIT {

    private static final String NAMESPACE = "kalypso-java";
    private static final String DEPLOYMENT_NAME = "kalypso-scheduler";

    /** Shared client — opened once per test class, closed in {@link #tearDownAll()}. */
    private KubernetesClient client;

    /**
     * Waits for the operator Deployment to have at least one ready replica before any test runs.
     * The {@code kubectl rollout status} in the Maven pre-integration-test phase already blocks,
     * but this guard ensures the JVM-level test runner also waits correctly.
     */
    @BeforeAll
    void setUpAll() {
        client = new KubernetesClientBuilder().build();
        client.apps().deployments()
                .inNamespace(NAMESPACE)
                .withName(DEPLOYMENT_NAME)
                .waitUntilReady(2, TimeUnit.MINUTES);
    }

    @AfterAll
    void tearDownAll() {
        deleteQuietly("test-template-crud");
        deleteQuietly("test-template-roundtrip");
        deleteQuietly("test-clustertype-crud");
        deleteQuietly("test-configschema-crud");
        deleteQuietly("test-baserepo-crud");
        deleteQuietly("test-baserepo-nocommit");
        deleteQuietly("test-environment-crud");
        deleteQuietly("test-workloadreg-crud");
        deleteQuietly("test-workload-crud");
        deleteQuietly("test-deploymenttarget-crud");
        deleteQuietly("test-schedulingpolicy-crud");
        deleteQuietly("test-assignment-crud");
        deleteQuietly("test-assignmentpackage-crud");
        deleteQuietly("test-gitopsrepo-crud");
        deleteFluxQuietly("test-flux-service");
        if (client != null) {
            client.close();
        }
    }

    // -------------------------------------------------------------------------
    // Operator health
    // -------------------------------------------------------------------------

    /**
     * Verifies the operator Deployment is Running with the expected replica count.
     * This is the most basic smoke test — if it fails the operator never started.
     */
    @Test
    void testOperatorDeploymentIsRunning() {
        var deployment = client.apps().deployments()
                .inNamespace(NAMESPACE)
                .withName(DEPLOYMENT_NAME)
                .get();

        assertNotNull(deployment, "Deployment must exist in namespace " + NAMESPACE);

        // readyReplicas is omitted from the Kubernetes API response when it is 0 (null == 0)
        int readyReplicas = deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas() : 0;
        assertTrue(readyReplicas >= 1,
                "Expected at least 1 ready replica, got: " + readyReplicas);
    }

    /**
     * Verifies the operator Pod is not crash-looping (no recent restarts).
     * A restart count above 0 suggests the process panicked on startup.
     */
    @Test
    void testOperatorPodHasNoRestarts() {
        var pods = client.pods()
                .inNamespace(NAMESPACE)
                .withLabel("app", DEPLOYMENT_NAME)
                .list()
                .getItems();

        assertFalse(pods.isEmpty(), "No pods found with label app=" + DEPLOYMENT_NAME);

        var pod = pods.get(0);
        var containerStatus = pod.getStatus().getContainerStatuses();
        assertFalse(containerStatus.isEmpty(), "Container status must be populated");

        int restarts = containerStatus.get(0).getRestartCount();
        assertEquals(0, restarts,
                "Operator pod restarted " + restarts + " time(s) — check logs for crash cause");
    }

    /**
     * Verifies the operator logs a startup confirmation line and no failure entries.
     *
     * <p>Without a readiness probe the Pod is marked "ready" as soon as the container
     * process starts — before the JVM has had time to write anything. We poll for the
     * expected line with a 30-second timeout rather than doing a one-shot fetch.
     */
    @Test
    void testOperatorLogsShowStartupAndNoErrors() {
        var pods = client.pods()
                .inNamespace(NAMESPACE)
                .withLabel("app", DEPLOYMENT_NAME)
                .list()
                .getItems();

        assertFalse(pods.isEmpty(), "No pods found for log check");
        String podName = pods.get(0).getMetadata().getName();

        String logs = pollForLog(podName, "Kalypso Scheduler Operator started successfully", 30);

        assertNotNull(logs, "Pod logs must not be null");
        assertTrue(logs.contains("Kalypso Scheduler Operator started successfully"),
                "Logs must contain startup confirmation within 30 s.\nActual logs:\n" + logs);
        assertFalse(logs.contains("Failed to start Kalypso Scheduler Operator"),
                "Logs must not contain startup failure.\nActual logs:\n" + logs);
    }

    /**
     * Polls pod logs once per second until {@code expectedLine} appears or {@code timeoutSeconds} elapses.
     *
     * @param podName        the name of the pod to poll
     * @param expectedLine   the log line to wait for
     * @param timeoutSeconds maximum number of seconds to wait
     * @return the last log snapshot retrieved (may or may not contain the expected line)
     */
    private String pollForLog(String podName, String expectedLine, int timeoutSeconds) {
        String logs = "";
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            logs = client.pods()
                    .inNamespace(NAMESPACE)
                    .withName(podName)
                    .getLog();
            if (logs != null && logs.contains(expectedLine)) {
                return logs;
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return logs;
    }

    // -------------------------------------------------------------------------
    // Template CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a Template resource via the Kubernetes API and reads it back.
     * Verifies that the Java model serializes to valid JSON that the API server accepts,
     * and that deserialization restores all field values.
     */
    @Test
    void testTemplateResourceCrudRoundTrip() {
        Template template = buildTemplate("test-template-crud", TemplateType.RECONCILER,
                List.of(
                        buildManifest("reconciler.yaml",
                                "apiVersion: kustomize.toolkit.fluxcd.io/v1beta2\nkind: Kustomization",
                                ContentType.YAML)
                ));

        Template created = client.resources(Template.class, TemplateList.class)
                .inNamespace(NAMESPACE)
                .resource(template)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-template-crud", created.getMetadata().getName());
        assertEquals(TemplateType.RECONCILER, created.getSpec().getType());
        assertEquals(1, created.getSpec().getManifests().size());
        assertEquals("reconciler.yaml", created.getSpec().getManifests().get(0).getName());
        assertEquals(ContentType.YAML, created.getSpec().getManifests().get(0).getContentType());

        // Read back from API server (not from the create response cache)
        Template fetched = client.resources(Template.class, TemplateList.class)
                .inNamespace(NAMESPACE)
                .withName("test-template-crud")
                .get();

        assertNotNull(fetched, "Template must be retrievable after creation");
        assertEquals(TemplateType.RECONCILER, fetched.getSpec().getType());
        assertNotNull(fetched.getSpec().getManifests().get(0).getTemplate(),
                "Template source string must survive the round-trip");
    }

    /**
     * Creates a Template with multiple manifests of different content types (yaml + sh)
     * and verifies both are persisted correctly.
     */
    @Test
    void testTemplateWithMultipleManifestsRoundTrip() {
        Template template = buildTemplate("test-template-roundtrip", TemplateType.NAMESPACE,
                List.of(
                        buildManifest("namespace.yaml", "apiVersion: v1\nkind: Namespace", ContentType.YAML),
                        buildManifest("setup.sh", "#!/bin/bash\necho 'namespace ready'", ContentType.SH)
                ));

        client.resources(Template.class, TemplateList.class)
                .inNamespace(NAMESPACE)
                .resource(template)
                .serverSideApply();

        Template fetched = client.resources(Template.class, TemplateList.class)
                .inNamespace(NAMESPACE)
                .withName("test-template-roundtrip")
                .get();

        assertNotNull(fetched);
        assertEquals(TemplateType.NAMESPACE, fetched.getSpec().getType());
        assertEquals(2, fetched.getSpec().getManifests().size());

        var manifests = fetched.getSpec().getManifests();
        assertEquals("namespace.yaml", manifests.get(0).getName());
        assertEquals(ContentType.YAML, manifests.get(0).getContentType());
        assertEquals("setup.sh", manifests.get(1).getName());
        assertEquals(ContentType.SH, manifests.get(1).getContentType());
    }

    // -------------------------------------------------------------------------
    // ClusterType CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a ClusterType resource via the Kubernetes API and reads it back.
     * Verifies that the ClusterTypeSpec fields (reconciler, namespaceService, configType)
     * survive serialization through the API server unchanged.
     */
    @Test
    void testClusterTypeResourceCrudRoundTrip() {
        ClusterType clusterType = buildClusterType("test-clustertype-crud",
                "my-reconciler-template",
                "my-namespace-template",
                ClusterConfigType.CONFIGMAP);

        ClusterType created = client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NAMESPACE)
                .resource(clusterType)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-clustertype-crud", created.getMetadata().getName());
        assertEquals("my-reconciler-template", created.getSpec().getReconciler());
        assertEquals("my-namespace-template", created.getSpec().getNamespaceService());
        assertEquals(ClusterConfigType.CONFIGMAP, created.getSpec().getConfigType());

        ClusterType fetched = client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NAMESPACE)
                .withName("test-clustertype-crud")
                .get();

        assertNotNull(fetched);
        assertEquals("my-reconciler-template", fetched.getSpec().getReconciler());
        assertEquals("my-namespace-template", fetched.getSpec().getNamespaceService());
        assertEquals(ClusterConfigType.CONFIGMAP, fetched.getSpec().getConfigType());
    }

    /**
     * Verifies that a ClusterType using the {@code envfile} config type serializes
     * the enum value as the lowercase string {@code "envfile"} (not {@code "ENVFILE"}).
     */
    @Test
    void testClusterTypeWithEnvfileConfigTypeRoundTrip() {
        ClusterType clusterType = buildClusterType("test-clustertype-crud",
                "edge-reconciler", null, ClusterConfigType.ENVFILE);

        client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NAMESPACE)
                .resource(clusterType)
                .serverSideApply();

        ClusterType fetched = client.resources(ClusterType.class, ClusterTypeList.class)
                .inNamespace(NAMESPACE)
                .withName("test-clustertype-crud")
                .get();

        assertNotNull(fetched);
        assertEquals(ClusterConfigType.ENVFILE, fetched.getSpec().getConfigType());
        assertNull(fetched.getSpec().getNamespaceService(),
                "namespaceService was not set and should remain null");
    }

    // -------------------------------------------------------------------------
    // ConfigSchema CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a ConfigSchema resource via the Kubernetes API and reads it back.
     * Verifies that the nested JsonNode schema survives serialization through the API server.
     */
    @Test
    void testConfigSchemaResourceCrudRoundTrip() throws Exception {
        String schemaJson = "{\"type\":\"object\",\"properties\":{\"REGION\":{\"type\":\"string\"},\"DB_URL\":{\"type\":\"string\"}}}";
        Object schemaObj = new ObjectMapper().readValue(schemaJson, Object.class);

        ConfigSchema configSchema = buildConfigSchema("test-configschema-crud", "large", schemaObj);

        ConfigSchema created = client.resources(ConfigSchema.class, ConfigSchemaList.class)
                .inNamespace(NAMESPACE)
                .resource(configSchema)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-configschema-crud", created.getMetadata().getName());
        assertEquals("large", created.getSpec().getClusterType());
        assertNotNull(created.getSpec().getSchema(), "schema node must be present");

        ConfigSchema fetched = client.resources(ConfigSchema.class, ConfigSchemaList.class)
                .inNamespace(NAMESPACE)
                .withName("test-configschema-crud")
                .get();

        assertNotNull(fetched, "ConfigSchema must be retrievable after creation");
        assertEquals("large", fetched.getSpec().getClusterType());
        assertNotNull(fetched.getSpec().getSchema());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> schemaMap = (java.util.Map<String, Object>) fetched.getSpec().getSchema();
        assertEquals("object", schemaMap.get("type"),
                "schema type field must survive the round-trip");
    }

    /**
     * Verifies that a ConfigSchema with only the clusterType field (no schema body)
     * is accepted by the API server and null schema is preserved correctly.
     */
    @Test
    void testConfigSchemaWithNoSchemaBodyRoundTrip() {
        ConfigSchema configSchema = buildConfigSchema("test-configschema-crud", "edge", null);

        client.resources(ConfigSchema.class, ConfigSchemaList.class)
                .inNamespace(NAMESPACE)
                .resource(configSchema)
                .serverSideApply();

        ConfigSchema fetched = client.resources(ConfigSchema.class, ConfigSchemaList.class)
                .inNamespace(NAMESPACE)
                .withName("test-configschema-crud")
                .get();

        assertNotNull(fetched);
        assertEquals("edge", fetched.getSpec().getClusterType());
        assertNull(fetched.getSpec().getSchema(), "absent schema must remain null");
    }

    // -------------------------------------------------------------------------
    // BaseRepo CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a BaseRepo resource with all fields (including optional commit) and reads it back.
     * Verifies that all Git coordinates survive serialization through the API server.
     */
    @Test
    void testBaseRepoResourceCrudRoundTrip() {
        BaseRepo baseRepo = buildBaseRepo("test-baserepo-crud",
                "https://github.com/org/control-plane", "main", "./environments", "a1b2c3d4");

        BaseRepo created = client.resources(BaseRepo.class, BaseRepoList.class)
                .inNamespace(NAMESPACE)
                .resource(baseRepo)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-baserepo-crud", created.getMetadata().getName());
        assertEquals("https://github.com/org/control-plane", created.getSpec().getRepo());
        assertEquals("main",            created.getSpec().getBranch());
        assertEquals("./environments",  created.getSpec().getPath());
        assertEquals("a1b2c3d4",        created.getSpec().getCommit());

        BaseRepo fetched = client.resources(BaseRepo.class, BaseRepoList.class)
                .inNamespace(NAMESPACE)
                .withName("test-baserepo-crud")
                .get();

        assertNotNull(fetched, "BaseRepo must be retrievable after creation");
        assertEquals("https://github.com/org/control-plane", fetched.getSpec().getRepo());
        assertEquals("main",           fetched.getSpec().getBranch());
        assertEquals("./environments", fetched.getSpec().getPath());
        assertEquals("a1b2c3d4",       fetched.getSpec().getCommit());
    }

    /**
     * Verifies that a BaseRepo without the optional {@code commit} field is accepted by
     * the API server and the field remains absent (null) on read-back.
     */
    @Test
    void testBaseRepoWithoutCommitRoundTrip() {
        BaseRepo baseRepo = buildBaseRepo("test-baserepo-nocommit",
                "https://github.com/org/base", "release/v2", "./base", null);

        client.resources(BaseRepo.class, BaseRepoList.class)
                .inNamespace(NAMESPACE)
                .resource(baseRepo)
                .serverSideApply();

        BaseRepo fetched = client.resources(BaseRepo.class, BaseRepoList.class)
                .inNamespace(NAMESPACE)
                .withName("test-baserepo-nocommit")
                .get();

        assertNotNull(fetched);
        assertEquals("https://github.com/org/base", fetched.getSpec().getRepo());
        assertEquals("release/v2", fetched.getSpec().getBranch());
        assertEquals("./base",     fetched.getSpec().getPath());
        assertNull(fetched.getSpec().getCommit(), "commit was not set and must remain null");
    }

    // -------------------------------------------------------------------------
    // Environment CRD
    // -------------------------------------------------------------------------

    /**
     * Creates an Environment resource via the Kubernetes API and reads it back.
     * Verifies that the nested {@code RepositoryReference} in {@code controlPlane}
     * survives serialization through the API server unchanged.
     */
    @Test
    void testEnvironmentResourceCrudRoundTrip() {
        Environment environment = buildEnvironment("test-environment-crud",
                "https://github.com/org/control-plane", "main", "./environments/prod");

        Environment created = client.resources(Environment.class, EnvironmentList.class)
                .inNamespace(NAMESPACE)
                .resource(environment)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-environment-crud", created.getMetadata().getName());
        assertNotNull(created.getSpec().getControlPlane());
        assertEquals("https://github.com/org/control-plane", created.getSpec().getControlPlane().getRepo());
        assertEquals("main", created.getSpec().getControlPlane().getBranch());
        assertEquals("./environments/prod", created.getSpec().getControlPlane().getPath());

        Environment fetched = client.resources(Environment.class, EnvironmentList.class)
                .inNamespace(NAMESPACE)
                .withName("test-environment-crud")
                .get();

        assertNotNull(fetched, "Environment must be retrievable after creation");
        assertNotNull(fetched.getSpec().getControlPlane());
        assertEquals("https://github.com/org/control-plane", fetched.getSpec().getControlPlane().getRepo());
        assertEquals("./environments/prod", fetched.getSpec().getControlPlane().getPath());
    }

    // -------------------------------------------------------------------------
    // WorkloadRegistration CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a WorkloadRegistration resource via the Kubernetes API and reads it back.
     * Verifies that the {@code workload} repository reference and {@code workspace}
     * fields survive serialization through the API server unchanged.
     */
    @Test
    void testWorkloadRegistrationResourceCrudRoundTrip() {
        WorkloadRegistration wreg = buildWorkloadRegistration("test-workloadreg-crud",
                "https://github.com/org/workloads", "main", "./apps/myapp", "team-alpha");

        WorkloadRegistration created = client.resources(WorkloadRegistration.class, WorkloadRegistrationList.class)
                .inNamespace(NAMESPACE)
                .resource(wreg)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-workloadreg-crud", created.getMetadata().getName());
        assertNotNull(created.getSpec().getWorkload());
        assertEquals("https://github.com/org/workloads", created.getSpec().getWorkload().getRepo());
        assertEquals("team-alpha", created.getSpec().getWorkspace());

        WorkloadRegistration fetched = client.resources(WorkloadRegistration.class, WorkloadRegistrationList.class)
                .inNamespace(NAMESPACE)
                .withName("test-workloadreg-crud")
                .get();

        assertNotNull(fetched, "WorkloadRegistration must be retrievable after creation");
        assertEquals("./apps/myapp", fetched.getSpec().getWorkload().getPath());
        assertEquals("team-alpha", fetched.getSpec().getWorkspace());
    }

    // -------------------------------------------------------------------------
    // Workload CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a Workload resource with a deployment target list via the Kubernetes API
     * and reads it back. Verifies the list and nested repository reference survive
     * serialization through the API server.
     */
    @Test
    void testWorkloadResourceCrudRoundTrip() {
        Workload workload = buildWorkload("test-workload-crud",
                "prod-east", "https://github.com/org/wl", "main", "./targets/east");

        Workload created = client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NAMESPACE)
                .resource(workload)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-workload-crud", created.getMetadata().getName());
        assertNotNull(created.getSpec().getDeploymentTargets());
        assertEquals(1, created.getSpec().getDeploymentTargets().size());
        assertEquals("prod-east", created.getSpec().getDeploymentTargets().get(0).getName());

        Workload fetched = client.resources(Workload.class, WorkloadList.class)
                .inNamespace(NAMESPACE)
                .withName("test-workload-crud")
                .get();

        assertNotNull(fetched, "Workload must be retrievable after creation");
        assertEquals(1, fetched.getSpec().getDeploymentTargets().size());
        assertEquals("prod-east", fetched.getSpec().getDeploymentTargets().get(0).getName());
        assertEquals("./targets/east",
                fetched.getSpec().getDeploymentTargets().get(0).getManifests().getPath());
    }

    // -------------------------------------------------------------------------
    // DeploymentTarget CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a DeploymentTarget resource with labels and manifests via the Kubernetes API
     * and reads it back. Verifies all spec fields survive serialization.
     */
    @Test
    void testDeploymentTargetResourceCrudRoundTrip() {
        DeploymentTarget dt = buildDeploymentTarget("test-deploymenttarget-crud",
                "prod-east-cluster", "prod",
                Map.of("region", "east-us", "tier", "production"),
                "https://github.com/org/infra", "main", "./clusters/prod-east");

        DeploymentTarget created = client.resources(DeploymentTarget.class, DeploymentTargetList.class)
                .inNamespace(NAMESPACE)
                .resource(dt)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-deploymenttarget-crud", created.getMetadata().getName());
        assertEquals("prod-east-cluster", created.getSpec().getName());
        assertEquals("prod", created.getSpec().getEnvironment());
        assertNotNull(created.getSpec().getLabels());

        DeploymentTarget fetched = client.resources(DeploymentTarget.class, DeploymentTargetList.class)
                .inNamespace(NAMESPACE)
                .withName("test-deploymenttarget-crud")
                .get();

        assertNotNull(fetched, "DeploymentTarget must be retrievable after creation");
        assertEquals("prod-east-cluster", fetched.getSpec().getName());
        assertEquals("east-us", fetched.getSpec().getLabels().get("region"));
        assertEquals("./clusters/prod-east", fetched.getSpec().getManifests().getPath());
    }

    // -------------------------------------------------------------------------
    // SchedulingPolicy CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a SchedulingPolicy resource with both selectors via the Kubernetes API
     * and reads it back. Verifies nested selectors and label maps survive serialization.
     */
    @Test
    void testSchedulingPolicyResourceCrudRoundTrip() {
        SchedulingPolicy sp = buildSchedulingPolicy("test-schedulingpolicy-crud",
                "team-alpha", Map.of("tier", "production"),
                null, Map.of("size", "large"));

        SchedulingPolicy created = client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                .inNamespace(NAMESPACE)
                .resource(sp)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-schedulingpolicy-crud", created.getMetadata().getName());
        assertNotNull(created.getSpec().getDeploymentTargetSelector());
        assertEquals("team-alpha", created.getSpec().getDeploymentTargetSelector().getWorkspace());

        SchedulingPolicy fetched = client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                .inNamespace(NAMESPACE)
                .withName("test-schedulingpolicy-crud")
                .get();

        assertNotNull(fetched, "SchedulingPolicy must be retrievable after creation");
        assertEquals("team-alpha", fetched.getSpec().getDeploymentTargetSelector().getWorkspace());
        assertEquals("production",
                fetched.getSpec().getDeploymentTargetSelector().getLabelSelector().get("tier"));
        assertNotNull(fetched.getSpec().getClusterTypeSelector());
        assertEquals("large", fetched.getSpec().getClusterTypeSelector().getLabelSelector().get("size"));
    }

    // -------------------------------------------------------------------------
    // Assignment CRD
    // -------------------------------------------------------------------------

    /**
     * Creates an Assignment resource via the Kubernetes API and reads it back.
     * Verifies that the {@code clusterType} and {@code deploymentTarget} fields
     * survive serialization through the API server.
     */
    @Test
    void testAssignmentResourceCrudRoundTrip() {
        Assignment assignment = buildAssignment("test-assignment-crud",
                "large-aks", "prod-east-cluster");

        Assignment created = client.resources(Assignment.class, AssignmentList.class)
                .inNamespace(NAMESPACE)
                .resource(assignment)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-assignment-crud", created.getMetadata().getName());
        assertEquals("large-aks", created.getSpec().getClusterType());
        assertEquals("prod-east-cluster", created.getSpec().getDeploymentTarget());

        Assignment fetched = client.resources(Assignment.class, AssignmentList.class)
                .inNamespace(NAMESPACE)
                .withName("test-assignment-crud")
                .get();

        assertNotNull(fetched, "Assignment must be retrievable after creation");
        assertEquals("large-aks", fetched.getSpec().getClusterType());
        assertEquals("prod-east-cluster", fetched.getSpec().getDeploymentTarget());
    }

    // -------------------------------------------------------------------------
    // AssignmentPackage CRD
    // -------------------------------------------------------------------------

    /**
     * Creates an AssignmentPackage resource via the Kubernetes API and reads it back.
     * Verifies that the manifest lists and content types survive serialization.
     */
    @Test
    void testAssignmentPackageResourceCrudRoundTrip() {
        AssignmentPackage pkg = buildAssignmentPackage("test-assignmentpackage-crud",
                List.of("apiVersion: v1\nkind: Kustomization"), ContentType.YAML,
                List.of("apiVersion: v1\nkind: Namespace"),     ContentType.YAML,
                List.of(),                                       ContentType.YAML);

        AssignmentPackage created = client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                .inNamespace(NAMESPACE)
                .resource(pkg)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-assignmentpackage-crud", created.getMetadata().getName());
        assertEquals(1, created.getSpec().getReconcilerManifests().size());
        assertEquals(ContentType.YAML, created.getSpec().getReconcilerManifestsContentType());

        AssignmentPackage fetched = client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                .inNamespace(NAMESPACE)
                .withName("test-assignmentpackage-crud")
                .get();

        assertNotNull(fetched, "AssignmentPackage must be retrievable after creation");
        assertEquals(1, fetched.getSpec().getReconcilerManifests().size());
        assertEquals(ContentType.YAML, fetched.getSpec().getNamespaceManifestsContentType());
    }

    // -------------------------------------------------------------------------
    // GitOpsRepo CRD
    // -------------------------------------------------------------------------

    /**
     * Creates a GitOpsRepo resource via the Kubernetes API and reads it back.
     * Verifies that the repo URL, branch, and path fields survive serialization.
     */
    @Test
    void testGitOpsRepoResourceCrudRoundTrip() {
        GitOpsRepo gitOpsRepo = buildGitOpsRepo("test-gitopsrepo-crud",
                "https://github.com/org/gitops-manifests", "main", "./clusters");

        GitOpsRepo created = client.resources(GitOpsRepo.class, GitOpsRepoList.class)
                .inNamespace(NAMESPACE)
                .resource(gitOpsRepo)
                .serverSideApply();

        assertNotNull(created.getMetadata().getUid(), "API server must assign a UID");
        assertEquals("test-gitopsrepo-crud", created.getMetadata().getName());
        assertEquals("https://github.com/org/gitops-manifests", created.getSpec().getRepo());
        assertEquals("main", created.getSpec().getBranch());
        assertEquals("./clusters", created.getSpec().getPath());

        GitOpsRepo fetched = client.resources(GitOpsRepo.class, GitOpsRepoList.class)
                .inNamespace(NAMESPACE)
                .withName("test-gitopsrepo-crud")
                .get();

        assertNotNull(fetched, "GitOpsRepo must be retrievable after creation");
        assertEquals("https://github.com/org/gitops-manifests", fetched.getSpec().getRepo());
        assertEquals("main", fetched.getSpec().getBranch());
        assertEquals("./clusters", fetched.getSpec().getPath());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Template buildTemplate(String name, TemplateType type, List<TemplateManifest> manifests) {
        Template template = new Template();
        template.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        TemplateSpec spec = new TemplateSpec();
        spec.setType(type);
        spec.setManifests(manifests);
        template.setSpec(spec);

        return template;
    }

    private TemplateManifest buildManifest(String name, String templateSource, ContentType contentType) {
        TemplateManifest m = new TemplateManifest();
        m.setName(name);
        m.setTemplate(templateSource);
        m.setContentType(contentType);
        return m;
    }

    private ClusterType buildClusterType(String name, String reconciler,
                                         String namespaceService, ClusterConfigType configType) {
        ClusterType ct = new ClusterType();
        ct.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        ClusterTypeSpec spec = new ClusterTypeSpec();
        spec.setReconciler(reconciler);
        spec.setNamespaceService(namespaceService);
        spec.setConfigType(configType);
        ct.setSpec(spec);

        return ct;
    }

    private ConfigSchema buildConfigSchema(String name, String clusterType, Object schema) {
        ConfigSchema cs = new ConfigSchema();
        cs.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        ConfigSchemaSpec spec = new ConfigSchemaSpec();
        spec.setClusterType(clusterType);
        spec.setSchema(schema);
        cs.setSpec(spec);

        return cs;
    }

    private BaseRepo buildBaseRepo(String name, String repo, String branch, String path, String commit) {
        BaseRepo br = new BaseRepo();
        br.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        BaseRepoSpec spec = new BaseRepoSpec();
        spec.setRepo(repo);
        spec.setBranch(branch);
        spec.setPath(path);
        spec.setCommit(commit);
        br.setSpec(spec);

        return br;
    }

    private Environment buildEnvironment(String name, String repo, String branch, String path) {
        Environment env = new Environment();
        env.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        EnvironmentSpec spec = new EnvironmentSpec();
        spec.setControlPlane(ref);
        env.setSpec(spec);

        return env;
    }

    private WorkloadRegistration buildWorkloadRegistration(String name, String repo, String branch,
                                                           String path, String workspace) {
        WorkloadRegistration wreg = new WorkloadRegistration();
        wreg.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        WorkloadRegistrationSpec spec = new WorkloadRegistrationSpec();
        spec.setWorkload(ref);
        spec.setWorkspace(workspace);
        wreg.setSpec(spec);

        return wreg;
    }

    private Workload buildWorkload(String name, String targetName,
                                   String repo, String branch, String path) {
        Workload wl = new Workload();
        wl.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        WorkloadTarget target = new WorkloadTarget();
        target.setName(targetName);
        target.setManifests(ref);

        WorkloadSpec spec = new WorkloadSpec();
        spec.setDeploymentTargets(List.of(target));
        wl.setSpec(spec);

        return wl;
    }

    private DeploymentTarget buildDeploymentTarget(String name, String targetName, String environment,
                                                   Map<String, String> labels,
                                                   String repo, String branch, String path) {
        DeploymentTarget dt = new DeploymentTarget();
        dt.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        RepositoryReference ref = new RepositoryReference();
        ref.setRepo(repo);
        ref.setBranch(branch);
        ref.setPath(path);

        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName(targetName);
        spec.setEnvironment(environment);
        spec.setLabels(labels);
        spec.setManifests(ref);
        dt.setSpec(spec);

        return dt;
    }

    private SchedulingPolicy buildSchedulingPolicy(String name,
                                                   String dtWorkspace, Map<String, String> dtLabelSelector,
                                                   String ctWorkspace, Map<String, String> ctLabelSelector) {
        SchedulingPolicy sp = new SchedulingPolicy();
        sp.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        Selector dtSelector = new Selector();
        dtSelector.setWorkspace(dtWorkspace);
        dtSelector.setLabelSelector(dtLabelSelector);

        Selector ctSelector = new Selector();
        ctSelector.setWorkspace(ctWorkspace);
        ctSelector.setLabelSelector(ctLabelSelector);

        SchedulingPolicySpec spec = new SchedulingPolicySpec();
        spec.setDeploymentTargetSelector(dtSelector);
        spec.setClusterTypeSelector(ctSelector);
        sp.setSpec(spec);

        return sp;
    }

    private Assignment buildAssignment(String name, String clusterType, String deploymentTarget) {
        Assignment asgn = new Assignment();
        asgn.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        AssignmentSpec spec = new AssignmentSpec();
        spec.setClusterType(clusterType);
        spec.setDeploymentTarget(deploymentTarget);
        asgn.setSpec(spec);

        return asgn;
    }

    private AssignmentPackage buildAssignmentPackage(String name,
                                                     List<String> reconcilerManifests,
                                                     ContentType reconcilerContentType,
                                                     List<String> namespaceManifests,
                                                     ContentType namespaceContentType,
                                                     List<String> configManifests,
                                                     ContentType configContentType) {
        AssignmentPackage pkg = new AssignmentPackage();
        pkg.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        AssignmentPackageSpec spec = new AssignmentPackageSpec();
        spec.setReconcilerManifests(reconcilerManifests);
        spec.setReconcilerManifestsContentType(reconcilerContentType);
        spec.setNamespaceManifests(namespaceManifests);
        spec.setNamespaceManifestsContentType(namespaceContentType);
        spec.setConfigManifests(configManifests);
        spec.setConfigManifestsContentType(configContentType);
        pkg.setSpec(spec);

        return pkg;
    }

    private GitOpsRepo buildGitOpsRepo(String name, String repo, String branch, String path) {
        GitOpsRepo gor = new GitOpsRepo();
        gor.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(NAMESPACE)
                .build());

        GitOpsRepoSpec spec = new GitOpsRepoSpec();
        spec.setRepo(repo);
        spec.setBranch(branch);
        spec.setPath(path);
        gor.setSpec(spec);

        return gor;
    }

    // -------------------------------------------------------------------------
    // FluxService
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link FluxService} can create and delete a Flux
     * {@code GitRepository} + {@code Kustomization} pair against a live cluster.
     *
     * <p>This test is skipped automatically when the Flux source CRD
     * ({@code gitrepositories.source.toolkit.fluxcd.io}) is not installed,
     * so the test suite does not fail on clusters without Flux.
     */
    @Test
    void testFluxServiceCreateAndDeleteReferenceResources() {
        var crd = client.apiextensions().v1().customResourceDefinitions()
                .withName("gitrepositories.source.toolkit.fluxcd.io")
                .get();
        boolean v1Served = crd != null && crd.getSpec().getVersions().stream()
                .anyMatch(v -> "v1".equals(v.getName()) && Boolean.TRUE.equals(v.getServed()));
        Assumptions.assumeTrue(v1Served,
                "Skipping FluxService IT: Flux GitRepository v1 not served on cluster");

        // Flux resources always live in flux-system; targetNamespace is the CRD's namespace
        String fluxNamespace = FluxService.DEFAULT_FLUX_NAMESPACE;
        FluxService fluxService = new FluxService(client);
        String testName = "test-flux-service";

        fluxService.createFluxReferenceResources(
                testName, fluxNamespace, NAMESPACE,
                "https://github.com/org/test-repo", "main", "./", null);

        var gitRepo = client.genericKubernetesResources(
                        new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                                .withGroup("source.toolkit.fluxcd.io")
                                .withVersion("v1")
                                .withKind("GitRepository")
                                .withNamespaced(true)
                                .build())
                .inNamespace(fluxNamespace)
                .withName(testName)
                .get();
        assertNotNull(gitRepo, "GitRepository must exist in flux-system after createFluxReferenceResources");
        assertEquals(testName, gitRepo.getMetadata().getName());

        var kustomization = client.genericKubernetesResources(
                        new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                                .withGroup("kustomize.toolkit.fluxcd.io")
                                .withVersion("v1")
                                .withKind("Kustomization")
                                .withNamespaced(true)
                                .build())
                .inNamespace(fluxNamespace)
                .withName(testName)
                .get();
        assertNotNull(kustomization, "Kustomization must exist in flux-system after createFluxReferenceResources");
        assertEquals(testName, kustomization.getMetadata().getName());

        fluxService.deleteFluxReferenceResources(testName, fluxNamespace);

        assertNull(client.genericKubernetesResources(
                        new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                                .withGroup("source.toolkit.fluxcd.io")
                                .withVersion("v1")
                                .withKind("GitRepository")
                                .withNamespaced(true)
                                .build())
                .inNamespace(fluxNamespace)
                .withName(testName)
                .get(),
                "GitRepository must be absent after deleteFluxReferenceResources");
    }

    private void deleteFluxQuietly(String name) {
        // Only attempt deletion if Flux v1beta2 is available; otherwise the API call itself throws.
        var crd = client.apiextensions().v1().customResourceDefinitions()
                .withName("gitrepositories.source.toolkit.fluxcd.io").get();
        boolean v1Served = crd != null && crd.getSpec().getVersions().stream()
                .anyMatch(v -> "v1".equals(v.getName()) && Boolean.TRUE.equals(v.getServed()));
        if (!v1Served) {
            return;
        }
        String fluxNamespace = FluxService.DEFAULT_FLUX_NAMESPACE;
        try {
            client.genericKubernetesResources(
                            new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                                    .withGroup("source.toolkit.fluxcd.io")
                                    .withVersion("v1")
                                    .withKind("GitRepository")
                                    .withNamespaced(true)
                                    .build())
                    .inNamespace(fluxNamespace).withName(name).delete();
        } catch (Exception ignored) {}
        try {
            client.genericKubernetesResources(
                            new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                                    .withGroup("kustomize.toolkit.fluxcd.io")
                                    .withVersion("v1")
                                    .withKind("Kustomization")
                                    .withNamespaced(true)
                                    .build())
                    .inNamespace(fluxNamespace).withName(name).delete();
        } catch (Exception ignored) {}
    }

    private void deleteQuietly(String resourceName) {
        try {
            client.resources(Template.class, TemplateList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(ClusterType.class, ClusterTypeList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(ConfigSchema.class, ConfigSchemaList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(BaseRepo.class, BaseRepoList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(Environment.class, EnvironmentList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(WorkloadRegistration.class, WorkloadRegistrationList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(Workload.class, WorkloadList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(DeploymentTarget.class, DeploymentTargetList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(SchedulingPolicy.class, SchedulingPolicyList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(Assignment.class, AssignmentList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(AssignmentPackage.class, AssignmentPackageList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(GitOpsRepo.class, GitOpsRepoList.class)
                    .inNamespace(NAMESPACE).withName(resourceName).delete();
        } catch (Exception ignored) {}
    }
}
