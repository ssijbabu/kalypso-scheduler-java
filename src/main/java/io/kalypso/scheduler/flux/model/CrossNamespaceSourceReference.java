package io.kalypso.scheduler.flux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code sourceRef} field of a Flux {@code Kustomization} resource.
 *
 * <p>References the Flux source object (typically a {@code GitRepository}) that
 * provides the Git tree the Kustomization applies. The {@code kind} and {@code name}
 * fields together uniquely identify the source within the same namespace.
 *
 * <p>Corresponds to {@code CrossNamespaceSourceReference} in the Flux kustomize API
 * ({@code kustomize.toolkit.fluxcd.io/v1beta2}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrossNamespaceSourceReference {

    /**
     * Kind of the referenced source object (e.g. {@code "GitRepository"}).
     * The Kalypso operator always sets this to {@code "GitRepository"}.
     */
    @JsonProperty("kind")
    private String kind;

    /** Name of the referenced source object in the same namespace. */
    @JsonProperty("name")
    private String name;

    /** @return the kind of the referenced source */
    public String getKind() {
        return kind;
    }

    /** @param kind the kind of the referenced source object */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /** @return the name of the referenced source */
    public String getName() {
        return name;
    }

    /** @param name the name of the referenced source object */
    public void setName(String name) {
        this.name = name;
    }
}
