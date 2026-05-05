package io.kalypso.scheduler.api.v1alpha1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Observed state of a {@code Template} resource.
 *
 * <p>Template has no active controller, so conditions are set by controllers that
 * reference this Template (e.g. {@code AssignmentReconciler}). The standard
 * {@code Ready} condition signals whether the template was parsed and rendered
 * successfully during the last reconciliation cycle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateStatus {

    /**
     * Conditions describing the current observed state. Uses the standard
     * Kubernetes {@code metav1.Condition} type for consistency with kubectl tooling.
     */
    @JsonProperty("conditions")
    private List<Condition> conditions = new ArrayList<>();

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
