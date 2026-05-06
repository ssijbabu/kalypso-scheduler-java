package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.EnvironmentSpec;
import io.kalypso.scheduler.api.v1alpha1.status.EnvironmentStatus;

/**
 * Kubernetes Custom Resource representing a deployment environment.
 *
 * <p>An {@code Environment} describes the GitOps control-plane coordinates for a
 * specific environment (e.g. "dev", "staging", "prod"). The
 * {@code EnvironmentReconciler} creates Flux {@code GitRepository} and
 * {@code Kustomization} resources from the {@code spec.controlPlane} field
 * and reflects the outcome in {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("Environment")
@ShortNames("env")
public class Environment extends CustomResource<EnvironmentSpec, EnvironmentStatus> implements Namespaced {
}
