package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of a {@code BaseRepo} resource.
 *
 * <p>Describes the Git repository coordinates that the {@code BaseRepoReconciler}
 * uses when creating Flux {@code GitRepository} and {@code Kustomization} resources.
 * All fields except {@code commit} are required for the operator to function.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   repo: https://github.com/org/control-plane
 *   branch: main
 *   path: ./environments
 *   commit: a1b2c3d4   # optional — pins to a specific SHA
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseRepoSpec {

    /** URL of the Git repository (HTTPS or SSH). */
    @JsonProperty("repo")
    private String repo;

    /** Branch to track within the repository. */
    @JsonProperty("branch")
    private String branch;

    /** Path within the repository root where Kustomizations are applied. */
    @JsonProperty("path")
    private String path;

    /**
     * Optional specific commit SHA to pin reconciliation to.
     * When {@code null} the operator tracks the tip of {@link #branch}.
     */
    @JsonProperty("commit")
    private String commit;

    /** @return the Git repository URL */
    public String getRepo() {
        return repo;
    }

    /** @param repo the Git repository URL */
    public void setRepo(String repo) {
        this.repo = repo;
    }

    /** @return the branch name */
    public String getBranch() {
        return branch;
    }

    /** @param branch the branch name to track */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /** @return the path within the repository */
    public String getPath() {
        return path;
    }

    /** @param path the path within the repository root */
    public void setPath(String path) {
        this.path = path;
    }

    /** @return the pinned commit SHA, or {@code null} if tracking branch head */
    public String getCommit() {
        return commit;
    }

    /** @param commit optional commit SHA; {@code null} means track branch head */
    public void setCommit(String commit) {
        this.commit = commit;
    }
}
