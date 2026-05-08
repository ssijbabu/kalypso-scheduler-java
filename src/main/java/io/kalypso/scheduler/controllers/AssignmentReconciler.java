package io.kalypso.scheduler.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
import io.kalypso.scheduler.api.v1alpha1.AssignmentPackage;
import io.kalypso.scheduler.api.v1alpha1.ClusterType;
import io.kalypso.scheduler.api.v1alpha1.ConfigSchema;
import io.kalypso.scheduler.api.v1alpha1.DeploymentTarget;
import io.kalypso.scheduler.api.v1alpha1.Template;
import io.kalypso.scheduler.api.v1alpha1.spec.AssignmentPackageSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.ContentType;
import io.kalypso.scheduler.api.v1alpha1.spec.DeploymentTargetSpec;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateManifest;
import io.kalypso.scheduler.api.v1alpha1.spec.TemplateType;
import io.kalypso.scheduler.controllers.shared.StatusConditionHelper;
import io.kalypso.scheduler.exception.ConfigValidationException;
import io.kalypso.scheduler.model.TemplateContext;
import io.kalypso.scheduler.services.ConfigValidationService;
import io.kalypso.scheduler.services.TemplateProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reconciler for {@code Assignment} (scheduler.kalypso.io/v1alpha1) resources.
 *
 * <p>Mirrors {@code AssignmentReconciler} in the Go operator's
 * {@code controllers/assignment_controller.go}.
 *
 * <p><strong>Reconcile path</strong>:
 * <ol>
 *   <li>Fetches the referenced {@code ClusterType} and {@code DeploymentTarget} resources.</li>
 *   <li>Gathers configuration data from {@code ConfigMap} objects in the namespace whose
 *       labels match the cluster type or deployment target.</li>
 *   <li>Validates gathered config against the {@code ConfigSchema} CRD for the cluster type,
 *       if one exists.</li>
 *   <li>Renders the three template roles (reconciler, namespace, config) using
 *       {@link TemplateProcessingService}.</li>
 *   <li>Creates or updates an {@code AssignmentPackage} CRD with the rendered manifests.</li>
 * </ol>
 *
 * <p><strong>Deletion path</strong> (via {@link Cleaner}): deletes the owned
 * {@code AssignmentPackage} before JOSDK removes the finalizer.
 */
