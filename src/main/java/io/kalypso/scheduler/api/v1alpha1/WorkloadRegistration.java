package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadRegistrationSpec;
import io.kalypso.scheduler.api.v1alpha1.status.WorkloadRegistrationStatus;

/**
 * Kubernetes Custom Resource that registers a workload with the Kalypso scheduler.
 *
 * <p>A {@code WorkloadRegistration} declares the Git repository where a workload's
 * manifests live and the workspace (tenant) that owns it. The
 * {@code WorkloadRegistrationReconciler} creates Flux {@code GitRepository} and
 * {@code Kustomization} resources so the workload definition is synced into the
 * cluster and reflects the outcome in {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("WorkloadRegistration")
@ShortNames("wreg")
public class WorkloadRegistration extends CustomResource<WorkloadRegistrationSpec, WorkloadRegistrationStatus> implements Namespaced {
}
