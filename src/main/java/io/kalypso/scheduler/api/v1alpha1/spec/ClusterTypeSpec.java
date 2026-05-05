package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Desired state of a {@code ClusterType} resource.
 *
 * <p>A ClusterType acts as a blueprint that wires together three named
 * {@link Template}s — one per rendering role — and declares how configuration
 * data should be delivered to clusters of that type.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   reconciler:       my-reconciler-template
 *   namespaceService: my-namespace-template
 *   configType:       configmap
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterTypeSpec {

    /**
     * Name of the {@code Template} resource whose {@code type=reconciler} manifests
     * are rendered for clusters of this type.
     */
    @JsonProperty("reconciler")
    private String reconciler;

    /**
     * Name of the {@code Template} resource whose {@code type=namespace} manifests
     * provision namespaces on clusters of this type.
     */
    @JsonProperty("namespaceService")
    private String namespaceService;

    /**
     * Mechanism used to deliver configuration data to clusters of this type.
     * Drives how the config manifest is interpreted by the target cluster agent.
     */
    @JsonProperty("configType")
    private ClusterConfigType configType;

    public String getReconciler() {
        return reconciler;
    }

    public void setReconciler(String reconciler) {
        this.reconciler = reconciler;
    }

    public String getNamespaceService() {
        return namespaceService;
    }

    public void setNamespaceService(String namespaceService) {
        this.namespaceService = namespaceService;
    }

    public ClusterConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(ClusterConfigType configType) {
        this.configType = configType;
    }
}
