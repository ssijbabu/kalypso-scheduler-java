package io.kalypso.scheduler.flux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spec of a Flux {@code GitRepository} resource
 * ({@code source.toolkit.fluxcd.io/v1beta2}).
 *
 * <p>Describes the Git repository that the Flux source controller monitors
 * and clones. The {@code FluxService} populates these fields when creating
 * a {@code GitRepository} resource on behalf of a Kalypso CRD reconciler.
 *
 * <p>Example Flux YAML:
 * <pre>
 * spec:
 *   url: https://github.com/org/control-plane
 *   interval: 1m0s
 *   ref:
 *     branch: main
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitRepositorySpec {

    /** URL of the Git repository (HTTPS or SSH). */
    @JsonProperty("url")
    private String url;

    /**
     * Reconciliation interval (e.g. {@code "1m0s"}).
     * Flux uses this to control how often the source controller polls the repo.
     */
    @JsonProperty("interval")
    private String interval;

    /** Branch reference to track within the repository. */
    @JsonProperty("ref")
    private GitRepositoryRef ref;

    /**
     * Reference to a Kubernetes {@code Secret} in the same namespace that holds
     * the Git credentials (SSH private key or HTTPS token).
     * Corresponds to {@code gitRepo.Spec.SecretRef} in the Go operator,
     * which always points at {@code "gh-repo-secret"} in {@code flux-system}.
     */
    @JsonProperty("secretRef")
    private LocalObjectReference secretRef;

    /** @return the Git repository URL */
    public String getUrl() {
        return url;
    }

    /** @param url the Git repository URL */
    public void setUrl(String url) {
        this.url = url;
    }

    /** @return the reconciliation interval string */
    public String getInterval() {
        return interval;
    }

    /** @param interval the reconciliation interval (e.g. {@code "10s"}) */
    public void setInterval(String interval) {
        this.interval = interval;
    }

    /** @return the branch reference */
    public GitRepositoryRef getRef() {
        return ref;
    }

    /** @param ref the branch reference to track */
    public void setRef(GitRepositoryRef ref) {
        this.ref = ref;
    }

    /** @return the secret reference for Git credentials */
    public LocalObjectReference getSecretRef() {
        return secretRef;
    }

    /** @param secretRef reference to the Secret holding Git credentials */
    public void setSecretRef(LocalObjectReference secretRef) {
        this.secretRef = secretRef;
    }
}
