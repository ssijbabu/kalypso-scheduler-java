package io.kalypso.scheduler.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.kalypso.scheduler.flux.model.CrossNamespaceSourceReference;
import io.kalypso.scheduler.flux.model.GitRepositoryRef;
import io.kalypso.scheduler.flux.model.GitRepositorySpec;
import io.kalypso.scheduler.flux.model.KustomizationSpec;
import io.kalypso.scheduler.flux.model.LocalObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Manages Flux {@code GitRepository} and {@code Kustomization} resource pairs in the
 * {@code flux-system} namespace, mirroring the {@code flux.go} component of the Go operator.
 *
 * <p>Each Kalypso reconciler that needs to pull manifests from a Git repository delegates
 * to this service to create (or delete) the two Flux resources that the Flux source- and
 * kustomize-controllers use to keep the cluster in sync with that repository.
 *
 * <p><strong>Namespace convention</strong> (matches Go operator):
 * <ul>
 *   <li>Both Flux resources always live in {@value #DEFAULT_FLUX_NAMESPACE} —
 *       the namespace where the Flux controllers run.</li>
 *   <li>{@code targetNamespace} is the Kubernetes namespace into which the
 *       Kustomization's manifests are applied, typically the CRD's own namespace.</li>
 * </ul>
 *
 * <p><strong>Authentication</strong>: A Kubernetes {@code Secret} named
 * {@link #REPO_SECRET_NAME} must exist in {@value #DEFAULT_FLUX_NAMESPACE} and hold the
 * Git credentials (SSH private key or HTTPS token). This mirrors the Go operator constant
 * {@code RepoSecretName = "gh-repo-secret"}.
 *
 * <p>Resources are created via server-side apply (idempotent), unlike the Go operator which
 * uses a Get-then-Create-or-Update pattern. The outcome is equivalent.
 *
 * <p>Example usage in a reconciler:
 * <pre>
 *     fluxService.createFluxReferenceResources(
 *         namespace + "-" + name,   // Flux resource name  (Go: fmt.Sprintf("%s-%s", ns, name))
 *         FluxService.DEFAULT_FLUX_NAMESPACE,
 *         resource.getMetadata().getNamespace(),  // targetNamespace
 *         spec.getRepo(),
 *         spec.getBranch(),
 *         spec.getPath(),
 *         spec.getCommit());        // null or "" to track branch head
 * </pre>
 */
public class FluxService {

    private static final Logger logger = LoggerFactory.getLogger(FluxService.class);

    /**
     * Namespace where all Flux resources are created.
     * Corresponds to {@code DefaulFluxNamespace = "flux-system"} in the Go operator.
     */
    public static final String DEFAULT_FLUX_NAMESPACE = "flux-system";

    /**
     * Name of the Kubernetes Secret in {@value #DEFAULT_FLUX_NAMESPACE} that holds
     * Git credentials. Corresponds to {@code RepoSecretName = "gh-repo-secret"} in
     * the Go operator. Override via {@code flux.secret-name} in {@code application.properties}.
     */
    public static final String REPO_SECRET_NAME = "gh-repo-secret";

    /** Reconciliation interval for both GitRepository and Kustomization resources. */
    static final String DEFAULT_INTERVAL = "10s";

    /** API group for Flux source resources (GitRepository). */
    static final String SOURCE_API_GROUP = "source.toolkit.fluxcd.io";

    /** API group for Flux kustomize resources (Kustomization). */
    static final String KUSTOMIZE_API_GROUP = "kustomize.toolkit.fluxcd.io";

    /** Flux API version for both resource types (Flux 2.0+ stable). */
    static final String FLUX_API_VERSION = "v1";

    private static final ResourceDefinitionContext GIT_REPO_CONTEXT =
            new ResourceDefinitionContext.Builder()
                    .withGroup(SOURCE_API_GROUP)
                    .withVersion(FLUX_API_VERSION)
                    .withKind("GitRepository")
                    .withNamespaced(true)
                    .build();

    private static final ResourceDefinitionContext KUSTOMIZATION_CONTEXT =
            new ResourceDefinitionContext.Builder()
                    .withGroup(KUSTOMIZE_API_GROUP)
                    .withVersion(FLUX_API_VERSION)
                    .withKind("Kustomization")
                    .withNamespaced(true)
                    .build();

    private final KubernetesClient kubernetesClient;
    private final ObjectMapper objectMapper;
    private final String secretName;

    /**
     * Constructs a {@code FluxService} using the default Git secret name
     * ({@value #REPO_SECRET_NAME}).
     *
     * @param kubernetesClient fabric8 client used to interact with the Flux CRDs
     */
    public FluxService(KubernetesClient kubernetesClient) {
        this(kubernetesClient, REPO_SECRET_NAME);
    }

    /**
     * Constructs a {@code FluxService} with a configurable Git secret name.
     * Use this constructor when the secret name is read from {@code application.properties}
     * ({@code flux.secret-name}).
     *
     * @param kubernetesClient fabric8 client used to interact with the Flux CRDs
     * @param secretName       name of the Secret in {@value #DEFAULT_FLUX_NAMESPACE} that
     *                         holds the Git credentials
     */
    public FluxService(KubernetesClient kubernetesClient, String secretName) {
        this.kubernetesClient = kubernetesClient;
        this.secretName = secretName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates or updates a Flux {@code GitRepository} and {@code Kustomization} resource
     * pair in the {@value #DEFAULT_FLUX_NAMESPACE} namespace.
     *
     * <p>Mirrors {@code CreateFluxReferenceResources} in the Go operator's {@code flux.go}.
     * Both resources receive the same {@code name}. The Kustomization's {@code sourceRef}
     * points at the {@code GitRepository}. The {@code secretRef} on the GitRepository points
     * at the configured Git credentials secret.
     *
     * <p>This method is idempotent — calling it multiple times converges to the same state.
     *
     * @param name            name for both the {@code GitRepository} and {@code Kustomization}
     *                        (Go pattern: {@code fmt.Sprintf("%s-%s", namespace, resourceName)})
     * @param namespace       namespace where both Flux resources are created (always
     *                        {@value #DEFAULT_FLUX_NAMESPACE} in practice)
     * @param targetNamespace Kubernetes namespace into which the Kustomization applies
     *                        manifests — typically the CRD's own namespace
     * @param url             Git repository URL (HTTPS or SSH)
     * @param branch          branch to track
     * @param path            path within the repository root where manifests live
     * @param commit          optional commit SHA to pin to; {@code null} or empty tracks
     *                        branch head (Go operator passes {@code ""} when not pinning)
     * @throws RuntimeException if the Kubernetes API call fails
     */
    public void createFluxReferenceResources(String name, String namespace, String targetNamespace,
                                              String url, String branch, String path, String commit) {
        logger.info("Creating Flux reference resources: name={}, namespace={}, targetNamespace={}, url={}, branch={}, path={}",
                name, namespace, targetNamespace, url, branch, path);

        GenericKubernetesResource gitRepo = buildGitRepository(name, namespace, url, branch, commit);
        kubernetesClient.genericKubernetesResources(GIT_REPO_CONTEXT)
                .inNamespace(namespace)
                .resource(gitRepo)
                .serverSideApply();
        logger.debug("Applied Flux GitRepository: {}/{}", namespace, name);

        GenericKubernetesResource kustomization = buildKustomization(name, namespace, targetNamespace, name, path);
        kubernetesClient.genericKubernetesResources(KUSTOMIZATION_CONTEXT)
                .inNamespace(namespace)
                .resource(kustomization)
                .serverSideApply();
        logger.debug("Applied Flux Kustomization: {}/{}", namespace, name);
    }

    /**
     * Deletes the Flux {@code GitRepository} and {@code Kustomization} resources with
     * the given name from the given namespace.
     *
     * <p>Mirrors {@code DeleteFluxReferenceResources} in the Go operator's {@code flux.go}.
     * Unlike the Go operator (which deletes Kustomization before GitRepository), this
     * implementation deletes GitRepository first then Kustomization — both orderings are
     * safe because Flux controllers reconcile independently.
     *
     * <p>If a resource does not exist, the deletion is silently skipped.
     *
     * @param name      name of the resources to delete
     * @param namespace namespace where the resources live (always {@value #DEFAULT_FLUX_NAMESPACE})
     * @throws RuntimeException if the Kubernetes API call fails
     */
    public void deleteFluxReferenceResources(String name, String namespace) {
        logger.info("Deleting Flux reference resources: name={}, namespace={}", name, namespace);

        kubernetesClient.genericKubernetesResources(GIT_REPO_CONTEXT)
                .inNamespace(namespace)
                .withName(name)
                .delete();
        logger.debug("Deleted Flux GitRepository: {}/{}", namespace, name);

        kubernetesClient.genericKubernetesResources(KUSTOMIZATION_CONTEXT)
                .inNamespace(namespace)
                .withName(name)
                .delete();
        logger.debug("Deleted Flux Kustomization: {}/{}", namespace, name);
    }

    /**
     * Builds a {@code GenericKubernetesResource} representing a Flux
     * {@code GitRepository} (source.toolkit.fluxcd.io/v1).
     *
     * <p>Sets {@code spec.secretRef} to the configured Git credentials secret,
     * matching the Go operator's {@code gitRepo.Spec.SecretRef = &meta.LocalObjectReference{Name: RepoSecretName}}.
     * Sets {@code spec.ref.commit} only when {@code commit} is non-empty.
     *
     * <p>Package-private for unit testing.
     *
     * @param name      resource name
     * @param namespace Kubernetes namespace (always {@value #DEFAULT_FLUX_NAMESPACE})
     * @param url       Git repository URL
     * @param branch    branch to track
     * @param commit    optional commit SHA; {@code null} or empty string means track branch head
     * @return the populated resource ready for server-side apply
     */
    GenericKubernetesResource buildGitRepository(String name, String namespace,
                                                  String url, String branch, String commit) {
        GitRepositoryRef ref = new GitRepositoryRef();
        ref.setBranch(branch);
        if (commit != null && !commit.isEmpty()) {
            ref.setCommit(commit);
        }

        LocalObjectReference secretRef = new LocalObjectReference();
        secretRef.setName(secretName);

        GitRepositorySpec spec = new GitRepositorySpec();
        spec.setUrl(url);
        spec.setInterval(DEFAULT_INTERVAL);
        spec.setRef(ref);
        spec.setSecretRef(secretRef);

        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setApiVersion(SOURCE_API_GROUP + "/" + FLUX_API_VERSION);
        resource.setKind("GitRepository");
        resource.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build());
        resource.setAdditionalProperty("spec", toMap(spec));
        return resource;
    }

    /**
     * Builds a {@code GenericKubernetesResource} representing a Flux
     * {@code Kustomization} (kustomize.toolkit.fluxcd.io/v1).
     *
     * <p>Sets {@code prune: true} so Flux removes resources that disappear from the
     * source — matching {@code kustomization.Spec.Prune = true} in the Go operator.
     *
     * <p>Package-private for unit testing.
     *
     * @param name            resource name
     * @param namespace       Kubernetes namespace (always {@value #DEFAULT_FLUX_NAMESPACE})
     * @param targetNamespace namespace into which the manifests are applied
     * @param sourceRefName   name of the {@code GitRepository} source to reference
     * @param path            path within the repository where manifests live
     * @return the populated resource ready for server-side apply
     */
    GenericKubernetesResource buildKustomization(String name, String namespace,
                                                  String targetNamespace,
                                                  String sourceRefName, String path) {
        CrossNamespaceSourceReference sourceRef = new CrossNamespaceSourceReference();
        sourceRef.setKind("GitRepository");
        sourceRef.setName(sourceRefName);

        KustomizationSpec spec = new KustomizationSpec();
        spec.setSourceRef(sourceRef);
        spec.setPath(path);
        spec.setInterval(DEFAULT_INTERVAL);
        spec.setPrune(true);
        spec.setTargetNamespace(targetNamespace);

        GenericKubernetesResource resource = new GenericKubernetesResource();
        resource.setApiVersion(KUSTOMIZE_API_GROUP + "/" + FLUX_API_VERSION);
        resource.setKind("Kustomization");
        resource.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .build());
        resource.setAdditionalProperty("spec", toMap(spec));
        return resource;
    }

    /**
     * Converts a POJO to a {@code Map<String, Object>} via Jackson for use as a
     * {@code GenericKubernetesResource} additional property.
     *
     * @param value the POJO to convert
     * @return a map representation suitable for {@code setAdditionalProperty}
     */
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }
}
