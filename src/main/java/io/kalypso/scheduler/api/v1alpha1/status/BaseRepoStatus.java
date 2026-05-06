package io.kalypso.scheduler.api.v1alpha1.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Observed state of a {@code BaseRepo} resource.
 *
 * <p>The {@code BaseRepoReconciler} writes a {@code Ready} condition after each
 * reconciliation cycle:
 * <ul>
 *   <li>{@code Ready=True} — Flux {@code GitRepository} and {@code Kustomization}
 *       resources were created or verified successfully.</li>
 *   <li>{@code Ready=False} — An error occurred; the {@code message} field
 *       contains the failure reason and the resource will be requeued.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseRepoStatus {

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
