package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Specifies how configuration data is stored and delivered to a cluster.
 *
 * <ul>
 *   <li>{@link #CONFIGMAP} – configuration is materialised as a Kubernetes ConfigMap.</li>
 *   <li>{@link #ENVFILE}   – configuration is written as an environment-variable file.</li>
 * </ul>
 *
 * JSON values are lowercase to match the Go source convention.
 */
public enum ClusterConfigType {

    @JsonProperty("configmap") CONFIGMAP,
    @JsonProperty("envfile")   ENVFILE
}
