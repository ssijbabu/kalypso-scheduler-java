package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Embedded POJO describing a single deployment target entry within a {@code Workload}.
 *
 * <p>This is NOT a standalone CRD — it is an inline spec element of
 * {@link WorkloadSpec#getDeploymentTargets()}. Each {@code WorkloadTarget} pairs
 * a logical name with the Git repository coordinates ({@link RepositoryReference})
 * that hold the manifests for that target.
 *
 * <p>Example YAML fragment within a {@code Workload} spec:
 * <pre>
 * deploymentTargets:
 *   - name: prod-east
 *     manifests:
 *       repo:   https://github.com/org/workloads
 *       branch: main
 *       path:   ./targets/prod-east
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkloadTarget {

    /** Logical name of the deployment target within this workload. */
    @JsonProperty("name")
    private String name;

    /**
     * Git repository reference pointing to the manifests specific to this
     * deployment target. Used by the {@code WorkloadReconciler} to create the
     * corresponding {@code DeploymentTarget} CRD.
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
