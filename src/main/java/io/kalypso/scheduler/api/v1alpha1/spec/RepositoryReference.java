package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared embedded POJO representing a reference to a Git repository location.
 *
 * <p>Used by {@code EnvironmentSpec}, {@code WorkloadRegistrationSpec},
 * and {@code WorkloadTarget} to describe where manifests should be sourced from.
 * All three fields are optional and omitted from JSON when {@code null}.
 *
 * <p>Example YAML:
 * <pre>
 * repo:   https://github.com/org/control-plane
 * branch: main
 * path:   ./environments
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryReference {

    /** URL of the Git repository (HTTPS or SSH). */
    @JsonProperty("repo")
    private String repo;

    /** Branch to track within the repository. */
    @JsonProperty("branch")
    private String branch;

    /** Path within the repository where manifests live. */
    @JsonProperty("path")
    private String path;

    /**
     * Returns the Git repository URL.
     *
     * @return the repository URL, or {@code null} if not set
     */
    public String getRepo() {
        return repo;
    }

    /**
     * Sets the Git repository URL.
     *
     * @param repo the Git repository URL (HTTPS or SSH)
     */
    public void setRepo(String repo) {
        this.repo = repo;
    }

    /**
     * Returns the branch name within the repository.
     *
     * @return the branch name, or {@code null} if not set
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Sets the branch name to track within the repository.
     *
     * @param branch the branch name
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Returns the path within the repository where manifests are located.
     *
     * @return the repository path, or {@code null} if not set
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the path within the repository root where manifests reside.
     *
     * @param path the path within the repository
     */
    public void setPath(String path) {
        this.path = path;
    }
}
