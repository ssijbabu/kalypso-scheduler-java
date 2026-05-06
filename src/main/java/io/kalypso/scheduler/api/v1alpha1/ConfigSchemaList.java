package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Kubernetes list type for {@link ConfigSchema} resources.
 *
 * <p>Required by fabric8 for {@code LIST} and {@code WATCH} API operations.
 * Used with {@code client.resources(ConfigSchema.class, ConfigSchemaList.class).list()}.
 */
public class ConfigSchemaList extends DefaultKubernetesResourceList<ConfigSchema> {
}
