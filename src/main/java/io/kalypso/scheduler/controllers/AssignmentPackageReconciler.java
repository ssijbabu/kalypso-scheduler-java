package io.kalypso.scheduler.controllers;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;

/**
 * Reconciler for {@code AssignmentPackage} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code AssignmentPackageReconciler} in the Go operator's
 * {@code controllers/assignmentpackage_controller.go}.
 *
 * <p><strong>Reconcile path</strong>: validates that the rendered manifest strings
 * inside the package can be parsed as valid YAML (for {@link ContentType#YAML} content)
 * or are non-empty (for {@link ContentType#SH} content). Sets {@code Ready=True} when
 * all manifests pass validation so downstream consumers (the
 * {@code GitOpsRepoReconciler}) can gate on this condition.
 *
 * <p>This reconciler has no side effects beyond writing status — it does not create,
 * modify, or delete any other Kubernetes resources. {@code AssignmentPackage} resources
 * are always created by the {@code AssignmentReconciler}; this reconciler only validates
 * and stamps their status.
 */
@ControllerConfiguration
public class AssignmentPackageReconciler
        implements Reconciler<AssignmentPackage>, Cleaner<AssignmentPackage> {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentPackageReconciler.class);

    /**
     * Constructs the reconciler. No external dependencies are required because this
     * reconciler only performs in-memory validation of the package's manifest strings.
     */
    public AssignmentPackageReconciler() {}

    /**
     * Reconciles an {@code AssignmentPackage} resource.
     *
     * <p>Validates every manifest string in the package. Sets
     * {@code status.conditions[Ready]=True} when all manifests are valid,
     * {@code Ready=False} with a description of the first failure otherwise.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist updated conditions
     */
    @Override
    public UpdateControl<AssignmentPackage> reconcile(
            AssignmentPackage resource, Context<AssignmentPackage> context) {

        String name = resource.getMetadata().getName();
        logger.info("Reconciling AssignmentPackage: name={}", name);

        try {
            validateManifests(resource.getSpec());

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "ManifestsValid",
                    "All manifests are valid");
            logger.info("AssignmentPackage reconciled successfully: {}", name);

        } catch (Exception e) {
            logger.warn("AssignmentPackage validation failed: name={}, error={}", name, e.getMessage());
            StatusConditionHelper.setNotReady(
                    resource.getStatus().getConditions(),
                    "InvalidManifests",
                    e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * No-op cleanup: {@code AssignmentPackage} resources are owned by their parent
     * {@code Assignment} via Kubernetes owner references, so Kubernetes itself handles
     * garbage collection via the garbage collector. The finalizer is removed immediately.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(AssignmentPackage resource, Context<AssignmentPackage> context) {
        logger.info("Cleaning up AssignmentPackage: {}", resource.getMetadata().getName());
        return DeleteControl.defaultDelete();
    }

    /**
     * Validates all manifest groups in the package spec.
     *
     * <p>YAML manifests are parsed with SnakeYAML to catch syntax errors.
     * Shell-script manifests are only checked to be non-empty.
     *
     * <p>Package-private for unit testing.
     *
     * @param spec the package spec to validate
     * @throws IllegalArgumentException if any manifest fails validation
     */
    void validateManifests(AssignmentPackageSpec spec) {
        validateGroup("reconcilerManifests",
                spec.getReconcilerManifests(),
                spec.getReconcilerManifestsContentType());

        validateGroup("namespaceManifests",
                spec.getNamespaceManifests(),
                spec.getNamespaceManifestsContentType());

        validateGroup("configManifests",
                spec.getConfigManifests(),
                spec.getConfigManifestsContentType());
    }

    /**
     * Validates a single manifest group.
     *
     * <p>Package-private for unit testing.
     *
     * @param groupName   descriptive name used in error messages
     * @param manifests   the list of manifest strings to validate
     * @param contentType the declared content type (YAML parses, SH only checks non-empty)
     * @throws IllegalArgumentException if any manifest in the group is invalid
     */
    void validateGroup(String groupName, List<String> manifests, ContentType contentType) {
        if (manifests == null || manifests.isEmpty()) {
            return;
        }

        for (int i = 0; i < manifests.size(); i++) {
            String manifest = manifests.get(i);
            if (manifest == null || manifest.isBlank()) {
                throw new IllegalArgumentException(
                        groupName + "[" + i + "] is empty");
            }

            if (ContentType.YAML.equals(contentType) || contentType == null) {
                try {
                    new Yaml().load(manifest);
                } catch (YAMLException e) {
                    throw new IllegalArgumentException(
                            groupName + "[" + i + "] is not valid YAML: " + e.getMessage());
                }
            }
        }
    }
}
