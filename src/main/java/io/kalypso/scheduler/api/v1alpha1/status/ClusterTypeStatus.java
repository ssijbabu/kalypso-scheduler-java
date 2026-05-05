package io.kalypso.scheduler.api.v1alpha1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Observed state of a {@code ClusterType} resource.
 *
 * <p>ClusterType has no dedicated controller. The {@code Ready} condition is
 * expected to be set by controllers that validate the referenced Template names
 * exist and are syntactically correct (e.g. {@code AssignmentReconciler}).
 * An absent or empty conditions list should be treated as {@code Unknown}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClusterTypeStatus {

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
