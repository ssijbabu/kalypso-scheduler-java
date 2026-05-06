package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of a {@code GitOpsRepo} resource.
 *
 * <p>A GitOpsRepo points to the GitHub repository where the operator will deliver
 * rendered manifests via Pull Requests. The {@code GitOpsRepoReconciler} uses these
 * coordinates when creating branches and opening PRs through the GitHub API.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   repo:   https://github.com/org/gitops-manifests
 *   branch: main
 *   path:   ./clusters
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitOpsRepoSpec {

    /** URL of the target GitHub repository (HTTPS format). */
    @JsonProperty("repo")
    private String repo;

    /** Base branch that Pull Requests will target. */
    @JsonProperty("branch")
    private String branch;

    /** Path prefix within the repository where rendered manifests are placed. */
    @JsonProperty("path")
    private String path;

    /**
     * Returns the URL of the target GitHub repository.
     *
     * @return the repository URL, or {@code null} if not set
     */
    public String getRepo() {
        return repo;
    }

    /**
     * Sets the URL of the target GitHub repository.
     *
     * @param repo the repository URL (HTTPS format)
     */
    public void setRepo(String repo) {
        this.repo = repo;
    }

    /**
     * Returns the base branch that Pull Requests will target.
     *
     * @return the branch name, or {@code null} if not set
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Sets the base branch that Pull Requests will target.
     *
     * @param branch the branch name (e.g. "main")
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Returns the path prefix within the repository for rendered manifests.
     *
     * @return the path, or {@code null} if not set
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the path prefix within the repository where rendered manifests are placed.
     *
     * @param path the path prefix (e.g. "./clusters")
     */
    public void setPath(String path) {
        this.path = path;
    }
}
