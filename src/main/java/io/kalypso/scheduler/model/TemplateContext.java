package io.kalypso.scheduler.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable data bag passed to Freemarker when rendering a {@code Template} manifest.
 *
 * <p>This is the Java equivalent of the Go operator's {@code dataType} struct in
 * {@code scheduler/templater.go}:
 * <pre>
 * type dataType struct {
 *     DeploymentTargetName string
 *     Namespace            string
 *     Environment          string
 *     Workspace            string
 *     Workload             string
 *     Labels               map[string]string
 *     Manifests            map[string]string
 *     ClusterType          string
 *     ConfigData           map[string]interface{}
 * }
 * </pre>
 *
 * <p>The {@link #toMap()} method exposes all fields under their PascalCase names
 * so Freemarker templates can reference them as {@code ${DeploymentTargetName}},
 * {@code ${Labels.region}}, {@code ${ConfigData.DB_URL}}, etc.
 *
 * <p>Use the {@link Builder} to construct instances:
 * <pre>
 * TemplateContext ctx = new TemplateContext.Builder()
 *     .deploymentTargetName("prod-east")
 *     .namespace("prod-aks-prod-east")
 *     .environment("prod")
 *     .clusterType("aks-large")
 *     .labels(Map.of("region", "eastus"))
 *     .configData(Map.of("DB_URL", "postgres://..."))
 *     .build();
 * </pre>
 */
public class TemplateContext {

    private final String deploymentTargetName;
    private final String namespace;
    private final String environment;
    private final String workspace;
    private final String workload;
    private final Map<String, String> labels;
    /** Git coordinates for the deployment-target manifests repo (repo/branch/path). */
    private final Map<String, String> manifests;
    private final String clusterType;
    private final Map<String, Object> configData;

    private TemplateContext(Builder builder) {
        this.deploymentTargetName = builder.deploymentTargetName;
        this.namespace = builder.namespace;
        this.environment = builder.environment;
        this.workspace = builder.workspace;
        this.workload = builder.workload;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(builder.labels));
        this.manifests = Collections.unmodifiableMap(new LinkedHashMap<>(builder.manifests));
        this.clusterType = builder.clusterType;
        this.configData = Collections.unmodifiableMap(new LinkedHashMap<>(builder.configData));
    }

    /** @return name of the deployment target (maps to Go {@code dataType.DeploymentTargetName}) */
    public String getDeploymentTargetName() { return deploymentTargetName; }

    /** @return target namespace where manifests are applied */
    public String getNamespace() { return namespace; }

    /** @return name of the environment this deployment target belongs to */
    public String getEnvironment() { return environment; }

    /** @return workspace that owns this workload */
    public String getWorkspace() { return workspace; }

    /** @return workload name */
    public String getWorkload() { return workload; }

    /** @return label map attached to the deployment target */
    public Map<String, String> getLabels() { return labels; }

    /** @return Git coordinates for the deployment-target's manifest repo */
    public Map<String, String> getManifests() { return manifests; }

    /** @return cluster type name */
    public String getClusterType() { return clusterType; }

    /** @return free-form config values gathered from ConfigMaps */
    public Map<String, Object> getConfigData() { return configData; }

    /**
     * Returns a {@code Map<String, Object>} suitable for Freemarker's data model.
     *
     * <p>All keys use PascalCase to match the Go operator's {@code dataType} field
     * names and the template variable references documented in {@code CLAUDE.md}.
     * Additional convenience keys {@code Repo}, {@code Branch}, and {@code Path}
     * are extracted from {@link #getManifests()} so templates can write
     * {@code ${Repo}} directly instead of {@code ${Manifests.repo}}.
     *
     * @return unmodifiable PascalCase key map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("DeploymentTargetName", deploymentTargetName);
        map.put("Namespace", namespace);
        map.put("Environment", environment);
        map.put("Workspace", workspace);
        map.put("Workload", workload);
        map.put("Labels", labels);
        map.put("Manifests", manifests);
        map.put("ClusterType", clusterType);
        map.put("ConfigData", configData);
        // Convenience shortcuts from the manifests repository reference
        map.put("Repo", manifests.getOrDefault("repo", ""));
        map.put("Branch", manifests.getOrDefault("branch", ""));
        map.put("Path", manifests.getOrDefault("path", ""));
        return Collections.unmodifiableMap(map);
    }

    /**
     * Fluent builder for {@link TemplateContext}.
     *
     * <p>All fields default to empty strings or empty maps so partial contexts
     * can be constructed for testing without specifying every field.
     */
    public static class Builder {
        private String deploymentTargetName = "";
        private String namespace = "";
        private String environment = "";
        private String workspace = "";
        private String workload = "";
        private Map<String, String> labels = new LinkedHashMap<>();
        private Map<String, String> manifests = new LinkedHashMap<>();
        private String clusterType = "";
        private Map<String, Object> configData = new LinkedHashMap<>();

        /** @param val name of the deployment target */
        public Builder deploymentTargetName(String val) { this.deploymentTargetName = val; return this; }

        /** @param val target namespace where manifests are applied */
        public Builder namespace(String val) { this.namespace = val; return this; }

        /** @param val environment name */
        public Builder environment(String val) { this.environment = val; return this; }

        /** @param val workspace name */
        public Builder workspace(String val) { this.workspace = val; return this; }

        /** @param val workload name */
        public Builder workload(String val) { this.workload = val; return this; }

        /** @param val labels map */
        public Builder labels(Map<String, String> val) { this.labels = val != null ? val : new LinkedHashMap<>(); return this; }

        /** @param val manifests repository reference (keys: repo, branch, path) */
        public Builder manifests(Map<String, String> val) { this.manifests = val != null ? val : new LinkedHashMap<>(); return this; }

        /** @param val cluster type name */
        public Builder clusterType(String val) { this.clusterType = val; return this; }

        /** @param val free-form config data from ConfigMaps */
        public Builder configData(Map<String, Object> val) { this.configData = val != null ? val : new LinkedHashMap<>(); return this; }

        /** @return new immutable {@link TemplateContext} */
        public TemplateContext build() { return new TemplateContext(this); }
    }
}
