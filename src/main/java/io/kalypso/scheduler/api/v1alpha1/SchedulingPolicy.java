package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.SchedulingPolicySpec;
import io.kalypso.scheduler.api.v1alpha1.status.SchedulingPolicyStatus;

/**
 * Kubernetes Custom Resource that defines pairing rules between
 * {@code DeploymentTarget} and {@code ClusterType} resources.
 *
 * <p>The {@code SchedulingPolicyReconciler} evaluates both selectors in the spec
 * against available resources in the namespace and creates an {@code Assignment}
 * CRD for every matching (DeploymentTarget, ClusterType) pair. Old assignments
 * that no longer match are deleted. The outcome is reflected in
 * {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("SchedulingPolicy")
@ShortNames("sp")
public class SchedulingPolicy extends CustomResource<SchedulingPolicySpec, SchedulingPolicyStatus> implements Namespaced {
}
