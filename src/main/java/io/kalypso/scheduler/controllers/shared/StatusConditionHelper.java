package io.kalypso.scheduler.controllers.shared;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for managing Kubernetes status conditions on CRD resources.
 *
 * <p>Mirrors the condition-handling pattern used throughout the Go operator
 * (equivalent to controller-runtime's {@code meta.SetStatusCondition}).
 *
 * <p>All Kalypso reconcilers set a single {@value #CONDITION_TYPE_READY} condition
 * after each reconciliation cycle: {@code Ready=True} on success, {@code Ready=False}
 * with a human-readable message on failure.
 */
public final class StatusConditionHelper {

    /** Condition type used by all Kalypso reconcilers for the top-level ready state. */
    public static final String CONDITION_TYPE_READY = "Ready";

    /** Condition status value indicating the resource is ready. */
    public static final String STATUS_TRUE = "True";

    /** Condition status value indicating the resource is not ready. */
    public static final String STATUS_FALSE = "False";

    private StatusConditionHelper() {}

    /**
     * Sets or replaces a condition in the given list.
     *
     * <p>If a condition of the same {@code type} already exists it is replaced.
     * The {@code lastTransitionTime} is preserved when the {@code status} has not
     * changed; it is updated to the current UTC time when the status changes.
     * This mirrors controller-runtime's {@code meta.SetStatusCondition} semantics.
     *
     * @param conditions the mutable conditions list to update
     * @param type       condition type (e.g. {@link #CONDITION_TYPE_READY})
     * @param status     condition status ({@link #STATUS_TRUE} or {@link #STATUS_FALSE})
     * @param reason     a CamelCase reason token (e.g. {@code "FluxResourcesCreated"})
     * @param message    a human-readable explanation of the current state
     */
    public static void setCondition(List<Condition> conditions,
                                     String type, String status,
                                     String reason, String message) {
        String now = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Optional<Condition> existing = conditions.stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst();

        // preserve lastTransitionTime if status is unchanged
        String transitionTime = existing
                .filter(c -> status.equals(c.getStatus()))
                .map(Condition::getLastTransitionTime)
                .orElse(now);

        Condition condition = new ConditionBuilder()
                .withType(type)
                .withStatus(status)
                .withReason(reason)
                .withMessage(message)
                .withLastTransitionTime(transitionTime)
                .build();

        existing.ifPresent(conditions::remove);
        conditions.add(condition);
    }

    /**
     * Sets a {@code Ready=True} condition.
     *
     * @param conditions the mutable conditions list to update
     * @param reason     a CamelCase reason token
     * @param message    a human-readable explanation
     */
    public static void setReady(List<Condition> conditions, String reason, String message) {
        setCondition(conditions, CONDITION_TYPE_READY, STATUS_TRUE, reason, message);
    }

    /**
     * Sets a {@code Ready=False} condition.
     *
     * @param conditions the mutable conditions list to update
     * @param reason     a CamelCase reason token
     * @param message    a human-readable explanation of the failure
     */
    public static void setNotReady(List<Condition> conditions, String reason, String message) {
        setCondition(conditions, CONDITION_TYPE_READY, STATUS_FALSE, reason, message);
    }
}
