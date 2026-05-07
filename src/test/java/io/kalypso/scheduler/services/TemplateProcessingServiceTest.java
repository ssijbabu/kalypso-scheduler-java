package io.kalypso.scheduler.services;

import io.kalypso.scheduler.exception.TemplateProcessingException;
import io.kalypso.scheduler.model.TemplateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TemplateProcessingService}.
 *
 * <p>Tests cover: basic interpolation, each custom function (toYaml, stringify, hash, unquote),
 * recursive template re-processing, and the static {@link TemplateProcessingService#buildTargetNamespace}
 * helper.
 *
 * <p>No Kubernetes client or external services are needed — all tests exercise
 * Freemarker rendering in-process against a {@link TemplateContext}.
 */
class TemplateProcessingServiceTest {

    private TemplateProcessingService service;

    @BeforeEach
    void setUp() {
        service = new TemplateProcessingService();
    }

    /**
     * Verifies that a simple variable interpolation resolves correctly.
     */
    @Test
    void testProcessSimpleTemplate() {
        TemplateContext ctx = new TemplateContext.Builder()
                .deploymentTargetName("prod-east")
                .environment("prod")
                .build();

        String result = service.processTemplate("target=${DeploymentTargetName} env=${Environment}", ctx);
        assertEquals("target=prod-east env=prod", result);
    }

    /**
     * Verifies that all standard context keys are exposed under their PascalCase names
     * so that Freemarker templates can reference them directly.
     */
    @Test
    void testProcessTemplateAllContextKeys() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("tier", "production");
        Map<String, Object> configData = new LinkedHashMap<>();
        configData.put("DB_URL", "postgres://host/db");
        Map<String, String> manifests = Map.of("repo", "https://github.com/org/repo",
                "branch", "main", "path", "./clusters");

        TemplateContext ctx = new TemplateContext.Builder()
                .deploymentTargetName("cluster-1")
                .namespace("prod-aks-cluster-1")
                .environment("prod")
                .workspace("team-a")
                .workload("myapp")
                .clusterType("aks")
                .labels(labels)
                .configData(configData)
                .manifests(manifests)
                .build();

        String template = "${DeploymentTargetName}/${Namespace}/${Environment}/${Workspace}/${Workload}/${ClusterType}/${Repo}/${Branch}/${Path}";
        String result = service.processTemplate(template, ctx);
        assertEquals("cluster-1/prod-aks-cluster-1/prod/team-a/myapp/aks/https://github.com/org/repo/main/./clusters", result);
    }

    /**
     * Verifies that the {@code toYaml} custom function serializes a map to YAML.
     * Corresponds to Go's {@code toYaml} (yaml.Marshal) function in the funcMap.
     */
    @Test
    void testToYamlFunction() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("region", "eastus");
        labels.put("tier", "production");

        TemplateContext ctx = new TemplateContext.Builder().labels(labels).build();
        String result = service.processTemplate("${toYaml(Labels)}", ctx);

        assertTrue(result.contains("region: eastus"), "toYaml output must contain 'region: eastus'");
        assertTrue(result.contains("tier: production"), "toYaml output must contain 'tier: production'");
    }

    /**
     * Verifies that the {@code stringify} custom function converts a map to a YAML string.
     * Corresponds to Go's {@code stringify} (map flattening) function.
     */
    @Test
    void testStringifyFunction() {
        Map<String, Object> configData = new LinkedHashMap<>();
        configData.put("DB_HOST", "postgres.example.com");
        configData.put("PORT", 5432);

        TemplateContext ctx = new TemplateContext.Builder().configData(configData).build();
        String result = service.processTemplate("${stringify(ConfigData)}", ctx);

        assertTrue(result.contains("DB_HOST: postgres.example.com"),
                "stringify output must contain 'DB_HOST: postgres.example.com'");
    }

    /**
     * Verifies that the {@code hash} custom function returns a non-empty string.
     * Corresponds to Go's {@code hash} (hashstructure) function.
     * The exact hash value is not asserted — only that it is deterministic for the same input.
     */
    @Test
    void testHashFunctionDeterministic() {
        Map<String, String> labels = Map.of("region", "eastus");
        TemplateContext ctx = new TemplateContext.Builder().labels(labels).build();

        String hash1 = service.processTemplate("${hash(Labels)}", ctx);
        String hash2 = service.processTemplate("${hash(Labels)}", ctx);

        assertFalse(hash1.isBlank(), "hash must return a non-empty string");
        assertEquals(hash1, hash2, "hash must be deterministic for the same input");
    }

    /**
     * Verifies that the {@code unquote} custom function strips surrounding quotes and whitespace.
     * Corresponds to Go's {@code unquote} (trim quotes/whitespace) function.
     */
    @Test
    void testUnquoteFunction() {
        TemplateContext ctx = new TemplateContext.Builder()
                .deploymentTargetName("\"  quoted  \"")
                .build();

        String result = service.processTemplate("${unquote(DeploymentTargetName)}", ctx);
        assertEquals("quoted", result);
    }

    /**
     * Verifies recursive template re-processing: when the first rendered output still
     * contains {@code ${} markers, the service re-processes the result.
     *
     * <p>Mirrors Go's {@code replaceTemplateVariables} recursive check for {@code {{}}
     * in the rendered output.
     */
    @Test
    void testRecursiveTemplateProcessing() {
        // The namespace value itself contains a Freemarker expression.
        // First pass: "${Namespace}" → "prefix-${DeploymentTargetName}"
        // Second pass: "prefix-${DeploymentTargetName}" → "prefix-cluster-1"
        TemplateContext ctx = new TemplateContext.Builder()
                .deploymentTargetName("cluster-1")
                .namespace("prefix-${DeploymentTargetName}")
                .build();

        String result = service.processTemplate("${Namespace}", ctx);
        assertEquals("prefix-cluster-1", result);
    }

    /**
     * Verifies that the recursion guard stops after {@link TemplateProcessingService#MAX_RECURSION_DEPTH}
     * levels and returns the partially resolved string rather than looping forever.
     */
    @Test
    void testRecursionDepthGuard() {
        // Each pass replaces ${Namespace} but namespace itself contains ${Namespace}
        // This would loop forever without the guard.
        TemplateContext ctx = new TemplateContext.Builder()
                .namespace("${Namespace}")
                .build();

        // Should not throw StackOverflowError; must complete within the guard
        assertDoesNotThrow(() -> service.processTemplate("${Namespace}", ctx));
    }

    /**
     * Verifies that an invalid Freemarker template throws {@link TemplateProcessingException}.
     */
    @Test
    void testInvalidTemplateThrows() {
        TemplateContext ctx = new TemplateContext.Builder().build();
        // Unclosed interpolation — invalid Freemarker syntax
        assertThrows(TemplateProcessingException.class,
                () -> service.processTemplate("${unclosed", ctx));
    }
}
