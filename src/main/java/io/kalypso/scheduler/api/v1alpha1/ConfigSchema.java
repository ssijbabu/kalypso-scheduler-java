package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.ConfigSchemaSpec;
import io.kalypso.scheduler.api.v1alpha1.status.ConfigSchemaStatus;

/**
 * Kubernetes Custom Resource that defines a JSON Schema for validating
 * cluster configuration data.
 *
 * <p>A {@code ConfigSchema} is associated with a specific {@code ClusterType} and
 * declares the shape and constraints of the configuration values that operators must
 * supply when scheduling workloads onto clusters of that type. The schema is
 * evaluated by the {@code AssignmentReconciler} before rendering templates.
 *
 * <p>This CRD has no dedicated controller; it is a passive data resource read
 * by the {@code AssignmentReconciler} during configuration validation.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("ConfigSchema")
@ShortNames("cschema")
public class ConfigSchema extends CustomResource<ConfigSchemaSpec, ConfigSchemaStatus> implements Namespaced {
}
