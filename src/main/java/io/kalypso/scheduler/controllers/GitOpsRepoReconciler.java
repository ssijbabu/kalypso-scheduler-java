package io.kalypso.scheduler.controllers;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.GitOpsRepo;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicy;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciler for {@code GitOpsRepo} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code GitOpsRepoReconciler} in the Go operator's
 * {@code controllers/gitopsrepo_controller.go}.
 *
 * <p><strong>Reconcile path</strong>:
 * <ol>
 *   <li>Checks that all {@code SchedulingPolicy} resources in the namespace are
 *       {@code Ready=True}. If any policy is not ready, the reconciler sets
 *       {@code Ready=False} and returns — manifest generation would be incomplete.</li>
 *   <li>Lists all {@code AssignmentPackage} resources in the namespace.</li>
 *   <li>For each package, organises its rendered manifests into the GitOps directory
 *       structure: {@code {basePath}/{clusterType}/{deploymentTarget}/{fileName}}.</li>
 *   <li>Calls {@link GitHubService#createPullRequest} to open a Pull Request in the
 *       target GitHub repository with all the rendered manifest files.</li>
 *   <li>Sets {@code status.conditions[Ready]=True} when the PR is created.</li>
 * </ol>
 *
 * <p><strong>File structure</strong> (matches Go operator's {@code getTree}):
 * <pre>
 * {basePath}/{clusterType}/{deploymentTarget}/reconciler.yaml   (or .sh)
 * {basePath}/{clusterType}/{deploymentTarget}/namespace.yaml    (or .sh)
 * {basePath}/{clusterType}/{deploymentTarget}/platform-config.yaml (if present)
 * </pre>
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): finalizer is removed
 * immediately — no resources other than the GitOpsRepo itself need cleanup because
 * PRs are external state managed by GitHub.
 */
@ControllerConfiguration
public class GitOpsRepoReconciler implements Reconciler<GitOpsRepo>, Cleaner<GitOpsRepo> {

    private static final Logger logger = LoggerFactory.getLogger(GitOpsRepoReconciler.class);

    /** File extension for YAML manifests. */
    private static final String YAML_EXT = ".yaml";

    /** File extension for shell-script manifests. */
    private static final String SH_EXT = ".sh";

    private final io.fabric8.kubernetes.client.KubernetesClient kubernetesClient;
    private final GitHubService gitHubService;

    /**
     * Constructs the reconciler.
     *
     * @param kubernetesClient fabric8 client for listing policies and packages
     * @param gitHubService    service used to create GitHub Pull Requests
     */
    public GitOpsRepoReconciler(io.fabric8.kubernetes.client.KubernetesClient kubernetesClient,
                                 GitHubService gitHubService) {
        this.kubernetesClient = kubernetesClient;
        this.gitHubService = gitHubService;
    }

    /**
     * Reconciles a {@code GitOpsRepo} resource.
     *
     * <p>Waits for all {@code SchedulingPolicy} resources to be ready, then
     * aggregates all {@code AssignmentPackage} manifests and opens a GitHub Pull
     * Request. Sets {@code status.conditions[Ready]=True} when the PR is created.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist updated conditions
     */
    @Override
    public UpdateControl<GitOpsRepo> reconcile(GitOpsRepo resource, Context<GitOpsRepo> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Reconciling GitOpsRepo: name={}, namespace={}", name, namespace);

        try {
            // Step 1: gate on all SchedulingPolicies being ready
            if (!allPoliciesReady(namespace)) {
                StatusConditionHelper.setNotReady(
                        resource.getStatus().getConditions(),
                        "PoliciesNotReady",
                        "Waiting for all SchedulingPolicy resources to be Ready");
                logger.info("GitOpsRepo {} waiting: not all SchedulingPolicies are ready", name);
                return UpdateControl.patchStatus(resource);
            }

            // Step 2: collect all AssignmentPackages
            List<AssignmentPackage> packages = kubernetesClient
                    .resources(AssignmentPackage.class)
                    .inNamespace(namespace)
                    .list().getItems();

            if (packages.isEmpty()) {
                StatusConditionHelper.setNotReady(
                        resource.getStatus().getConditions(),
                        "NoPackages",
                        "No AssignmentPackage resources found in namespace");
                return UpdateControl.patchStatus(resource);
            }

            // Step 3: build file map for the PR
            Map<String, String> fileContents = buildFileContents(
                    resource.getSpec().getPath(), packages);

            // Step 4: create the GitHub PR
            String repoFullName = extractRepoFullName(resource.getSpec().getRepo());
            String prTitle = "Kalypso manifest update for " + namespace;

            gitHubService.createPullRequest(
                    repoFullName,
                    prTitle,
                    resource.getSpec().getBranch(),
                    fileContents);

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "PRCreated",
                    "Pull Request created in " + repoFullName);
            logger.info("GitOpsRepo reconciled successfully: {} — PR created in {}", name, repoFullName);

        } catch (Exception e) {
            logger.error("Failed to reconcile GitOpsRepo: {}", name, e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "ReconcileError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Removes the finalizer immediately — PR-based delivery is external state and
     * requires no Kubernetes-side cleanup.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(GitOpsRepo resource, Context<GitOpsRepo> context) {
        logger.info("Cleaning up GitOpsRepo: {}", resource.getMetadata().getName());
        return DeleteControl.defaultDelete();
    }

    /**
     * Returns {@code true} if every {@code SchedulingPolicy} in the namespace has a
     * {@code Ready=True} condition.
     *
     * <p>Mirrors the Go operator's dependency-readiness gate before PR creation.
     * Package-private for unit testing.
     *
     * @param namespace the Kubernetes namespace to check
     * @return {@code true} when all policies are ready; {@code false} otherwise
     */
    boolean allPoliciesReady(String namespace) {
        List<SchedulingPolicy> policies = kubernetesClient
                .resources(SchedulingPolicy.class).inNamespace(namespace)
                .list().getItems();

        if (policies.isEmpty()) {
            // No policies means nothing to wait for
            return true;
        }

        return policies.stream().allMatch(this::isReady);
    }

    /**
     * Checks whether a resource has a {@code Ready=True} condition.
     *
     * @param policy the {@code SchedulingPolicy} to check
     * @return {@code true} if the policy's Ready condition is True
     */
    private boolean isReady(SchedulingPolicy policy) {
        if (policy.getStatus() == null || policy.getStatus().getConditions() == null) {
            return false;
        }
        return policy.getStatus().getConditions().stream()
                .anyMatch(c -> StatusConditionHelper.CONDITION_TYPE_READY.equals(c.getType())
                        && StatusConditionHelper.STATUS_TRUE.equals(c.getStatus()));
    }

    /**
     * Builds the map of {@code path → content} for all files to commit in the PR.
     *
     * <p>For each {@code AssignmentPackage}, reads the {@code clusterType} and
     * {@code deploymentTarget} labels and writes the reconciler, namespace, and
     * (optional) config manifest files under the standard GitOps directory structure.
     * Mirrors Go's {@code getTree} function in {@code githubrepo.go}.
     *
     * <p>Package-private for unit testing.
     *
     * @param basePath the path prefix from {@code GitOpsRepo.spec.path}
     * @param packages the list of {@code AssignmentPackage} resources to aggregate
     * @return path-to-content map ready to pass to {@link GitHubService#createPullRequest}
     */
    Map<String, String> buildFileContents(String basePath, List<AssignmentPackage> packages) {
        Map<String, String> files = new HashMap<>();

        for (AssignmentPackage pkg : packages) {
            Map<String, String> labels = pkg.getMetadata().getLabels();
            if (labels == null) continue;

            String clusterType = labels.get(AssignmentPackageSpec.CLUSTER_TYPE_LABEL);
            String deploymentTarget = labels.get(AssignmentPackageSpec.DEPLOYMENT_TARGET_LABEL);
            if (clusterType == null || deploymentTarget == null) continue;

            String ext = ext(pkg.getSpec().getReconcilerManifestsContentType());

            // Reconciler manifest (first one — Go operator writes one file per role)
            List<String> reconcilerMfsts = pkg.getSpec().getReconcilerManifests();
            if (reconcilerMfsts != null && !reconcilerMfsts.isEmpty()) {
                String path = GitHubService.buildFilePath(basePath, clusterType,
                        deploymentTarget, GitHubService.RECONCILER_NAME + ext);
                files.put(path, reconcilerMfsts.get(0));
            }

            // Namespace manifest
            List<String> namespaceMfsts = pkg.getSpec().getNamespaceManifests();
            String nsExt = ext(pkg.getSpec().getNamespaceManifestsContentType());
            if (namespaceMfsts != null && !namespaceMfsts.isEmpty()) {
                String path = GitHubService.buildFilePath(basePath, clusterType,
                        deploymentTarget, GitHubService.NAMESPACE_NAME + nsExt);
                files.put(path, namespaceMfsts.get(0));
            }

            // Config manifest (optional)
            List<String> configMfsts = pkg.getSpec().getConfigManifests();
            String cfgExt = ext(pkg.getSpec().getConfigManifestsContentType());
            if (configMfsts != null && !configMfsts.isEmpty()) {
                String path = GitHubService.buildFilePath(basePath, clusterType,
                        deploymentTarget, GitHubService.CONFIG_NAME + cfgExt);
                files.put(path, configMfsts.get(0));
            }
        }

        return files;
    }

    /**
     * Extracts the {@code "org/repo"} full-name from a GitHub HTTPS URL.
     *
     * <p>Converts {@code "https://github.com/org/repo"} to {@code "org/repo"} as
     * required by {@link GitHubService#createPullRequest}. Mirrors Go's use of
     * {@code strings.TrimPrefix} on the URL.
     *
     * <p>Package-private for unit testing.
     *
     * @param repoUrl the full HTTPS URL from {@code GitOpsRepoSpec.repo}
     * @return the {@code "org/repo"} full name
     * @throws IllegalArgumentException if the URL does not contain the expected format
     */
    static String extractRepoFullName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("GitOpsRepo.spec.repo must not be empty");
        }
        // Remove https://github.com/ prefix (or any https://<host>/ prefix)
        String stripped = repoUrl;
        if (stripped.startsWith("https://")) {
            int slashAfterHost = stripped.indexOf('/', "https://".length());
            if (slashAfterHost >= 0) {
                stripped = stripped.substring(slashAfterHost + 1);
            }
        }
        // Remove trailing .git if present
        if (stripped.endsWith(".git")) {
            stripped = stripped.substring(0, stripped.length() - 4);
        }
        return stripped;
    }

    /**
     * Returns the file extension string for the given content type.
     *
     * @param contentType the content type ({@link ContentType#YAML} → {@code ".yaml"},
     *                    {@link ContentType#SH} → {@code ".sh"})
     * @return the extension string including the leading dot
     */
    private static String ext(ContentType contentType) {
        return ContentType.SH.equals(contentType) ? SH_EXT : YAML_EXT;
    }
}
