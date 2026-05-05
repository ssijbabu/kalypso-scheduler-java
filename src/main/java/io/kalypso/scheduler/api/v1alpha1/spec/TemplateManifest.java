package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single named Freemarker template and its expected output format.
 *
 * <p>Each {@code TemplateManifest} represents one file that will be rendered and
 * stored in the generated {@code AssignmentPackage}. The {@code template} field
 * holds the raw Freemarker source; the {@code name} identifies it within the package.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateManifest {

    /** Logical name of this manifest within the AssignmentPackage (e.g. "reconciler.yaml"). */
    @JsonProperty("name")
    private String name;

    /** Raw Freemarker template source. Variables are resolved at reconciliation time. */
    @JsonProperty("template")
    private String template;

    /**
     * Format of the rendered output. Defaults to {@link ContentType#YAML}.
     * Set to {@link ContentType#SH} for imperative shell-script manifests.
     */
    @JsonProperty("contentType")
    private ContentType contentType = ContentType.YAML;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }
}
