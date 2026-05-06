package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of a {@code WorkloadRegistration} resource.
 *
 * <p>A WorkloadRegistration registers a workload with the scheduler by providing
 * the Git repository coordinates where the workload manifests reside and the
 * workspace it belongs to. The {@code WorkloadRegistrationReconciler} uses these
 * to create Flux {@code GitRepository} and {@code Kustomization} resources.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   workload:
 *     repo:   https://github.com/org/workloads
 *     branch: main
 *     path:   ./apps/myapp
 *   workspace: team-alpha
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkloadRegistrationSpec {

    /**
     * Git repository reference pointing to the workload's manifests.
     * Used by the {@code WorkloadRegistrationReconciler} to create Flux resources
     * that sync the workload definition into the cluster.
     */
    @JsonProperty("workload")
    private RepositoryReference workload;

    /**
     * The workspace (tenant) that owns this workload registration.
     * Used for label-based selection by the {@code SchedulingPolicyReconciler}.
     */
    @JsonProperty("workspace")
    private String workspace;

    /**
     * Returns the Git repository reference for the workload manifests.
     *
     * @return the {@link RepositoryReference}, or {@code null} if not set
     */
    public RepositoryReference getWorkload() {
        return workload;
    }

    /**
     * Sets the Git repository reference for the workload manifests.
     *
     * @param workload the {@link RepositoryReference} pointing to workload manifests
     */
    public void setWorkload(RepositoryReference workload) {
        this.workload = workload;
    }

    /**
     * Returns the workspace (tenant) name for this workload registration.
     *
     * @return the workspace name, or {@code null} if not set
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Sets the workspace (tenant) name for this workload registration.
     *
     * @param workspace the workspace name
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }
}
