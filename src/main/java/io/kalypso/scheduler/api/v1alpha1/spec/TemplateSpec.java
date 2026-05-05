package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Desired state of a {@code Template} resource.
 *
 * <p>A Template groups one or more {@link TemplateManifest}s under a single
 * {@link TemplateType}, indicating how the rendered output should be used
 * (reconciler, namespace provisioning, or configuration).
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   type: reconciler
 *   manifests:
 *     - name: reconciler.yaml
 *       template: |
 *         apiVersion: kustomize.toolkit.fluxcd.io/v1beta2
 *         kind: Kustomization
 *         ...
 *       contentType: yaml
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateSpec {

    /**
     * Role this template plays in the scheduling pipeline.
     * Drives which field of {@code AssignmentPackage} receives the rendered output.
     */
    @JsonProperty("type")
    private TemplateType type;

    /** Ordered list of template manifests to render for this template. */
    @JsonProperty("manifests")
    private List<TemplateManifest> manifests = new ArrayList<>();

    public TemplateType getType() {
        return type;
    }

    public void setType(TemplateType type) {
        this.type = type;
    }

    public List<TemplateManifest> getManifests() {
        return manifests;
    }

    public void setManifests(List<TemplateManifest> manifests) {
        this.manifests = manifests != null ? manifests : new ArrayList<>();
    }
}
