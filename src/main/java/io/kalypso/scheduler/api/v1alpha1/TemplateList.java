package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Kubernetes list type for {@link Template} resources.
 *
 * <p>Required by fabric8 for {@code LIST} and {@code WATCH} API operations.
 * The fabric8 CRD generator uses this class to produce the plural form of the
 * CRD ({@code templates}) in the generated YAML.
 */
public class TemplateList extends DefaultKubernetesResourceList<Template> {
}
