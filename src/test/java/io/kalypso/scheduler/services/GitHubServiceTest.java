package io.kalypso.scheduler.services;

import io.kalypso.scheduler.exception.GitHubServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GitHubService}.
 *
 * <p>These tests cover the logic that does not require a live GitHub connection:
 * static file-path construction and token validation. The actual pull-request
 * creation and promotion-check methods are verified in integration tests
 * (requires a valid {@code GITHUB_AUTH_TOKEN} environment variable).
 */
class GitHubServiceTest {

    // -------------------------------------------------------------------------
    // buildFilePath — static, no GitHub connection needed
    // -------------------------------------------------------------------------

    /**
     * Verifies the standard file path structure used in Kalypso GitOps repos:
     * {@code {basePath}/{clusterType}/{deploymentTarget}/{fileName}}.
     *
     * <p>Corresponds to Go's file tree layout in {@code githubrepo.go}:
     * {@code clusterType/deploymentTarget/reconciler.yaml|namespace.yaml|platform-config.yaml}
     */
    @Test
    void testBuildFilePathStandardCase() {
        String path = GitHubService.buildFilePath("./clusters", "aks", "prod-east", "reconciler.yaml");
        assertEquals("clusters/aks/prod-east/reconciler.yaml", path);
    }

    /**
     * Verifies that a base path without the leading {@code ./} prefix is handled correctly.
     */
    @Test
    void testBuildFilePathNoDotSlashPrefix() {
        String path = GitHubService.buildFilePath("manifests", "gke", "staging-west", "namespace.yaml");
        assertEquals("manifests/gke/staging-west/namespace.yaml", path);
    }

    /**
     * Verifies that an empty base path produces a path starting directly with the cluster type.
     */
    @Test
    void testBuildFilePathEmptyBasePath() {
        String path = GitHubService.buildFilePath("", "k3s", "dev", "platform-config.yaml");
        assertEquals("k3s/dev/platform-config.yaml", path);
    }

    /**
     * Verifies that a trailing slash in the base path is stripped to avoid double slashes.
     */
    @Test
    void testBuildFilePathTrailingSlashInBase() {
        String path = GitHubService.buildFilePath("./clusters/", "aks", "prod-east", "reconciler.yaml");
        assertEquals("clusters/aks/prod-east/reconciler.yaml", path);
    }

    /**
     * Verifies the platform-config file name constant matches the Go operator constant.
     * Go: {@code configName = "platform-config"}
     */
    @Test
    void testFileNameConstants() {
        assertEquals("reconciler", GitHubService.RECONCILER_NAME);
        assertEquals("namespace", GitHubService.NAMESPACE_NAME);
        assertEquals("platform-config", GitHubService.CONFIG_NAME);
    }

    /**
     * Verifies that the author and commit message constants match the Go operator values.
     * Go: {@code "Kalypso Scheduler"}, {@code "kalypso.scheduler@email.com"},
     * {@code "Kalypso Scheduler commit"}.
     */
    @Test
    void testAuthorAndCommitConstants() {
        assertEquals("Kalypso Scheduler", GitHubService.AUTHOR_NAME);
        assertEquals("kalypso.scheduler@email.com", GitHubService.AUTHOR_EMAIL);
        assertEquals("Kalypso Scheduler commit", GitHubService.COMMIT_MESSAGE);
    }

    /**
     * Verifies that calling a GitHub API method without a token throws
     * {@link GitHubServiceException} with a descriptive message.
     */
    @Test
    void testMissingTokenThrowsOnApiCall() {
        GitHubService service = new GitHubService((String) null);
        GitHubServiceException ex = assertThrows(GitHubServiceException.class,
                () -> service.cleanPullRequests("org/repo", "main"));
        assertTrue(ex.getMessage().contains("GITHUB_AUTH_TOKEN"),
                "Exception message must mention the environment variable name");
    }

    /**
     * Verifies that a blank (empty string) token also triggers the error on API calls.
     */
    @Test
    void testBlankTokenThrowsOnApiCall() {
        GitHubService service = new GitHubService("");
        assertThrows(GitHubServiceException.class,
                () -> service.isPromoted("org/repo", "main"));
    }
}
