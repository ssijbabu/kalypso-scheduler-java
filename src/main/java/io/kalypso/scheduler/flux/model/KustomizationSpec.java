package io.kalypso.scheduler.flux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spec of a Flux {@code Kustomization} resource
 * ({@code kustomize.toolkit.fluxcd.io/v1beta2}).
 *
 * <p>Describes how the Flux kustomize controller applies a set of Kubernetes
 * manifests from a Git-backed source. The {@code FluxService} populates these
 * fields when creating a {@code Kustomization} resource on behalf of a Kalypso
 * CRD reconciler.
 *
 * <p>Example Flux YAML:
 * <pre>
 * spec:
 *   sourceRef:
 *     kind: GitRepository
 *     name: my-repo
 *   path: ./environments/prod
 *   interval: 1m0s
 *   prune: true
 *   targetNamespace: kalypso-java
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KustomizationSpec {

    /**
     * Reference to the Flux source (always a {@code GitRepository} in Kalypso).
     * Must exist in the same namespace as the Kustomization.
     */
    @JsonProperty("sourceRef")
    private CrossNamespaceSourceReference sourceRef;

    /** Path within the Git repository root where the Kustomization overlay lives. */
    @JsonProperty("path")
    private String path;

    /**
     * Reconciliation interval (e.g. {@code "1m0s"}).
     * Controls how often the kustomize controller re-applies the manifests.
     */
    @JsonProperty("interval")
    private String interval;

    /**
     * When {@code true}, Flux deletes cluster resources that were previously
     * applied but are no longer present in the source manifests.
     */
    @JsonProperty("prune")
    private boolean prune;

    /**
     * Kubernetes namespace into which the kustomized resources are applied.
     * Defaults to the namespace of the Kustomization resource itself.
     */
    @JsonProperty("targetNamespace")
    private String targetNamespace;

    /** @return the source reference */
    public CrossNamespaceSourceReference getSourceRef() {
        return sourceRef;
    }

    /** @param sourceRef the Flux source to consume */
    public void setSourceRef(CrossNamespaceSourceReference sourceRef) {
        this.sourceRef = sourceRef;
    }

    /** @return the path within the repository */
    public String getPath() {
        return path;
    }

    /** @param path the path within the repository root */
    public void setPath(String path) {
        this.path = path;
    }

    /** @return the reconciliation interval string */
    public String getInterval() {
        return interval;
    }

    /** @param interval the reconciliation interval (e.g. {@code "1m0s"}) */
    public void setInterval(String interval) {
        this.interval = interval;
    }

    /** @return {@code true} if Flux should prune resources removed from the source */
    public boolean isPrune() {
        return prune;
    }

    /** @param prune whether to enable Flux pruning */
    public void setPrune(boolean prune) {
        this.prune = prune;
    }

    /** @return the target namespace for applied resources */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /** @param targetNamespace the namespace into which resources are applied */
    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }
}
