package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes the format of rendered manifest content stored in an AssignmentPackage.
 *
 * <ul>
 *   <li>{@link #YAML} – standard Kubernetes YAML manifest.</li>
 *   <li>{@link #SH}   – shell script (e.g. for imperative setup steps).</li>
 * </ul>
 *
 * JSON values are lowercase to match the Go source convention.
 */
public enum ContentType {

    @JsonProperty("yaml") YAML,
    @JsonProperty("sh")   SH
}
