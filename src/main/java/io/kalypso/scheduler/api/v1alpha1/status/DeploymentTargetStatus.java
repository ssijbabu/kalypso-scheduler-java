package io.kalypso.scheduler.api.v1alpha1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Observed state of a {@code DeploymentTarget} resource.
 *
 * <p>DeploymentTargets are owned by the {@code WorkloadReconciler} and are typically
 * not reconciled by a dedicated controller; they are passive data resources consumed
 * by the {@code SchedulingPolicyReconciler}. The conditions field is present for
 * consistency and future use.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeploymentTargetStatus {

    /**
     * Conditions describing the current observed state. Uses the standard
     * Kubernetes {@code metav1.Condition} type for consistency with kubectl tooling.
     */
    @JsonProperty("conditions")
    private List<Condition> conditions = new ArrayList<>();

    /**
     * Returns the current conditions list.
     *
     * @return the conditions list; never {@code null}
     */
    public List<Condition> getConditions() {
        return conditions;
    }

    /**
     * Sets the conditions list.
     *
     * @param conditions the conditions to set; {@code null} is treated as empty
     */
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
