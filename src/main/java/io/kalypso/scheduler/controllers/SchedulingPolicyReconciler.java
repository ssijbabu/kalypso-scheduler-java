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
import io.kalypso.scheduler.api.v1alpha1.Assignment;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.SchedulingPolicy;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.Selector;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciler for {@code SchedulingPolicy} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code SchedulingPolicyReconciler} in the Go operator's
 * {@code controllers/schedulingpolicy_controller.go}.
 *
 * <p><strong>Reconcile path</strong>:
 * <ol>
 *   <li>Lists all {@code DeploymentTarget} and {@code ClusterType} resources in the namespace.</li>
 *   <li>Filters each list using the policy's {@code deploymentTargetSelector} and
 *       {@code clusterTypeSelector} respectively.</li>
 *   <li>Creates an {@code Assignment} for every matching (DeploymentTarget, ClusterType) pair
 *       via server-side apply.</li>
 *   <li>Deletes any previously created {@code Assignment} resources that are no longer
 *       in the computed set (identified by the {@value #SCHEDULING_POLICY_LABEL} label).</li>
 * </ol>
 *
 * <p><strong>Selector semantics</strong>: A {@code null} or empty selector matches all resources.
 * When both {@link Selector#getWorkspace()} and {@link Selector#getLabelSelector()} are set,
 * both conditions must hold (AND logic). Workspace is matched against the resource's
 * {@link DeploymentTargetSpec#WORKSPACE_LABEL} metadata label.
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): JOSDK automatically adds a
 * finalizer to every {@code SchedulingPolicy} before the first reconcile. On deletion,
 * {@link #cleanup} deletes all owned {@code Assignment} resources before JOSDK removes
 * the finalizer.
 *
 * <p><strong>Assignment naming</strong> (matches Go):
 * {@code name = "{policy.name}-{dt.name}-{ct.name}"}
 */
@ControllerConfiguration
public class SchedulingPolicyReconciler
        implements Reconciler<SchedulingPolicy>, Cleaner<SchedulingPolicy> {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingPolicyReconciler.class);

    /**
     * Label key applied to every {@code Assignment} created by a given policy.
     * Used to list and garbage-collect stale assignments when the policy changes.
     */
    static final String SCHEDULING_POLICY_LABEL = "scheduler.kalypso.io/schedulingPolicy";

    /** API version used in owner references pointing to the parent {@code SchedulingPolicy}. */
    private static final String POLICY_API_VERSION = "scheduler.kalypso.io/v1alpha1";

    /** Kind name used in owner references pointing to the parent {@code SchedulingPolicy}. */
    private static final String POLICY_KIND = "SchedulingPolicy";

    private final KubernetesClient kubernetesClient;

    /**
     * Constructs the reconciler.
     *
     * @param kubernetesClient fabric8 client used for listing and mutating CRD resources
     */
    public SchedulingPolicyReconciler(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Reconciles a {@code SchedulingPolicy} resource.
     *
     * <p>Computes the desired set of {@code Assignment} resources, applies them, and
     * garbage-collects any stale ones. Sets {@code status.conditions[Ready]=True} on
     * success, {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist the updated conditions
     */
    @Override
    public UpdateControl<SchedulingPolicy> reconcile(
            SchedulingPolicy resource, Context<SchedulingPolicy> context) {
        String policyName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Reconciling SchedulingPolicy: name={}, namespace={}", policyName, namespace);

        try {
            // Step 1: list all DTs and CTs in the namespace
            List<DeploymentTarget> allDts = kubernetesClient
                    .resources(DeploymentTarget.class)
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            List<ClusterType> allCts = kubernetesClient
                    .resources(ClusterType.class)
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            // Step 2: filter by selectors
            Selector dtSelector = resource.getSpec().getDeploymentTargetSelector();
            Selector ctSelector = resource.getSpec().getClusterTypeSelector();

            List<DeploymentTarget> matchedDts = allDts.stream()
                    .filter(dt -> matchesSelector(dtSelector, dt.getMetadata().getLabels()))
                    .collect(Collectors.toList());

            List<ClusterType> matchedCts = allCts.stream()
                    .filter(ct -> matchesSelector(ctSelector, ct.getMetadata().getLabels()))
                    .collect(Collectors.toList());

            // Step 3: compute desired assignment names
            Set<String> desiredNames = computeDesiredAssignmentNames(resource, matchedDts, matchedCts);

            // Step 4: apply desired Assignments
            for (DeploymentTarget dt : matchedDts) {
                for (ClusterType ct : matchedCts) {
                    String assignmentName = policyName + "-" + dt.getMetadata().getName()
                            + "-" + ct.getMetadata().getName();
                    Assignment assignment = buildAssignment(
                            assignmentName, namespace,
                            ct.getMetadata().getName(),
                            dt.getMetadata().getName(),
                            resource);
                    kubernetesClient
                            .resources(Assignment.class)
                            .inNamespace(namespace)
                            .resource(assignment)
                            .serverSideApply();
                    logger.debug("Applied Assignment: {}", assignmentName);
                }
            }

            // Step 5: delete stale Assignments no longer in the desired set
            List<Assignment> existingAssignments = kubernetesClient
                    .resources(Assignment.class)
                    .inNamespace(namespace)
                    .withLabel(SCHEDULING_POLICY_LABEL, policyName)
                    .list()
                    .getItems();

            for (Assignment existing : existingAssignments) {
                String existingName = existing.getMetadata().getName();
                if (!desiredNames.contains(existingName)) {
                    kubernetesClient
                            .resources(Assignment.class)
                            .inNamespace(namespace)
                            .withName(existingName)
                            .delete();
                    logger.debug("Deleted stale Assignment: {}", existingName);
                }
            }

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "AssignmentsReconciled",
                    "All Assignment resources are in sync");
            logger.info("SchedulingPolicy reconciled successfully: {} ({} assignments)",
                    policyName, desiredNames.size());

        } catch (Exception e) {
            logger.error("Failed to reconcile SchedulingPolicy: {}", policyName, e);
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "ReconcileError",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Deletes all owned {@code Assignment} resources when a {@code SchedulingPolicy} is deleted.
     *
     * <p>Lists {@code Assignment} resources labelled with
     * {@value #SCHEDULING_POLICY_LABEL}={@code policy.name} and deletes each one before
     * JOSDK removes the finalizer. Deletion errors are logged but do not block finalizer
     * removal to avoid leaving the resource stuck in terminating state.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(SchedulingPolicy resource, Context<SchedulingPolicy> context) {
        String policyName = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Cleaning up SchedulingPolicy: name={}, namespace={}", policyName, namespace);

        try {
            List<Assignment> owned = kubernetesClient
                    .resources(Assignment.class)
                    .inNamespace(namespace)
                    .withLabel(SCHEDULING_POLICY_LABEL, policyName)
                    .list()
                    .getItems();

            for (Assignment assignment : owned) {
                kubernetesClient
                        .resources(Assignment.class)
                        .inNamespace(namespace)
                        .withName(assignment.getMetadata().getName())
                        .delete();
                logger.debug("Deleted Assignment: {}", assignment.getMetadata().getName());
            }
            logger.info("Deleted {} Assignment(s) for SchedulingPolicy: {}",
                    owned.size(), policyName);
        } catch (Exception e) {
            logger.error("Failed to delete Assignments for SchedulingPolicy: {}", policyName, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Computes the set of desired {@code Assignment} names for the given policy and
     * filtered resource lists.
     *
     * <p>The name format is {@code "{policy.name}-{dt.name}-{ct.name}"}, matching the
     * Go operator's naming convention in {@code schedulingpolicy_controller.go}.
     * Package-private for unit testing.
     *
     * @param policy      the {@code SchedulingPolicy} being reconciled
     * @param dts         filtered list of matching {@code DeploymentTarget} resources
     * @param cts         filtered list of matching {@code ClusterType} resources
     * @return the full set of desired assignment names
     */
    Set<String> computeDesiredAssignmentNames(SchedulingPolicy policy,
            List<DeploymentTarget> dts, List<ClusterType> cts) {
        String policyName = policy.getMetadata().getName();
        return dts.stream()
                .flatMap(dt -> cts.stream()
                        .map(ct -> policyName + "-" + dt.getMetadata().getName()
                                + "-" + ct.getMetadata().getName()))
                .collect(Collectors.toSet());
    }

    /**
     * Tests whether a resource's label map satisfies the given {@link Selector}.
     *
     * <p>Selector matching is an AND of all enabled conditions:
     * <ul>
     *   <li>If {@link Selector#getWorkspace()} is non-null, the resource must carry
     *       {@link DeploymentTargetSpec#WORKSPACE_LABEL} with that exact value.</li>
     *   <li>If {@link Selector#getLabelSelector()} is non-null and non-empty, the resource
     *       must carry every key/value pair from the map (matchLabels semantics).</li>
     * </ul>
     * A {@code null} selector — or a selector with both fields null — matches all resources.
     *
     * <p>Package-private for unit testing.
     *
     * @param selector       the selector to evaluate; {@code null} matches everything
     * @param resourceLabels the metadata labels of the candidate resource; may be {@code null}
     * @return {@code true} if the resource satisfies the selector, {@code false} otherwise
     */
    boolean matchesSelector(Selector selector, Map<String, String> resourceLabels) {
        if (selector == null) {
            return true;
        }

        Map<String, String> labels = resourceLabels != null ? resourceLabels : Map.of();

        // Check workspace constraint
        if (selector.getWorkspace() != null && !selector.getWorkspace().isEmpty()) {
            String actualWorkspace = labels.get(DeploymentTargetSpec.WORKSPACE_LABEL);
            if (!selector.getWorkspace().equals(actualWorkspace)) {
                return false;
            }
        }

        // Check all labelSelector key/value pairs (matchLabels semantics)
        if (selector.getLabelSelector() != null && !selector.getLabelSelector().isEmpty()) {
            for (Map.Entry<String, String> required : selector.getLabelSelector().entrySet()) {
                if (!required.getValue().equals(labels.get(required.getKey()))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Builds an {@code Assignment} resource object binding a {@code ClusterType} to a
     * {@code DeploymentTarget} under the given {@code SchedulingPolicy}.
     *
     * <p>The assignment carries the {@value #SCHEDULING_POLICY_LABEL} label and an
     * owner reference pointing at the policy, enabling label-based listing for
     * garbage collection and Kubernetes garbage collection respectively.
     *
     * <p>Package-private for unit testing without a Kubernetes API server.
     *
     * @param name             name of the {@code Assignment} resource
     * @param namespace        namespace in which to create the resource
     * @param clusterType      name of the matching {@code ClusterType} resource
     * @param deploymentTarget name of the matching {@code DeploymentTarget} resource
     * @param owner            the owning {@code SchedulingPolicy} resource
     * @return a fully populated {@code Assignment} ready for server-side apply
     */
    Assignment buildAssignment(String name, String namespace,
            String clusterType, String deploymentTarget, SchedulingPolicy owner) {

        Map<String, String> labels = new HashMap<>();
        labels.put(SCHEDULING_POLICY_LABEL, owner.getMetadata().getName());

        String ownerUid = owner.getMetadata().getUid() != null
                ? owner.getMetadata().getUid() : "";

        OwnerReference ownerRef = new OwnerReferenceBuilder()
                .withApiVersion(POLICY_API_VERSION)
                .withKind(POLICY_KIND)
                .withName(owner.getMetadata().getName())
                .withUid(ownerUid)
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();

        AssignmentSpec spec = new AssignmentSpec();
        spec.setClusterType(clusterType);
        spec.setDeploymentTarget(deploymentTarget);

        Assignment assignment = new Assignment();
        assignment.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .withOwnerReferences(ownerRef)
                .build());
        assignment.setSpec(spec);
        return assignment;
    }
}
