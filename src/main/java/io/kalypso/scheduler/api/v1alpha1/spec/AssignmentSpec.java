package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of an {@code Assignment} resource.
 *
 * <p>An Assignment represents a binding between a specific {@code ClusterType}
 * and a specific {@code DeploymentTarget} as computed by the
 * {@code SchedulingPolicyReconciler}. The {@code AssignmentReconciler} consumes
 * this to generate the corresponding {@code AssignmentPackage} by rendering
 * the cluster-type templates against the deployment-target context.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   clusterType:      large-aks
 *   deploymentTarget: prod-east-cluster
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignmentSpec {

    /** Name of the {@code ClusterType} resource that this assignment binds to. */
    @JsonProperty("clusterType")
    private String clusterType;

    /** Name of the {@code DeploymentTarget} resource that this assignment binds to. */
    @JsonProperty("deploymentTarget")
    private String deploymentTarget;

    /**
     * Returns the name of the bound {@code ClusterType} resource.
     *
     * @return the cluster type name, or {@code null} if not set
     */
    public String getClusterType() {
        return clusterType;
    }

    /**
     * Sets the name of the bound {@code ClusterType} resource.
     *
     * @param clusterType the cluster type name
     */
    public void setClusterType(String clusterType) {
        this.clusterType = clusterType;
    }

    /**
     * Returns the name of the bound {@code DeploymentTarget} resource.
     *
     * @return the deployment target name, or {@code null} if not set
     */
    public String getDeploymentTarget() {
        return deploymentTarget;
    }

    /**
     * Sets the name of the bound {@code DeploymentTarget} resource.
     *
     * @param deploymentTarget the deployment target name
     */
    public void setDeploymentTarget(String deploymentTarget) {
        this.deploymentTarget = deploymentTarget;
    }
}
