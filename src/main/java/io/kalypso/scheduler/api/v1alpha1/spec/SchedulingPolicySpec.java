package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of a {@code SchedulingPolicy} resource.
 *
 * <p>A SchedulingPolicy defines the pairing rules between {@code DeploymentTarget}
 * and {@code ClusterType} resources. The {@code SchedulingPolicyReconciler} evaluates
 * both selectors and creates an {@code Assignment} CRD for every matching
 * (DeploymentTarget, ClusterType) pair.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   deploymentTargetSelector:
 *     workspace: team-alpha
 *     labelSelector:
 *       tier: production
 *   clusterTypeSelector:
 *     labelSelector:
 *       size: large
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchedulingPolicySpec {

    /**
     * Selector used to match {@code DeploymentTarget} resources that this policy
     * applies to. Both {@link Selector#getWorkspace()} and
     * {@link Selector#getLabelSelector()} are evaluated when set.
     */
    @JsonProperty("deploymentTargetSelector")
    private Selector deploymentTargetSelector;

    /**
     * Selector used to match {@code ClusterType} resources that this policy
     * applies to. Both {@link Selector#getWorkspace()} and
     * {@link Selector#getLabelSelector()} are evaluated when set.
     */
    @JsonProperty("clusterTypeSelector")
    private Selector clusterTypeSelector;

    /**
     * Returns the selector for matching {@code DeploymentTarget} resources.
     *
     * @return the {@link Selector}, or {@code null} if not set
     */
    public Selector getDeploymentTargetSelector() {
        return deploymentTargetSelector;
    }

    /**
     * Sets the selector for matching {@code DeploymentTarget} resources.
     *
     * @param deploymentTargetSelector the {@link Selector} to apply to DeploymentTargets
     */
    public void setDeploymentTargetSelector(Selector deploymentTargetSelector) {
        this.deploymentTargetSelector = deploymentTargetSelector;
    }

    /**
     * Returns the selector for matching {@code ClusterType} resources.
     *
     * @return the {@link Selector}, or {@code null} if not set
     */
    public Selector getClusterTypeSelector() {
        return clusterTypeSelector;
    }

    /**
     * Sets the selector for matching {@code ClusterType} resources.
     *
     * @param clusterTypeSelector the {@link Selector} to apply to ClusterTypes
     */
    public void setClusterTypeSelector(Selector clusterTypeSelector) {
        this.clusterTypeSelector = clusterTypeSelector;
    }
}
