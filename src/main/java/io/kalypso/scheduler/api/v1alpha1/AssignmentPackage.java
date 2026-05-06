package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.status.AssignmentPackageStatus;

/**
 * Kubernetes Custom Resource that holds the fully rendered manifests for a specific
 * (ClusterType, DeploymentTarget) assignment.
 *
 * <p>An {@code AssignmentPackage} is created and owned by the
 * {@code AssignmentReconciler}. It contains three sets of rendered manifest strings
 * grouped by role (reconciler, namespace, config). The
 * {@code GitOpsRepoReconciler} aggregates all packages in the namespace and opens
 * a GitHub PR to deliver the manifests to the target GitOps repository.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("AssignmentPackage")
@ShortNames("apkg")
public class AssignmentPackage extends CustomResource<AssignmentPackageSpec, AssignmentPackageStatus> implements Namespaced {
}
