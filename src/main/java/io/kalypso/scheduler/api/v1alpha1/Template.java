package io.kalypso.scheduler.api.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateSpec;
import io.kalypso.scheduler.api.v1alpha1.status.TemplateStatus;

/**
 * Kubernetes Custom Resource representing a Freemarker manifest template.
 *
 * <p>A {@code Template} holds one or more named Freemarker sources along with
 * metadata that identifies their rendering role (reconciler, namespace, or config).
 * Templates are referenced by {@code ClusterType} and rendered by the
 * {@code AssignmentReconciler} to produce manifests stored in an
 * {@code AssignmentPackage}.
 *
 * <p>This CRD has no dedicated controller; it is a passive data resource consumed
 * by other reconcilers.
 *
 * <p>API group/version: {@code scheduler.kalypso.io/v1alpha1}
 */
@Group("scheduler.kalypso.io")
@Version("v1alpha1")
@Kind("Template")
@ShortNames("tmpl")
public class Template extends CustomResource<TemplateSpec, TemplateStatus> implements Namespaced {
}
