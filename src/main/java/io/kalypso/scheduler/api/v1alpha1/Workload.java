package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadSpec;
import io.kalypso.scheduler.api.v1alpha1.status.WorkloadStatus;

/**
 * Kubernetes Custom Resource representing an application workload.
 *
 * <p>A {@code Workload} declares the set of deployment targets for an application.
 * The {@code WorkloadReconciler} reconciles the {@code spec.deploymentTargets} list
 * against actual {@code DeploymentTarget} CRDs in the namespace — creating, updating,
 * or deleting them as needed — and reflects the outcome in {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("Workload")
@ShortNames("wl")
public class Workload extends CustomResource<WorkloadSpec, WorkloadStatus> implements Namespaced {
}
