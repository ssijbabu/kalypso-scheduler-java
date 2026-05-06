package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of an {@code Environment} resource.
 *
 * <p>An Environment describes the GitOps control-plane coordinates for a specific
 * deployment environment (e.g. "dev", "staging", "prod"). The embedded
 * {@link RepositoryReference} tells the operator where the environment-level
 * manifests live so that Flux resources can be created to sync them.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   controlPlane:
 *     repo:   https://github.com/org/control-plane
 *     branch: main
 *     path:   ./environments/prod
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnvironmentSpec {

    /**
     * Git repository reference pointing to the control-plane manifests for this environment.
     * Used by the {@code EnvironmentReconciler} to create Flux {@code GitRepository} and
     * {@code Kustomization} resources.
     */
    @JsonProperty("controlPlane")
    private RepositoryReference controlPlane;

    /**
     * Returns the control-plane repository reference for this environment.
     *
     * @return the {@link RepositoryReference}, or {@code null} if not set
     */
    public RepositoryReference getControlPlane() {
        return controlPlane;
    }

    /**
     * Sets the control-plane repository reference for this environment.
     *
     * @param controlPlane the {@link RepositoryReference} pointing to environment manifests
     */
    public void setControlPlane(RepositoryReference controlPlane) {
        this.controlPlane = controlPlane;
    }
}
