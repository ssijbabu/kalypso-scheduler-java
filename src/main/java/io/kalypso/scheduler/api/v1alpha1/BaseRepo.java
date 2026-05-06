package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.BaseRepoSpec;
import io.kalypso.scheduler.api.v1alpha1.status.BaseRepoStatus;

/**
 * Kubernetes Custom Resource representing a Git repository that serves as the
 * base (control-plane) source for Kalypso scheduling.
 *
 * <p>A {@code BaseRepo} points to a Git repository and branch that the operator
 * monitors via Flux {@code GitRepository} and {@code Kustomization} resources.
 * The {@code BaseRepoReconciler} creates or deletes the corresponding Flux
 * resources and reflects the outcome in {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("BaseRepo")
@ShortNames("br")
public class BaseRepo extends CustomResource<BaseRepoSpec, BaseRepoStatus> implements Namespaced {
}
