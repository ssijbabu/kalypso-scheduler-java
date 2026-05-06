package io.kalypso.scheduler.flux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A reference to a Kubernetes {@code Secret} or {@code ConfigMap} in the same namespace.
 *
 * <p>Used as {@code spec.secretRef} in a Flux {@code GitRepository} to point at the
 * {@code Secret} that holds Git credentials (SSH private key or HTTPS token). The
 * secret must exist in the same namespace as the {@code GitRepository} resource —
 * which in Kalypso is always {@code flux-system}.
 *
 * <p>Corresponds to {@code meta.LocalObjectReference} in the Flux API package
 * ({@code github.com/fluxcd/pkg/apis/meta}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocalObjectReference {

    /** Name of the referenced Kubernetes object (Secret or ConfigMap). */
    @JsonProperty("name")
    private String name;

    /** @return the name of the referenced object */
    public String getName() {
        return name;
    }

    /** @param name the name of the referenced Kubernetes object */
    public void setName(String name) {
        this.name = name;
    }
}
