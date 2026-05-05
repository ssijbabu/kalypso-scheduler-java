package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Kubernetes list type for {@link ClusterType} resources.
 *
 * <p>Required by fabric8 for {@code LIST} and {@code WATCH} API operations.
 * The fabric8 CRD generator uses this class to produce the plural form of the
 * CRD ({@code clustertypes}) in the generated YAML.
 */
public class ClusterTypeList extends DefaultKubernetesResourceList<ClusterType> {
}
