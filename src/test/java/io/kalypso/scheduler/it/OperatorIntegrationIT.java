package io.kalypso.scheduler.it;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.ClusterTypeList;
import io.kalypso.scheduler.api.v1alpha1.Template;
import io.kalypso.scheduler.api.v1alpha1.TemplateList;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterConfigType;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateManifest;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
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
 * <p>Skip integration tests with: {@code mvn verify -DskipITs}
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

        // Read back
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
        // Reuse the crud resource by updating configType to ENVFILE
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

    private void deleteQuietly(String templateName) {
        try {
            client.resources(Template.class, TemplateList.class)
                    .inNamespace(NAMESPACE).withName(templateName).delete();
        } catch (Exception ignored) {}
        try {
            client.resources(ClusterType.class, ClusterTypeList.class)
                    .inNamespace(NAMESPACE).withName(templateName).delete();
        } catch (Exception ignored) {}
    }
}
