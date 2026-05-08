package io.kalypso.scheduler.controllers;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.BaseRepo;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciler for {@code BaseRepo} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code BaseRepoReconciler} in the Go operator's
 * {@code controllers/baserepo_controller.go}.
 *
 * <p><strong>Reconcile path</strong>: Creates or updates a Flux {@code GitRepository} and
 * {@code Kustomization} resource pair in the {@value FluxService#DEFAULT_FLUX_NAMESPACE}
 * namespace. The Kustomization targets the {@code BaseRepo}'s own namespace so that the
 * Flux source-controller and kustomize-controller keep that namespace in sync with the
 * Git repository.
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): JOSDK automatically adds a
 * finalizer to every {@code BaseRepo} resource before the first reconcile. When the resource
 * is deleted, JOSDK calls {@link #cleanup} instead of {@link #reconcile}, which removes the
 * Flux resources before JOSDK clears the finalizer. This mirrors the Go operator's
 * {@code controllerutil.AddFinalizer} / {@code controllerutil.RemoveFinalizer} pattern.
 *
 * <p><strong>Flux resource naming</strong> (matches Go):
 * {@code name = fmt.Sprintf("%s-%s", req.Namespace, req.Name)}
 */
@ControllerConfiguration
public class BaseRepoReconciler implements Reconciler<BaseRepo>, Cleaner<BaseRepo> {

    private static final Logger logger = LoggerFactory.getLogger(BaseRepoReconciler.class);

    private final FluxService fluxService;

    /**
     * Constructs the reconciler.
     *
     * @param fluxService service for creating and deleting Flux resources
     */
    public BaseRepoReconciler(FluxService fluxService) {
        this.fluxService = fluxService;
    }

    /**
     * Reconciles a {@code BaseRepo} resource.
     *
     * <p>Creates or updates a Flux {@code GitRepository} + {@code Kustomization} pair in
     * {@value FluxService#DEFAULT_FLUX_NAMESPACE} targeting the {@code BaseRepo}'s own
     * namespace. Sets {@code status.conditions[Ready]=True} on success,
     * {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist the updated conditions
     */
    @Override
    public UpdateControl<BaseRepo> reconcile(BaseRepo resource, Context<BaseRepo> context) {
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName());
        logger.info("Reconciling BaseRepo: name={}, fluxName={}", resource.getMetadata().getName(), fluxName);

        try {
            var spec = resource.getSpec();
            fluxService.createFluxReferenceResources(
                    fluxName,
                    FluxService.DEFAULT_FLUX_NAMESPACE,
                    resource.getMetadata().getNamespace(),
                    spec.getRepo(),
                    spec.getBranch(),
                    spec.getPath(),
                    spec.getCommit());

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "FluxResourcesCreated",
                    "Flux GitRepository and Kustomization created successfully");
            logger.info("BaseRepo reconciled successfully: {}", fluxName);

        } catch (Exception e) {
            logger.error("Failed to reconcile BaseRepo: {}", resource.getMetadata().getName(), e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "FluxError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Removes Flux resources when a {@code BaseRepo} is deleted.
     *
     * <p>Deletes the Flux {@code GitRepository} and {@code Kustomization} before JOSDK
     * removes the finalizer, matching the Go operator's finalizer cleanup block in
     * {@code baserepo_controller.go}. Deletion errors are logged but do not block
     * finalizer removal to avoid leaving resources stuck in terminating state.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(BaseRepo resource, Context<BaseRepo> context) {
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName());
        logger.info("Cleaning up BaseRepo: {}", fluxName);

        try {
            fluxService.deleteFluxReferenceResources(fluxName, FluxService.DEFAULT_FLUX_NAMESPACE);
            logger.info("Flux resources deleted for BaseRepo: {}", fluxName);
        } catch (Exception e) {
            logger.error("Failed to delete Flux resources for BaseRepo: {}", fluxName, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Builds the Flux resource name from the {@code BaseRepo}'s namespace and name.
     *
     * <p>Mirrors Go's {@code name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)}.
     * Package-private for unit testing.
     *
     * @param namespace Kubernetes namespace of the {@code BaseRepo} resource
     * @param name      Kubernetes name of the {@code BaseRepo} resource
     * @return the Flux resource name (e.g. {@code "kalypso-java-my-base-repo"})
     */
    static String buildFluxResourceName(String namespace, String name) {
        return namespace + "-" + name;
    }
}
