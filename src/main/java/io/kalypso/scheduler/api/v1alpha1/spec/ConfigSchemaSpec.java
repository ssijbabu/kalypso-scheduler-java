package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.crd.generator.annotation.PreserveUnknownFields;

/**
 * Desired state of a {@code ConfigSchema} resource.
 *
 * <p>Declares the JSON Schema that configuration data must conform to before it
 * is injected into templates during assignment rendering. The schema is keyed to
 * a specific {@code ClusterType} so that each cluster type can enforce its own
 * configuration contract.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   clusterType: large
 *   schema:
 *     type: object
 *     properties:
 *       REGION:
 *         type: string
 *       DB_URL:
 *         type: string
 *     required:
 *       - REGION
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigSchemaSpec {

    /**
     * Name of the {@code ClusterType} this schema applies to.
     * The {@code AssignmentReconciler} matches this against the cluster type
     * referenced by the assignment being processed.
     */
    @JsonProperty("clusterType")
    private String clusterType;

    /**
     * JSON Schema definition as a free-form object.
     * Declared as {@code Object} so the CRD generator emits
     * {@code x-kubernetes-preserve-unknown-fields: true}, allowing the API server
     * to accept any valid JSON Schema structure without type-checking individual
     * keywords such as {@code type}, {@code properties}, or {@code required}.
     * At runtime Jackson deserializes this into a {@code LinkedHashMap} tree
     * that can be traversed or re-serialized as needed.
     */
    @PreserveUnknownFields
    @JsonProperty("schema")
    private Object schema;

    /** @return name of the target ClusterType, or {@code null} if not set */
    public String getClusterType() {
        return clusterType;
    }

    /** @param clusterType name of the target ClusterType */
    public void setClusterType(String clusterType) {
        this.clusterType = clusterType;
    }

    /** @return the JSON Schema as a deserialized object tree, or {@code null} if not set */
    public Object getSchema() {
        return schema;
    }

    /** @param schema JSON Schema as a nested map / list structure or any Jackson-compatible object */
    public void setSchema(Object schema) {
        this.schema = schema;
    }
}
