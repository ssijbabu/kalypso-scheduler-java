package io.kalypso.scheduler.api.v1alpha1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Observed state of a {@code ConfigSchema} resource.
 *
 * <p>ConfigSchema has no active controller; conditions are updated by the
 * {@code AssignmentReconciler} when it validates configuration data against
 * this schema. The standard {@code Ready} condition is set to {@code True}
 * when the last validation succeeded, or {@code False} with a descriptive
 * reason when validation failed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigSchemaStatus {

    /**
     * Conditions describing the current observed state. Uses the standard
     * Kubernetes {@code metav1.Condition} type for consistency with kubectl tooling.
     */
    @JsonProperty("conditions")
    private List<Condition> conditions = new ArrayList<>();

    /** @return the current conditions list; never {@code null} */
    public List<Condition> getConditions() {
        return conditions;
    }

    /** @param conditions the conditions to set; {@code null} is treated as empty */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
