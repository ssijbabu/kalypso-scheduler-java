package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Kubernetes list type for {@link GitOpsRepo} custom resources.
 *
 * <p>Required by the fabric8 client when performing list operations such as
 * {@code client.resources(GitOpsRepo.class, GitOpsRepoList.class).list()}.
 */
public class GitOpsRepoList extends DefaultKubernetesResourceList<GitOpsRepo> {
}
