package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.Environment;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciler for {@code Environment} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code EnvironmentReconciler} in the Go operator's
 * {@code controllers/environment_controller.go}.
 *
 * <p><strong>Reconcile path</strong>:
 * <ol>
 *   <li>Creates (or verifies existence of) a Kubernetes {@code Namespace} named after
 *       the environment (e.g. {@code "dev"}, {@code "prod"}). This is where the
 *       Kustomization's manifests will be applied.</li>
 *   <li>Creates or updates a Flux {@code GitRepository} + {@code Kustomization} pair in
 *       {@value FluxService#DEFAULT_FLUX_NAMESPACE}, targeting the environment namespace
 *       as {@code targetNamespace}.</li>
 * </ol>
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): JOSDK automatically manages the
 * finalizer. On deletion, {@link #cleanup} removes the Flux resources and the environment
 * namespace before JOSDK clears the finalizer.
 *
 * <p><strong>Flux resource naming</strong> (matches Go):
 * {@code name = fmt.Sprintf("%s-%s", req.Namespace, req.Name)}
 *
 * <p><strong>Target namespace</strong>: The environment's own {@code metadata.name}
 * (e.g. {@code "prod"}), not its {@code metadata.namespace}. This mirrors the Go operator
 * where {@code targetNamespace = environment.Name}.
 */
@ControllerConfiguration
public class EnvironmentReconciler implements Reconciler<Environment>, Cleaner<Environment> {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentReconciler.class);

    private final KubernetesClient kubernetesClient;
    private final FluxService fluxService;

    /**
     * Constructs the reconciler.
     *
     * @param kubernetesClient fabric8 client used for namespace operations
     * @param fluxService      service for creating and deleting Flux resources
     */
    public EnvironmentReconciler(KubernetesClient kubernetesClient, FluxService fluxService) {
        this.kubernetesClient = kubernetesClient;
        this.fluxService = fluxService;
    }

    /**
     * Reconciles an {@code Environment} resource.
     *
     * <p>Creates a Kubernetes namespace and Flux resources as described in the class
     * documentation. Sets {@code status.conditions[Ready]=True} on success,
     * {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist the updated conditions
     */
    @Override
    public UpdateControl<Environment> reconcile(Environment resource, Context<Environment> context) {
        String environmentName = resource.getMetadata().getName();
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(), environmentName);
        logger.info("Reconciling Environment: name={}, fluxName={}", environmentName, fluxName);

        try {
            createNamespace(environmentName);

            var controlPlane = resource.getSpec().getControlPlane();
            fluxService.createFluxReferenceResources(
                    fluxName,
                    FluxService.DEFAULT_FLUX_NAMESPACE,
                    environmentName,
                    controlPlane.getRepo(),
                    controlPlane.getBranch(),
                    controlPlane.getPath(),
                    null);

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "EnvironmentReady",
                    "Namespace and Flux resources created successfully");
            logger.info("Environment reconciled successfully: {}", environmentName);

        } catch (Exception e) {
            logger.error("Failed to reconcile Environment: {}", environmentName, e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "ReconcileError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Removes Flux resources and the environment namespace when an {@code Environment} is deleted.
     *
     * <p>Matches the Go operator's finalizer cleanup block in {@code environment_controller.go}.
     * Deletion errors are logged but do not block finalizer removal.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(Environment resource, Context<Environment> context) {
        String environmentName = resource.getMetadata().getName();
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(), environmentName);
        logger.info("Cleaning up Environment: {}", environmentName);

        try {
            fluxService.deleteFluxReferenceResources(fluxName, FluxService.DEFAULT_FLUX_NAMESPACE);
            logger.info("Flux resources deleted for Environment: {}", environmentName);
        } catch (Exception e) {
            logger.error("Failed to delete Flux resources for Environment: {}", environmentName, e);
        }

        try {
            kubernetesClient.namespaces().withName(environmentName).delete();
            logger.info("Namespace deleted for Environment: {}", environmentName);
        } catch (Exception e) {
            logger.error("Failed to delete namespace for Environment: {}", environmentName, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Creates (or verifies existence of) a Kubernetes namespace with the given name
     * via server-side apply. The call is idempotent.
     *
     * <p>Mirrors the Go operator's namespace creation step in
     * {@code environment_controller.go}.
     *
     * <p>Package-private for unit testing via subclass override.
     *
     * @param namespaceName the name of the Kubernetes namespace to create
     */
    void createNamespace(String namespaceName) {
        Namespace namespace = buildNamespace(namespaceName);
        kubernetesClient.namespaces().resource(namespace).serverSideApply();
        logger.debug("Applied namespace: {}", namespaceName);
    }

    /**
     * Builds a {@code Namespace} resource object for the given name.
     *
     * <p>Package-private for unit testing without a Kubernetes API server.
     *
     * @param namespaceName the name of the namespace
     * @return a {@code Namespace} ready for server-side apply
     */
    Namespace buildNamespace(String namespaceName) {
        return new NamespaceBuilder()
                .withNewMetadata()
                    .withName(namespaceName)
                .endMetadata()
                .build();
    }

    /**
     * Builds the Flux resource name from the {@code Environment}'s namespace and name.
     *
     * <p>Mirrors Go's {@code name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)}.
     * Package-private for unit testing.
     *
     * @param namespace Kubernetes namespace of the {@code Environment} resource
     * @param name      Kubernetes name of the {@code Environment} resource
     * @return the Flux resource name (e.g. {@code "kalypso-java-prod"})
     */
    static String buildFluxResourceName(String namespace, String name) {
        return namespace + "-" + name;
    }
}
