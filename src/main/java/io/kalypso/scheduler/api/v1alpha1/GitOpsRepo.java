package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.GitOpsRepoSpec;
import io.kalypso.scheduler.api.v1alpha1.status.GitOpsRepoStatus;

/**
 * Kubernetes Custom Resource pointing to the GitHub repository that receives
 * rendered manifests via Pull Requests.
 *
 * <p>The {@code GitOpsRepoReconciler} monitors all {@code AssignmentPackage}
 * resources in the namespace, organizes the rendered manifests into a directory
 * structure, and opens a GitHub Pull Request against the repository and branch
 * declared in this resource's spec. Outcomes are reflected in
 * {@code status.conditions}.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("GitOpsRepo")
@ShortNames("gor")
public class GitOpsRepo extends CustomResource<GitOpsRepoSpec, GitOpsRepoStatus> implements Namespaced {
}
