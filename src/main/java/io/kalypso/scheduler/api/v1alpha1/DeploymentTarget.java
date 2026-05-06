package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.status.DeploymentTargetStatus;

/**
 * Kubernetes Custom Resource representing a specific cluster (or cluster partition)
 * where workload manifests are delivered.
 *
 * <p>A {@code DeploymentTarget} is created and owned by the {@code WorkloadReconciler}
 * based on the parent {@code Workload}'s {@code spec.deploymentTargets} list.
 * The {@code SchedulingPolicyReconciler} selects DeploymentTargets using label and
 * workspace selectors to compute {@code Assignment} resources.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("DeploymentTarget")
@ShortNames("dt")
public class DeploymentTarget extends CustomResource<DeploymentTargetSpec, DeploymentTargetStatus> implements Namespaced {
}
