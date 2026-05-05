package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discriminates which rendering role a Template serves.
 *
 * <ul>
 *   <li>{@link #RECONCILER} – produces the Flux Kustomization / reconciler manifest.</li>
 *   <li>{@link #NAMESPACE} – produces the namespace manifest.</li>
 *   <li>{@link #CONFIG} – produces configuration data consumed by applications.</li>
 * </ul>
 *
 * JSON values are lowercase to match the Go source convention.
 */
public enum TemplateType {

    @JsonProperty("reconciler") RECONCILER,
    @JsonProperty("namespace")  NAMESPACE,
    @JsonProperty("config")     CONFIG
}
