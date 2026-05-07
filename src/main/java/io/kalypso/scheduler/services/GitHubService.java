package io.kalypso.scheduler.services;

import io.kalypso.scheduler.exception.GitHubServiceException;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeBuilder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Creates and cleans up Kalypso pull requests in a GitHub repository.
 *
 * <p>This is the Java equivalent of the Go operator's {@code scheduler/githubrepo.go}
 * {@code GithubRepo} interface. The Go implementation uses {@code google/go-github};
 * this implementation uses kohsuke/github-api 1.321.
 *
 * <h3>Go → Java constant mapping</h3>
 * <table>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr><td>{@code reconcilerName = "reconciler"}</td><td>{@link #RECONCILER_NAME}</td></tr>
 *   <tr><td>{@code namespaceName  = "namespace"}</td><td>{@link #NAMESPACE_NAME}</td></tr>
 *   <tr><td>{@code configName     = "platform-config"}</td><td>{@link #CONFIG_NAME}</td></tr>
 *   <tr><td>{@code "Kalypso Scheduler"}</td><td>{@link #AUTHOR_NAME}</td></tr>
 *   <tr><td>{@code "kalypso.scheduler@email.com"}</td><td>{@link #AUTHOR_EMAIL}</td></tr>
 *   <tr><td>{@code "Kalypso Scheduler commit"}</td><td>{@link #COMMIT_MESSAGE}</td></tr>
 * </table>
 *
 * <h3>Pull-request lifecycle</h3>
 * <ol>
 *   <li>{@link #cleanPullRequests} — closes all open PRs whose head branch starts
 *       with {@value #BRANCH_PREFIX} on the given base branch.</li>
 *   <li>{@link #createPullRequest} — creates a new branch, writes all manifest files
 *       in a single atomic commit using the Git Trees API, then opens a PR.</li>
 * </ol>
 *
 * <h3>File structure</h3>
 * Files are placed at:
 * <pre>
 * {basePath}/{clusterType}/{deploymentTarget}/reconciler.yaml
 * {basePath}/{clusterType}/{deploymentTarget}/namespace.yaml
 * {basePath}/{clusterType}/{deploymentTarget}/platform-config.yaml
 * </pre>
 * Use {@link #buildFilePath} to construct these paths deterministically.
 *
 * <h3>Authentication</h3>
 * The GitHub token is read from the {@code GITHUB_AUTH_TOKEN} environment variable,
 * matching the Go operator's {@code os.Getenv("GITHUB_AUTH_TOKEN")}.
 */
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);

    /** Standard file name for the reconciler manifest. Matches Go {@code reconcilerName}. */
    public static final String RECONCILER_NAME = "reconciler";

    /** Standard file name for the namespace manifest. Matches Go {@code namespaceName}. */
    public static final String NAMESPACE_NAME = "namespace";

    /** Standard file name for the platform configuration manifest. Matches Go {@code configName}. */
    public static final String CONFIG_NAME = "platform-config";

    /** Git commit author name. Matches the Go operator constant. */
    public static final String AUTHOR_NAME = "Kalypso Scheduler";

    /** Git commit author email. Matches the Go operator constant. */
    public static final String AUTHOR_EMAIL = "kalypso.scheduler@email.com";

    /** Commit message used for all Kalypso-generated commits. Matches the Go operator constant. */
    public static final String COMMIT_MESSAGE = "Kalypso Scheduler commit";

    /** Prefix for PR branch names — used to identify and clean up Kalypso branches. */
    public static final String BRANCH_PREFIX = "kalypso-";

    /** Environment variable name for the GitHub personal access token. */
    public static final String TOKEN_ENV_VAR = "GITHUB_AUTH_TOKEN";

    /** Path in the repo checked to determine if a branch has been promoted. */
    static final String PROMOTED_COMMIT_TRACKING_FILE = ".github/tracking/Promoted_Commit_Id";

    private final String token;

    /**
     * Constructs a {@code GitHubService} reading the token from the
     * {@value #TOKEN_ENV_VAR} environment variable.
     *
     * @throws GitHubServiceException if the environment variable is not set
     */
    public GitHubService() {
        this(System.getenv(TOKEN_ENV_VAR));
    }

    /**
     * Constructs a {@code GitHubService} with the given token.
     * Use this constructor in tests or when the token is injected via other means.
     *
     * @param token GitHub personal access token; must not be {@code null} or empty
     *              when actually calling GitHub APIs
     */
    public GitHubService(String token) {
        this.token = token;
    }

    /**
     * Creates a pull request in the given repository with the supplied file contents.
     *
     * <p>Mirrors Go's {@code CreatePullRequest} method in {@code githubrepo.go}.
     * The implementation:
     * <ol>
     *   <li>Calls {@link #cleanPullRequests} to close any previously open Kalypso PRs.</li>
     *   <li>Builds a new Git tree with all file contents in a single atomic operation.</li>
     *   <li>Creates a commit pointing at the new tree.</li>
     *   <li>Creates a new branch and opens a PR targeting {@code baseBranch}.</li>
     * </ol>
     *
     * @param repoFullName full GitHub repository name (e.g. {@code "org/repo"})
     * @param prTitle      title for the new pull request
     * @param baseBranch   target branch the PR merges into
     * @param fileContents map of relative file path → file content to include in the PR
     * @return the created {@link GHPullRequest}
     * @throws GitHubServiceException if any GitHub API call fails
     */
    public GHPullRequest createPullRequest(String repoFullName, String prTitle,
                                            String baseBranch,
                                            Map<String, String> fileContents) {
        logger.info("Creating PR in {}: title='{}', branch={}, files={}",
                repoFullName, prTitle, baseBranch, fileContents.size());
        try {
            GitHub github = buildClient();
            GHRepository repo = github.getRepository(repoFullName);

            cleanPullRequests(repo, baseBranch);

            GHBranch base = repo.getBranch(baseBranch);
            String baseSha = base.getSHA1();
            GHCommit baseCommit = repo.getCommit(baseSha);
            String baseTreeSha = baseCommit.getTree().getSha();

            GHTreeBuilder treeBuilder = repo.createTree().baseTree(baseTreeSha);
            for (Map.Entry<String, String> file : fileContents.entrySet()) {
                treeBuilder.add(file.getKey(), file.getValue(), false);
            }
            GHTree newTree = treeBuilder.create();

            GHCommit newCommit = repo.createCommit()
                    .message(COMMIT_MESSAGE)
                    .tree(newTree.getSha())
                    .parent(baseSha)
                    .create();

            String branchName = BRANCH_PREFIX + System.currentTimeMillis();
            repo.createRef("refs/heads/" + branchName, newCommit.getSHA1());

            GHPullRequest pr = repo.createPullRequest(prTitle, branchName, baseBranch, "");
            logger.info("Created PR #{} in {}: {}", pr.getNumber(), repoFullName, pr.getHtmlUrl());
            return pr;
        } catch (IOException e) {
            throw new GitHubServiceException(
                    "Failed to create PR in " + repoFullName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether a branch in the given repository has been promoted.
     *
     * <p>Mirrors Go's {@code isPromoted} helper in {@code githubrepo.go}.
     * A branch is considered promoted when the file
     * {@value #PROMOTED_COMMIT_TRACKING_FILE} exists on that branch.
     *
     * @param repoFullName full GitHub repository name
     * @param branch       branch to check
     * @return {@code true} if the tracking file exists; {@code false} otherwise
     * @throws GitHubServiceException if the repository cannot be accessed
     */
    public boolean isPromoted(String repoFullName, String branch) {
        try {
            GitHub github = buildClient();
            GHRepository repo = github.getRepository(repoFullName);
            repo.getFileContent(PROMOTED_COMMIT_TRACKING_FILE, branch);
            return true;
        } catch (org.kohsuke.github.GHFileNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new GitHubServiceException(
                    "Failed to check promotion status in " + repoFullName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Builds the relative file path for a manifest within a GitOps repository.
     *
     * <p>Follows the Kalypso file structure convention:
     * {@code {basePath}/{clusterType}/{deploymentTarget}/{fileName}}
     *
     * <p>Leading {@code ./} is stripped from {@code basePath} since GitHub API
     * paths must not start with {@code ./}.
     *
     * @param basePath         base path from {@code GitOpsRepoSpec.path} (e.g. {@code "./clusters"})
     * @param clusterType      cluster type name
     * @param deploymentTarget deployment target name
     * @param fileName         file name with extension (e.g. {@code "reconciler.yaml"})
     * @return relative file path (e.g. {@code "clusters/aks/prod-east/reconciler.yaml"})
     */
    public static String buildFilePath(String basePath, String clusterType,
                                        String deploymentTarget, String fileName) {
        String cleanBase = basePath.replaceAll("^\\./", "").replaceAll("/$", "");
        if (cleanBase.isEmpty()) {
            return String.join("/", clusterType, deploymentTarget, fileName);
        }
        return String.join("/", cleanBase, clusterType, deploymentTarget, fileName);
    }

    /**
     * Closes all open pull requests whose head branch starts with {@value #BRANCH_PREFIX}
     * and targets the given base branch.
     *
     * <p>Mirrors Go's {@code cleanPullRequests} helper in {@code githubrepo.go}.
     *
     * @param repoFullName full GitHub repository name
     * @param baseBranch   the base branch to filter PRs by
     * @throws GitHubServiceException if the GitHub API call fails
     */
    public void cleanPullRequests(String repoFullName, String baseBranch) {
        try {
            GitHub github = buildClient();
            GHRepository repo = github.getRepository(repoFullName);
            cleanPullRequests(repo, baseBranch);
        } catch (IOException e) {
            throw new GitHubServiceException(
                    "Failed to clean PRs in " + repoFullName + ": " + e.getMessage(), e);
        }
    }

    private void cleanPullRequests(GHRepository repo, String baseBranch) throws IOException {
        List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
        for (GHPullRequest pr : prs) {
            String headRef = pr.getHead().getRef();
            String prBase = pr.getBase().getRef();
            if (headRef.startsWith(BRANCH_PREFIX) && baseBranch.equals(prBase)) {
                logger.info("Closing existing Kalypso PR #{}: {}", pr.getNumber(), headRef);
                pr.close();
                try {
                    repo.getRef("heads/" + headRef).delete();
                } catch (IOException ignored) {
                    // Branch may already be deleted; not a failure
                }
            }
        }
    }

    private GitHub buildClient() throws IOException {
        if (token == null || token.isBlank()) {
            throw new GitHubServiceException(
                    "GitHub token is not set. Provide it via the " + TOKEN_ENV_VAR + " environment variable.");
        }
        return new GitHubBuilder().withOAuthToken(token).build();
    }
}
