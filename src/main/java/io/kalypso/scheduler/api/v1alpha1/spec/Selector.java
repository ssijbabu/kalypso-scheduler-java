package io.kalypso.scheduler.api.v1alpha1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Embedded POJO used by {@link SchedulingPolicySpec} to filter both
 * {@code DeploymentTarget} and {@code ClusterType} resources.
 *
 * <p>When both fields are set they are AND-ed together:
 * <ul>
 *   <li>{@link #workspace} — restricts selection to resources whose
 *       {@code workload.scheduler.kalypso.io/workspace} label value matches
 *       this string exactly.</li>
 *   <li>{@link #labelSelector} — matchLabels-style map; a resource must carry
 *       every key/value pair in this map to be selected.</li>
 * </ul>
 * Either field may be omitted; an empty selector matches all resources.
 *
 * <p>Example YAML fragment within a {@code SchedulingPolicy} spec:
 * <pre>
 * deploymentTargetSelector:
 *   workspace: team-alpha
 *   labelSelector:
 *     region: east-us
 *     tier:   production
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Selector {

    /**
     * Restricts selection to resources belonging to the named workspace.
     * Filters by the {@code workload.scheduler.kalypso.io/workspace} label.
     * When {@code null} the workspace filter is not applied.
     */
    @JsonProperty("workspace")
    private String workspace;

    /**
     * matchLabels-style key/value pairs. A resource must carry every pair to
     * be selected. When {@code null} or empty the label filter is not applied.
     */
    @JsonProperty("labelSelector")
    private Map<String, String> labelSelector;

    /**
     * Returns the workspace filter value.
     *
     * @return the workspace name, or {@code null} if the filter is not set
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Sets the workspace filter value.
     *
     * @param workspace the workspace name to match; {@code null} disables this filter
     */
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /**
     * Returns the matchLabels-style label selector map.
     *
     * @return the label selector map, or {@code null} if not set
     */
    public Map<String, String> getLabelSelector() {
        return labelSelector;
    }

    /**
     * Sets the matchLabels-style label selector map.
     *
     * @param labelSelector key/value pairs that a resource must all carry to be selected;
     *                      {@code null} or empty disables this filter
     */
    public void setLabelSelector(Map<String, String> labelSelector) {
        this.labelSelector = labelSelector;
    }
}