@ControllerConfiguration
public class AssignmentReconciler implements Reconciler<Assignment>, Cleaner<Assignment> {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentReconciler.class);

    private static final String ASSIGNMENT_API_VERSION = "scheduler.kalypso.io/v1alpha1";
    private static final String ASSIGNMENT_KIND = "Assignment";

    /**
     * Label applied to {@code AssignmentPackage} to identify its parent {@code Assignment}.
     * Used for look-up during cleanup.
     */
    static final String ASSIGNMENT_LABEL = "scheduler.kalypso.io/assignment";

    private final KubernetesClient kubernetesClient;
    private final TemplateProcessingService templateProcessingService;
    private final ConfigValidationService configValidationService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the reconciler.
     *
     * @param kubernetesClient          fabric8 client for resource lookup and mutation
     * @param templateProcessingService Freemarker template rendering service
     * @param configValidationService   JSON Schema validation service
     */
    public AssignmentReconciler(KubernetesClient kubernetesClient,
                                 TemplateProcessingService templateProcessingService,
                                 ConfigValidationService configValidationService) {
        this.kubernetesClient = kubernetesClient;
        this.templateProcessingService = templateProcessingService;
        this.configValidationService = configValidationService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Reconciles an {@code Assignment} resource.
     *
     * <p>Fetches the referenced resources, validates config, renders templates, and
     * creates or updates the resulting {@code AssignmentPackage}. Sets
     * {@code status.conditions[Ready]=True} on success, {@code Ready=False} on error.
     *
     * @param resource the desired state from the Kubernetes API
     * @param context  JOSDK reconciliation context
     * @return {@code UpdateControl.patchStatus(resource)} to persist updated conditions
     */
    @Override
    public UpdateControl<Assignment> reconcile(Assignment resource, Context<Assignment> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Reconciling Assignment: name={}, namespace={}", name, namespace);

        try {
            String clusterTypeName = resource.getSpec().getClusterType();
            String deploymentTargetName = resource.getSpec().getDeploymentTarget();

            // Step 1: fetch referenced resources
            ClusterType clusterType = kubernetesClient
                    .resources(ClusterType.class).inNamespace(namespace)
                    .withName(clusterTypeName).get();
            if (clusterType == null) {
                throw new IllegalStateException("ClusterType not found: " + clusterTypeName);
            }

            DeploymentTarget deploymentTarget = kubernetesClient
                    .resources(DeploymentTarget.class).inNamespace(namespace)
                    .withName(deploymentTargetName).get();
            if (deploymentTarget == null) {
                throw new IllegalStateException("DeploymentTarget not found: " + deploymentTargetName);
            }

            // Step 2: gather config data from ConfigMaps
            Map<String, Object> configData = gatherConfigData(namespace, clusterTypeName, deploymentTargetName);

            // Step 3: validate config against ConfigSchema if one exists
            validateConfigData(namespace, clusterTypeName, configData);

            // Step 4: build template context
            TemplateContext ctx = buildTemplateContext(clusterType, deploymentTarget, configData);

            // Step 5: render templates and build AssignmentPackage
            AssignmentPackage pkg = buildAssignmentPackage(name, namespace, clusterType, ctx, resource);

            // Step 6: apply AssignmentPackage
            kubernetesClient.resources(AssignmentPackage.class)
                    .inNamespace(namespace).resource(pkg).serverSideApply();
            logger.info("Applied AssignmentPackage: {}", name);

            StatusConditionHelper.setReady(
                    resource.getStatus().getConditions(),
                    "PackageRendered",
                    "AssignmentPackage rendered and applied successfully");

        } catch (ConfigValidationException e) {
            logger.error("Config validation failed for Assignment: {}", name, e);
            StatusConditionHelper.setNotReady(resource.getStatus().getConditions(),
                    "ConfigValidationFailed",
                    "Config validation failed: " + String.join("; ", e.getValidationErrors()));
        } catch (Exception e) {
            logger.error("Failed to reconcile Assignment: {}", name, e);
            StatusConditionHelper.setNotReady(resource.getStatus().getConditions(),
                    "ReconcileError", e.getMessage());
        }

        return UpdateControl.patchStatus(resource);
    }

    /**
     * Deletes the owned {@code AssignmentPackage} when the {@code Assignment} is deleted.
     *
     * @param resource the resource being deleted
     * @param context  JOSDK reconciliation context
     * @return {@link DeleteControl#defaultDelete()} to signal JOSDK to remove the finalizer
     */
    @Override
    public DeleteControl cleanup(Assignment resource, Context<Assignment> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        logger.info("Cleaning up Assignment: {}", name);

        try {
            kubernetesClient.resources(AssignmentPackage.class)
                    .inNamespace(namespace).withLabel(ASSIGNMENT_LABEL, name)
                    .list().getItems()
                    .forEach(pkg -> kubernetesClient.resources(AssignmentPackage.class)
                            .inNamespace(namespace).withName(pkg.getMetadata().getName()).delete());
            logger.info("Deleted AssignmentPackage(s) for Assignment: {}", name);
        } catch (Exception e) {
            logger.error("Failed to delete AssignmentPackage for Assignment: {}", name, e);
        }

        return DeleteControl.defaultDelete();
    }

    /**
     * Builds the {@link TemplateContext} from the resolved resources and config data.
     *
     * <p>Mirrors the Go operator's {@code dataType} population in
     * {@code assignment_controller.go}. Workspace and workload are read from the
     * {@code DeploymentTarget}'s metadata labels (set by the {@code WorkloadReconciler}).
     *
     * <p>Package-private for unit testing.
     *
     * @param clusterType    the resolved {@code ClusterType} resource
     * @param deploymentTarget the resolved {@code DeploymentTarget} resource
     * @param configData     merged configuration data from ConfigMaps
     * @return a fully populated {@link TemplateContext}
     */
    TemplateContext buildTemplateContext(ClusterType clusterType,
                                         DeploymentTarget deploymentTarget,
                                         Map<String, Object> configData) {
        Map<String, String> dtLabels = deploymentTarget.getMetadata().getLabels();
        if (dtLabels == null) dtLabels = Map.of();

        String workspace = dtLabels.getOrDefault(DeploymentTargetSpec.WORKSPACE_LABEL, "");
        String workload = dtLabels.getOrDefault(DeploymentTargetSpec.WORKLOAD_LABEL, "");

        String targetNamespace = TemplateProcessingService
                .buildTargetNamespace(deploymentTarget, clusterType);

        Map<String, String> manifests = new LinkedHashMap<>();
        if (deploymentTarget.getSpec().getManifests() != null) {
            var ref = deploymentTarget.getSpec().getManifests();
            manifests.put("repo",   ref.getRepo()   != null ? ref.getRepo()   : "");
            manifests.put("branch", ref.getBranch() != null ? ref.getBranch() : "");
            manifests.put("path",   ref.getPath()   != null ? ref.getPath()   : "");
        }

        Map<String, String> dtSpecLabels = deploymentTarget.getSpec().getLabels();

        return new TemplateContext.Builder()
                .deploymentTargetName(deploymentTarget.getMetadata().getName())
                .namespace(targetNamespace)
                .environment(deploymentTarget.getSpec().getEnvironment() != null
                        ? deploymentTarget.getSpec().getEnvironment() : "")
                .workspace(workspace)
                .workload(workload)
                .labels(dtSpecLabels != null ? dtSpecLabels : new LinkedHashMap<>())
                .manifests(manifests)
                .clusterType(clusterType.getMetadata().getName())
                .configData(configData)
                .build();
    }

    /**
     * Gathers configuration data from {@code ConfigMap} objects in the namespace whose
     * labels include the cluster type or deployment target identifiers.
     *
     * <p>Merges all matching ConfigMaps' {@code data} maps into one. Mirrors the Go
     * operator's config-gathering loop in {@code assignment_controller.go}. All values
     * remain as {@code String} objects; type coercion is handled by
     * {@link ConfigValidationService} during schema validation.
     *
     * <p>Package-private for unit testing.
     *
     * @param namespace          Kubernetes namespace to search
     * @param clusterTypeName    cluster type identifier for label matching
     * @param deploymentTargetName deployment target identifier for label matching
     * @return merged config data, possibly empty
     */
    Map<String, Object> gatherConfigData(String namespace,
                                          String clusterTypeName,
                                          String deploymentTargetName) {
        Map<String, Object> result = new HashMap<>();

        // Collect ConfigMaps labelled by cluster type
        List<ConfigMap> configMaps = new ArrayList<>();
        configMaps.addAll(kubernetesClient.configMaps().inNamespace(namespace)
                .withLabel(AssignmentPackageSpec.CLUSTER_TYPE_LABEL, clusterTypeName)
                .list().getItems());

        // Collect ConfigMaps labelled by deployment target (de-duplicated by name)
        kubernetesClient.configMaps().inNamespace(namespace)
                .withLabel(AssignmentPackageSpec.DEPLOYMENT_TARGET_LABEL, deploymentTargetName)
                .list().getItems().forEach(cm -> {
                    boolean alreadyListed = configMaps.stream()
                            .anyMatch(e -> e.getMetadata().getName()
                                    .equals(cm.getMetadata().getName()));
                    if (!alreadyListed) configMaps.add(cm);
                });

        // Merge all data maps (later entries overwrite earlier ones on key collision)
        for (ConfigMap cm : configMaps) {
            if (cm.getData() != null) {
                result.putAll(cm.getData());
            }
        }

        logger.debug("Gathered {} config key(s) from {} ConfigMap(s) for clusterType={}, deploymentTarget={}",
                result.size(), configMaps.size(), clusterTypeName, deploymentTargetName);
        return result;
    }

    /**
     * Validates the config data against the {@code ConfigSchema} for the cluster type,
     * if one exists in the namespace.
     *
     * <p>If no {@code ConfigSchema} exists for this cluster type, validation is skipped.
     * Mirrors the Go operator's optional schema validation step.
     *
     * <p>Package-private for unit testing.
     *
     * @param namespace       Kubernetes namespace to search
     * @param clusterTypeName cluster type identifier
     * @param configData      gathered config data to validate
     * @throws ConfigValidationException if the data does not conform to the schema
     */
    void validateConfigData(String namespace, String clusterTypeName,
                             Map<String, Object> configData) {
        List<ConfigSchema> schemas = kubernetesClient
                .resources(ConfigSchema.class).inNamespace(namespace)
                .list().getItems().stream()
                .filter(s -> clusterTypeName.equals(
                        s.getSpec() != null ? s.getSpec().getClusterType() : null))
                .collect(Collectors.toList());

        if (schemas.isEmpty()) {
            logger.debug("No ConfigSchema found for clusterType={} — skipping validation", clusterTypeName);
            return;
        }

        ConfigSchema schema = schemas.get(0);
        try {
            String schemaJson = objectMapper.writeValueAsString(schema.getSpec().getSchema());
            configValidationService.validateValues(configData, schemaJson);
            logger.debug("Config validation passed for clusterType={}", clusterTypeName);
        } catch (ConfigValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate config for clusterType=" + clusterTypeName, e);
        }
    }

    /**
     * Builds an {@code AssignmentPackage} by rendering templates from the {@code ClusterType}.
     *
     * <p>Renders the reconciler and namespace templates defined in the cluster type.
     * The config template is rendered if a template resource named by a CONFIG-typed
     * template reference exists. Each group of manifests is collected into the
     * corresponding list in {@link AssignmentPackageSpec}.
     *
     * <p>Package-private for unit testing.
     *
     * @param name        name for the {@code AssignmentPackage} resource
     * @param namespace   Kubernetes namespace
     * @param clusterType the resolved {@code ClusterType} resource
     * @param ctx         fully populated template context
     * @param owner       the owning {@code Assignment} resource
     * @return a fully populated {@code AssignmentPackage} ready for server-side apply
     */
    AssignmentPackage buildAssignmentPackage(String name, String namespace,
                                              ClusterType clusterType,
                                              TemplateContext ctx,
                                              Assignment owner) {
        AssignmentPackageSpec spec = new AssignmentPackageSpec();

        // Render reconciler template
        if (clusterType.getSpec().getReconciler() != null) {
            Template tmpl = fetchTemplate(namespace, clusterType.getSpec().getReconciler());
            if (tmpl != null) {
                List<String> rendered = renderManifests(tmpl, ctx);
                spec.setReconcilerManifests(rendered);
                spec.setReconcilerManifestsContentType(
                        primaryContentType(tmpl, ContentType.YAML));
            }
        }

        // Render namespace template
        if (clusterType.getSpec().getNamespaceService() != null) {
            Template tmpl = fetchTemplate(namespace, clusterType.getSpec().getNamespaceService());
            if (tmpl != null) {
                List<String> rendered = renderManifests(tmpl, ctx);
                spec.setNamespaceManifests(rendered);
                spec.setNamespaceManifestsContentType(
                        primaryContentType(tmpl, ContentType.YAML));
            }
        }

        // Config manifests: look for a Template of type CONFIG named by convention
        // Go operator checks clusterType.Spec.ConfigTemplate if present
        // Here we list Templates of type CONFIG in the namespace for this cluster type
        List<Template> configTemplates = listTemplatesByType(namespace, TemplateType.CONFIG);
        if (!configTemplates.isEmpty()) {
            Template tmpl = configTemplates.get(0);
            List<String> rendered = renderManifests(tmpl, ctx);
            spec.setConfigManifests(rendered);
            spec.setConfigManifestsContentType(primaryContentType(tmpl, ContentType.YAML));
        }

        // Build labels and owner reference
        Map<String, String> labels = new HashMap<>();
        labels.put(AssignmentPackageSpec.CLUSTER_TYPE_LABEL, clusterType.getMetadata().getName());
        labels.put(AssignmentPackageSpec.DEPLOYMENT_TARGET_LABEL, ctx.getDeploymentTargetName());
        labels.put(ASSIGNMENT_LABEL, owner.getMetadata().getName());

        String ownerUid = owner.getMetadata().getUid() != null
                ? owner.getMetadata().getUid() : "";
        OwnerReference ownerRef = new OwnerReferenceBuilder()
                .withApiVersion(ASSIGNMENT_API_VERSION)
                .withKind(ASSIGNMENT_KIND)
                .withName(owner.getMetadata().getName())
                .withUid(ownerUid)
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();

        AssignmentPackage pkg = new AssignmentPackage();
        pkg.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .withOwnerReferences(ownerRef)
                .build());
        pkg.setSpec(spec);
        return pkg;
    }

    /**
     * Renders all manifests in a {@link Template} resource against the given context.
     *
     * @param template template resource containing Freemarker sources
     * @param ctx      the data context for rendering
     * @return list of rendered manifest strings, one per manifest in the template
     */
    private List<String> renderManifests(Template template, TemplateContext ctx) {
        return template.getSpec().getManifests().stream()
                .map(TemplateManifest::getTemplate)
                .filter(src -> src != null && !src.isEmpty())
                .map(src -> templateProcessingService.processTemplate(src, ctx))
                .collect(Collectors.toList());
    }

    /**
     * Returns the content type of the first manifest in the template, defaulting to
     * {@code defaultType} if none is defined.
     */
    private ContentType primaryContentType(Template template, ContentType defaultType) {
        return template.getSpec().getManifests().stream()
                .map(TemplateManifest::getContentType)
                .filter(ct -> ct != null)
                .findFirst()
                .orElse(defaultType);
    }

    /**
     * Fetches a {@link Template} resource by name from the given namespace.
     * Returns {@code null} and logs a warning if not found.
     */
    private Template fetchTemplate(String namespace, String name) {
        Template tmpl = kubernetesClient.resources(Template.class)
                .inNamespace(namespace).withName(name).get();
        if (tmpl == null) {
            logger.warn("Template not found: namespace={}, name={}", namespace, name);
        }
        return tmpl;
    }

    /**
     * Lists all {@link Template} resources in the namespace with the given
     * {@link TemplateType}.
     */
    private List<Template> listTemplatesByType(String namespace, TemplateType type) {
        return kubernetesClient.resources(Template.class)
                .inNamespace(namespace).list().getItems().stream()
                .filter(t -> type.equals(t.getSpec() != null ? t.getSpec().getType() : null))
                .collect(Collectors.toList());
    }
}
