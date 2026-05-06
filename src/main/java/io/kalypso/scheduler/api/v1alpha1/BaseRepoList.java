package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Kubernetes list type for {@link BaseRepo} resources.
 *
 * <p>Required by fabric8 for {@code LIST} and {@code WATCH} API operations.
 * Used with {@code client.resources(BaseRepo.class, BaseRepoList.class).list()}.
 */
public class BaseRepoList extends DefaultKubernetesResourceList<BaseRepo> {
}
