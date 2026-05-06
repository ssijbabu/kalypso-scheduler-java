package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Desired state of a {@code Workload} resource.
 *
 * <p>A Workload declares the set of deployment targets for a given application.
 * The {@code WorkloadReconciler} reconciles the list of {@link WorkloadTarget} entries
 * against the actual {@code DeploymentTarget} CRDs in the namespace, creating,
 * updating, or deleting them as needed.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   deploymentTargets:
 *     - name: prod-east
 *       manifests:
 *         repo:   https://github.com/org/workloads
 *         branch: main
 *         path:   ./targets/prod-east
 *     - name: prod-west
 *       manifests:
 *         repo:   https://github.com/org/workloads
 *         branch: main
 *         path:   ./targets/prod-west
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkloadSpec {

    /**
     * List of deployment targets declared for this workload.
     * The {@code WorkloadReconciler} creates a {@code DeploymentTarget} CRD
     * for each entry and removes any stale ones not present in this list.
     */
    @JsonProperty("deploymentTargets")
    private List<WorkloadTarget> deploymentTargets = new ArrayList<>();

    /**
     * Returns the list of deployment targets for this workload.
     *
     * @return the deployment targets list; never {@code null}
     */
    public List<WorkloadTarget> getDeploymentTargets() {
        return deploymentTargets;
    }

    /**
     * Sets the list of deployment targets for this workload.
     *
     * @param deploymentTargets the list of {@link WorkloadTarget} entries;
     *                          {@code null} is treated as an empty list
     */
    public void setDeploymentTargets(List<WorkloadTarget> deploymentTargets) {
        this.deploymentTargets = deploymentTargets != null ? deploymentTargets : new ArrayList<>();
    }
}
