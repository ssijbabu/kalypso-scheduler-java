package io.kalypso.scheduler.flux.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code ref} field of a Flux {@code GitRepository} resource.
 *
 * <p>Specifies which branch (or tag/commit) of the Git repository the
 * Flux source controller should track. Only the {@code branch} field is
 * used by the Kalypso operator; the other Flux ref fields (tag, semver,
 * commit) are intentionally omitted since Kalypso always tracks a branch.
 *
 * <p>Corresponds to {@code GitRepositoryRef} in the Flux source API
 * ({@code source.toolkit.fluxcd.io/v1beta2}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GitRepositoryRef {

    /** Branch within the Git repository to track. */
    @JsonProperty("branch")
    private String branch;

    /**
     * Optional specific commit SHA to pin reconciliation to.
     * When {@code null} Flux tracks the tip of {@link #branch}.
     * Corresponds to {@code gitRepo.Spec.Reference.Commit} in the Go operator.
     */
    @JsonProperty("commit")
    private String commit;

    /** @return the branch name */
    public String getBranch() {
        return branch;
    }

    /** @param branch the branch name to track */
    public void setBranch(String branch) {
        this.branch = branch;
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
