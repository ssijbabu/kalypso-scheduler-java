package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Desired state of a {@code DeploymentTarget} resource.
 *
 * <p>A DeploymentTarget represents a specific cluster (or cluster partition) where
 * workload manifests should be delivered. It carries the label set that
 * {@code SchedulingPolicy} selectors match against, and the Git repository
 * coordinates for the target-specific manifests.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   name:        prod-east-cluster
 *   environment: prod
 *   labels:
 *     region: east-us
 *     tier:   production
 *   manifests:
 *     repo:   https://github.com/org/infra
 *     branch: main
 *     path:   ./clusters/prod-east
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeploymentTargetSpec {

    /**
     * Label key applied by the {@code WorkloadReconciler} to identify which workspace
     * owns a given {@code DeploymentTarget}. Used by {@code SchedulingPolicy} selectors.
     */
    public static final String WORKSPACE_LABEL = "workload.scheduler.kalypso.io/workspace";

    /**
     * Label key applied by the {@code WorkloadReconciler} to identify which workload
     * produced a given {@code DeploymentTarget}. Used by {@code SchedulingPolicy} selectors.
     */
    public static final String WORKLOAD_LABEL = "workload.scheduler.kalypso.io/workload";

    /** Logical name of this deployment target (typically matches the cluster name). */
    @JsonProperty("name")
    private String name;

    /**
     * Arbitrary key/value labels attached to this deployment target.
     * These labels are evaluated by {@link Selector#getLabelSelector()} in
     * {@code SchedulingPolicy} resources.
     */
    @JsonProperty("labels")
    private Map<String, String> labels;

    /** Name of the {@code Environment} resource this deployment target belongs to. */
    @JsonProperty("environment")
    private String environment;

    /**
     * Git repository reference pointing to the manifests for this deployment target.
     * Used by the {@code AssignmentReconciler} when generating {@code AssignmentPackage}
     * content.
     */
    @JsonProperty("manifests")
    private RepositoryReference manifests;

    /**
     * Returns the logical name of this deployment target.
     *
     * @return the target name, or {@code null} if not set
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the logical name of this deployment target.
     *
     * @param name the target name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the label map for this deployment target.
     *
     * @return the labels map, or {@code null} if not set
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Sets the label map for this deployment target.
     *
     * @param labels key/value pairs evaluated by {@code SchedulingPolicy} selectors
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    /**
     * Returns the name of the environment this deployment target belongs to.
     *
     * @return the environment name, or {@code null} if not set
     */
    public String getEnvironment() {
        return environment;
    }

    /**
     * Sets the environment name for this deployment target.
     *
     * @param environment the name of the owning {@code Environment} resource
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * Returns the Git repository reference for this target's manifests.
     *
     * @return the {@link RepositoryReference}, or {@code null} if not set
     */
    public RepositoryReference getManifests() {
        return manifests;
    }

    /**
     * Sets the Git repository reference for this target's manifests.
     *
     * @param manifests the {@link RepositoryReference} pointing to target manifests
     */
    public void setManifests(RepositoryReference manifests) {
        this.manifests = manifests;
    }
}
