package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.ClusterTypeSpec;
import io.kalypso.scheduler.api.v1alpha1.status.ClusterTypeStatus;

/**
 * Kubernetes Custom Resource representing a cluster-type blueprint.
 *
 * <p>A {@code ClusterType} groups the three {@link Template} references
 * (reconciler, namespace, config) that define how manifests are generated for a
 * particular class of target cluster (e.g. "large-aks", "edge-k3s").
 * It also declares how configuration data is delivered ({@code configType}).
 *
 * <p>The {@code SchedulingPolicyReconciler} selects ClusterTypes by label when
 * computing {@code Assignment} resources.
 *
 * <p>This CRD has no dedicated controller; it is a passive data resource consumed
 * by the {@code AssignmentReconciler}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("ClusterType")
@ShortNames("ct")
public class ClusterType extends CustomResource<ClusterTypeSpec, ClusterTypeStatus> implements Namespaced {
}
