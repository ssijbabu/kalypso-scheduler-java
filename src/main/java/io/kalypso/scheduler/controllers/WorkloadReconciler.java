package io.kalypso.scheduler.controllers;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.Workload;
import io.kalypso.scheduler.api.v1alpha1.WorkloadRegistration;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.WorkloadTarget;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciler for {@code Workload} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code WorkloadReconciler} in the Go operator's
 * {@code controllers/workload_controller.go}.
 *
 * <p><strong>Reconcile path</strong>: Compares the set of {@code DeploymentTarget} child
 * resources that should exist (derived from {@code spec.deploymentTargets}) against what
 * actually exists in the namespace. Missing targets are created via server-side apply;
 * targets no longer in the spec are deleted. Workspace metadata is resolved by looking up
 * a {@code WorkloadRegistration} with the same name in the same namespace.
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): JOSDK automatically adds a
 * finalizer to every {@code Workload} resource before the first reconcile. When the resource
 * is deleted, JOSDK calls {@link #cleanup} instead of {@link #reconcile}, which deletes all
 * owned {@code DeploymentTarget} resources before JOSDK clears the finalizer.
 *
 * <p><strong>DeploymentTarget naming</strong> (matches Go):
 * {@code name = fmt.Sprintf("%s-%s-%s", namespace, workloadName, targetName)}
 */
@ControllerConfiguration
public class WorkloadReconciler implements Reconciler<Workload>, Cleaner<Workload> {

    private static final Logger logger = LoggerFactory.getLogger(WorkloadReconciler.class);

    /** API version used in owner references pointing to the parent {@code Workload}. */
    private static final String WORKLOAD_API_VERSION = "scheduler.kalypso.io/v1alpha1";

    /** Kind name used in owner references pointing to the parent {@code Workload}. */
    private static final String WORKLOAD_KIND = "Workload";

    private final KubernetesClient kubernetesClient;

    /**
     * Constructs the reconciler.
     *
     * @param kubernetesClient fabric8 client used for {@code DeploymentTarget} and
     *                         {@code WorkloadRegistration} operations
     */
    public WorkloadReconciler(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Reconciles a {@code Workload} resource.
     *
     * <p>Delegates to {@link #reconcileDeploymentTargets} to create, update, or delete
     * child {@code DeploymentTarget} resources as needed. Sets
     * {@code status.conditions[Ready]=True} on success, {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist the updated conditions
     */
    @Override
    public UpdateControl<Workload> reconcile(Workload resource, Context<Workload> context) {
        logger.info("Reconciling Workload: name={}, namespace={}",
                resource.getMetadata().getName(), resource.getMetadata().getNamespace());

        try {
            reconcileDeploymentTargets(resource);

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "DeploymentTargetsReconciled",
                    "All DeploymentTarget resources are in sync");
            logger.info("Workload reconciled successfully: {}", resource.getMetadata().getName());

        } catch (Exception e) {
            logger.error("Failed to reconcile Workload: {}", resource.getMetadata().getName(), e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "ReconcileError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Deletes all owned {@code DeploymentTarget} resources when a {@code Workload} is deleted.
     *
     * <p>Lists {@code DeploymentTarget} resources labelled with
     * {@link DeploymentTargetSpec#WORKLOAD_LABEL}={@code workload.name} and deletes each one
     * before JOSDK removes the finalizer. Deletion errors are logged but do not block
     * finalizer removal to avoid leaving the resource stuck in terminating state.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(Workload resource, Context<Workload> context) {
        String workloadName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Cleaning up Workload: name={}, namespace={}", workloadName, namespace);

        try {
            List<DeploymentTarget> owned = kubernetesClient
                    .resources(DeploymentTarget.class)
                    .inNamespace(namespace)
                    .withLabel(DeploymentTargetSpec.WORKLOAD_LABEL, workloadName)
                    .list()
                    .getItems();

            for (DeploymentTarget dt : owned) {
                kubernetesClient
                        .resources(DeploymentTarget.class)
                        .inNamespace(namespace)
                        .withName(dt.getMetadata().getName())
                        .delete();
                logger.debug("Deleted DeploymentTarget: {}", dt.getMetadata().getName());
            }
            logger.info("Deleted {} DeploymentTarget(s) for Workload: {}", owned.size(), workloadName);
        } catch (Exception e) {
            logger.error("Failed to delete DeploymentTargets for Workload: {}", workloadName, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Reconciles the set of {@code DeploymentTarget} child resources against
     * {@code spec.deploymentTargets}.
     *
     * <p>For each entry in the spec, a {@code DeploymentTarget} is created or updated
     * via server-side apply. Existing {@code DeploymentTarget} resources owned by this
     * workload but absent from the spec are deleted. The workspace label value is resolved
     * by looking up the {@code WorkloadRegistration} with the same name.
     *
     * <p>Package-private for unit testing via subclass override.
     *
     * @param resource the {@code Workload} being reconciled
     * @throws RuntimeException if any Kubernetes API call fails
     */
    void reconcileDeploymentTargets(Workload resource) {
        String workloadName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        // Go operator: name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)
        String workloadNamespacePrefix = namespace + "-" + workloadName;

        // Resolve workspace by looking up the WorkloadRegistration with the same name
        String workspace = resolveWorkspace(workloadName, namespace);

        List<WorkloadTarget> desiredTargets = resource.getSpec().getDeploymentTargets();

        // Apply desired DeploymentTargets
        Set<String> desiredNames = desiredTargets.stream()
                .map(t -> buildDeploymentTargetName(workloadNamespacePrefix, t.getName()))
                .collect(Collectors.toSet());

        // Capture the Workload's UID for owner references on child resources
        String workloadUid = resource.getMetadata().getUid() != null
                ? resource.getMetadata().getUid() : "";

        for (WorkloadTarget target : desiredTargets) {
            String dtName = buildDeploymentTargetName(workloadNamespacePrefix, target.getName());
            DeploymentTarget dt = buildDeploymentTarget(
                    dtName, namespace, workloadName, workloadUid, workspace, target);
            kubernetesClient
                    .resources(DeploymentTarget.class)
                    .inNamespace(namespace)
                    .resource(dt)
                    .serverSideApply();
            logger.debug("Applied DeploymentTarget: {}", dtName);
        }

        // Delete stale DeploymentTargets owned by this Workload but absent from spec
        List<DeploymentTarget> existing = kubernetesClient
                .resources(DeploymentTarget.class)
                .inNamespace(namespace)
                .withLabel(DeploymentTargetSpec.WORKLOAD_LABEL, workloadName)
                .list()
                .getItems();

        for (DeploymentTarget existing_dt : existing) {
            String existingName = existing_dt.getMetadata().getName();
            if (!desiredNames.contains(existingName)) {
                kubernetesClient
                        .resources(DeploymentTarget.class)
                        .inNamespace(namespace)
                        .withName(existingName)
                        .delete();
                logger.debug("Deleted stale DeploymentTarget: {}", existingName);
            }
        }

        logger.info("Reconciled {} DeploymentTarget(s) for Workload: {}",
                desiredTargets.size(), workloadName);
    }

    /**
     * Builds the name of a {@code DeploymentTarget} child resource.
     *
     * <p>Mirrors Go's {@code dtName := fmt.Sprintf("%s-%s", name, target.Name)}.
     * Package-private for unit testing.
     *
     * @param workloadNamespacePrefix the combined {@code "{namespace}-{workloadName}"} prefix
     * @param targetName              the logical target name from {@code spec.deploymentTargets}
     * @return the full {@code DeploymentTarget} resource name
     */
    static String buildDeploymentTargetName(String workloadNamespacePrefix, String targetName) {
        return workloadNamespacePrefix + "-" + targetName;
    }

    /**
     * Builds a {@code DeploymentTarget} resource object for the given parameters.
     *
     * <p>Sets the required metadata labels ({@link DeploymentTargetSpec#WORKSPACE_LABEL},
     * {@link DeploymentTargetSpec#WORKLOAD_LABEL}) and an owner reference pointing to the
     * parent {@code Workload}. The spec carries the target's logical name, manifests
     * repository reference, and any labels defined on the target. The {@code environment}
     * field is left empty — it is set later by the {@code DeploymentTargetReconciler} or
     * environment-level logic, not by the workload.
     *
     * <p>Package-private for unit testing without a Kubernetes API server.
     *
     * @param dtName       name of the {@code DeploymentTarget} resource
     * @param namespace    namespace in which to create the resource
     * @param workloadName name of the parent {@code Workload} resource
     * @param workloadUid  UID of the parent {@code Workload} resource for the owner reference
     * @param workspace    workspace label value resolved from the {@code WorkloadRegistration}
     * @param target       source {@code WorkloadTarget} entry from the workload spec
     * @return a fully populated {@code DeploymentTarget} ready for server-side apply
     */
    DeploymentTarget buildDeploymentTarget(String dtName, String namespace,
            String workloadName, String workloadUid, String workspace, WorkloadTarget target) {

        Map<String, String> labels = new HashMap<>();
        labels.put(DeploymentTargetSpec.WORKSPACE_LABEL, workspace);
        labels.put(DeploymentTargetSpec.WORKLOAD_LABEL, workloadName);

        OwnerReference ownerRef = new OwnerReferenceBuilder()
                .withApiVersion(WORKLOAD_API_VERSION)
                .withKind(WORKLOAD_KIND)
                .withName(workloadName)
                .withUid(workloadUid)
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();

        DeploymentTargetSpec spec = new DeploymentTargetSpec();
        spec.setName(target.getName());
        spec.setManifests(target.getManifests());
        spec.setEnvironment("");

        DeploymentTarget dt = new DeploymentTarget();
        dt.setMetadata(new ObjectMetaBuilder()
                .withName(dtName)
                .withNamespace(namespace)
                .withLabels(labels)
                .withOwnerReferences(ownerRef)
                .build());
        dt.setSpec(spec);
        return dt;
    }

    /**
     * Looks up the workspace name from the {@code WorkloadRegistration} with the same
     * name as the workload in the same namespace.
     *
     * <p>The Go operator reads the workspace from the WorkloadRegistration associated with
     * the workload to populate the {@link DeploymentTargetSpec#WORKSPACE_LABEL} on each
     * child {@code DeploymentTarget}. If no WorkloadRegistration is found or the workspace
     * field is empty, an empty string is returned.
     *
     * @param workloadName name of the workload (and the WorkloadRegistration to look up)
     * @param namespace    namespace to search in
     * @return the workspace string, or {@code ""} if not found
     */
    private String resolveWorkspace(String workloadName, String namespace) {
        try {
            WorkloadRegistration reg = kubernetesClient
                    .resources(WorkloadRegistration.class)
                    .inNamespace(namespace)
                    .withName(workloadName)
                    .get();
            if (reg != null && reg.getSpec() != null
                    && reg.getSpec().getWorkspace() != null) {
                return reg.getSpec().getWorkspace();
            }
        } catch (Exception e) {
            logger.warn("Could not resolve workspace for Workload '{}' from WorkloadRegistration: {}",
                    workloadName, e.getMessage());
        }
        return "";
    }
}
