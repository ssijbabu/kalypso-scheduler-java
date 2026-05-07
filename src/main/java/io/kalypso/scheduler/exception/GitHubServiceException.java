package io.kalypso.scheduler.exception;

/**
 * Thrown when the GitHub API call fails during pull-request creation or cleanup.
 *
 * <p>Wraps {@link java.io.IOException} from kohsuke's GitHub client so callers
 * deal with a single unchecked type.
 *
 * <p>Corresponds to error propagation in the Go operator's
 * {@code scheduler/githubrepo.go} {@code CreatePullRequest} method.
 */
public class GitHubServiceException extends RuntimeException {

    /**
     * @param message description of the operation that failed
     * @param cause   the underlying IOException from the GitHub client
     */
    public GitHubServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message description of the failure (no underlying cause)
     */
    public GitHubServiceException(String message) {
        super(message);
    }
}
