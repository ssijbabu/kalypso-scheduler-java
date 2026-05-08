package io.kalypso.scheduler.controllers;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistration;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.services.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciler for {@code WorkloadRegistration} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code WorkloadRegistrationReconciler} in the Go operator's
 * {@code controllers/workloadregistration_controller.go}.
 *
 * <p><strong>Reconcile path</strong>: Creates or updates a Flux {@code GitRepository} and
 * {@code Kustomization} resource pair in the {@value FluxService#DEFAULT_FLUX_NAMESPACE}
 * namespace, pointing at the Git repository declared in {@code spec.workload}.
 * The Kustomization targets the {@code WorkloadRegistration}'s own namespace so that
 * the Flux source-controller and kustomize-controller keep that namespace in sync
 * with the workload's manifest repository.
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): JOSDK automatically adds a
 * finalizer to every {@code WorkloadRegistration} resource before the first reconcile.
 * When the resource is deleted, JOSDK calls {@link #cleanup} instead of {@link #reconcile},
 * which removes the Flux resources before JOSDK clears the finalizer. This mirrors the Go
 * operator's {@code controllerutil.AddFinalizer} / {@code controllerutil.RemoveFinalizer}
 * pattern.
 *
 * <p><strong>Flux resource naming</strong> (matches Go):
 * {@code name = fmt.Sprintf("%s-%s", req.Namespace, req.Name)}
 */
@ControllerConfiguration
public class WorkloadRegistrationReconciler
        implements Reconciler<WorkloadRegistration>, Cleaner<WorkloadRegistration> {

    private static final Logger logger =
            LoggerFactory.getLogger(WorkloadRegistrationReconciler.class);

    private final FluxService fluxService;

    /**
     * Constructs the reconciler.
     *
     * @param fluxService service for creating and deleting Flux resources
     */
    public WorkloadRegistrationReconciler(FluxService fluxService) {
        this.fluxService = fluxService;
    }

    /**
     * Reconciles a {@code WorkloadRegistration} resource.
     *
     * <p>Creates or updates a Flux {@code GitRepository} + {@code Kustomization} pair in
     * {@value FluxService#DEFAULT_FLUX_NAMESPACE} targeting the {@code WorkloadRegistration}'s
     * own namespace. Sets {@code status.conditions[Ready]=True} on success,
     * {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist the updated conditions
     */
    @Override
    public UpdateControl<WorkloadRegistration> reconcile(
            WorkloadRegistration resource, Context<WorkloadRegistration> context) {
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName());
        logger.info("Reconciling WorkloadRegistration: name={}, fluxName={}",
                resource.getMetadata().getName(), fluxName);

        try {
            var spec = resource.getSpec();
            var workload = spec.getWorkload();
            fluxService.createFluxReferenceResources(
                    fluxName,
                    FluxService.DEFAULT_FLUX_NAMESPACE,
                    resource.getMetadata().getNamespace(),
                    workload.getRepo(),
                    workload.getBranch(),
                    workload.getPath(),
                    null);

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "FluxResourcesCreated",
                    "Flux GitRepository and Kustomization created successfully");
            logger.info("WorkloadRegistration reconciled successfully: {}", fluxName);

        } catch (Exception e) {
            logger.error("Failed to reconcile WorkloadRegistration: {}",
                    resource.getMetadata().getName(), e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "FluxError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Removes Flux resources when a {@code WorkloadRegistration} is deleted.
     *
     * <p>Deletes the Flux {@code GitRepository} and {@code Kustomization} before JOSDK
     * removes the finalizer, matching the Go operator's finalizer cleanup block in
     * {@code workloadregistration_controller.go}. Deletion errors are logged but do not
     * block finalizer removal to avoid leaving resources stuck in terminating state.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(WorkloadRegistration resource,
            Context<WorkloadRegistration> context) {
        String fluxName = buildFluxResourceName(
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName());
        logger.info("Cleaning up WorkloadRegistration: {}", fluxName);

        try {
            fluxService.deleteFluxReferenceResources(fluxName, FluxService.DEFAULT_FLUX_NAMESPACE);
            logger.info("Flux resources deleted for WorkloadRegistration: {}", fluxName);
        } catch (Exception e) {
            logger.error("Failed to delete Flux resources for WorkloadRegistration: {}", fluxName, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Builds the Flux resource name from the {@code WorkloadRegistration}'s namespace and name.
     *
     * <p>Mirrors Go's {@code name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)}.
     * Package-private for unit testing.
     *
     * @param namespace Kubernetes namespace of the {@code WorkloadRegistration} resource
     * @param name      Kubernetes name of the {@code WorkloadRegistration} resource
     * @return the Flux resource name (e.g. {@code "kalypso-java-my-workload-reg"})
     */
    static String buildFluxResourceName(String namespace, String name) {
        return namespace + "-" + name;
    }
}
