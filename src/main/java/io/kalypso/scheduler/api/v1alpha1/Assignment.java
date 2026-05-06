package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentSpec;
import io.kalypso.scheduler.api.v1alpha1.status.AssignmentStatus;

/**
 * Kubernetes Custom Resource representing a binding between a {@code ClusterType}
 * and a {@code DeploymentTarget}.
 *
 * <p>An {@code Assignment} is created by the {@code SchedulingPolicyReconciler} for
 * every (ClusterType, DeploymentTarget) pair that satisfies a {@code SchedulingPolicy}.
 * The {@code AssignmentReconciler} then processes each Assignment to generate the
 * corresponding {@code AssignmentPackage} by rendering cluster-type templates against
 * the deployment-target context. Outcomes are reflected in {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("Assignment")
@ShortNames("asgn")
public class Assignment extends CustomResource<AssignmentSpec, AssignmentStatus> implements Namespaced {
}
