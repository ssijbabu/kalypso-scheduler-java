# SchedulingPolicyReconciler

## Overview

Reconciler for `SchedulingPolicy` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `schedulingpolicy_controller.go` in the Go operator.

A `SchedulingPolicy` defines the pairing rules between `DeploymentTarget` and `ClusterType` resources. The reconciler creates an `Assignment` CRD for every matching (DT, CT) pair, forming the cartesian product of the filtered sets.

## Reconcile Flow

```
SchedulingPolicy created/updated
  └─ list all DeploymentTargets in namespace
  └─ list all ClusterTypes in namespace
  └─ filter DTs by spec.deploymentTargetSelector
  └─ filter CTs by spec.clusterTypeSelector
  └─ for each (DT, CT) in cartesian product:
       └─ Assignment name = "{policyName}-{dtName}-{ctName}"
       └─ buildAssignment(...) → Assignment with SCHEDULING_POLICY_LABEL + ownerRef
       └─ kubernetesClient.resource(assignment).serverSideApply()
  └─ list existing Assignments with SCHEDULING_POLICY_LABEL={policyName}
  └─ delete any not in computed desired set
  └─ status.conditions[Ready] = True / False
```

**Deletion (via `Cleaner<SchedulingPolicy>`):**

```
SchedulingPolicy deleted
  └─ list Assignments with SCHEDULING_POLICY_LABEL={policyName}
  └─ delete each
  └─ JOSDK removes finalizer
```

## Selector Semantics

Selectors are evaluated with AND logic. A `null` selector matches everything.

| Field | Behavior |
|---|---|
| `selector == null` | Match all resources |
| `selector.workspace != null` | Resource must have `WORKSPACE_LABEL = workspace` |
| `selector.labelSelector != null` | Resource must carry every key/value pair (matchLabels) |
| Both fields set | Both conditions must hold |

## Assignment Naming

`"{policyName}-{dtName}-{ctName}"` — matches Go's `fmt.Sprintf("%s-%s-%s", policy.Name, dt.Name, ct.Name)`.

## Labels Applied to Assignments

| Label | Value |
|---|---|
| `scheduler.kalypso.io/schedulingPolicy` | `policy.metadata.name` |

This label is the selector used for stale-deletion and cleanup; avoids full namespace scans.

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `AssignmentsReconciled` | All Assignments in sync |
| `Ready=False` | `ReconcileError` | Kubernetes API call failed |

## Dependencies

- `KubernetesClient` — lists `DeploymentTarget`, `ClusterType`, `Assignment`; applies and deletes Assignments

## Go Equivalence

Mirrors `SchedulingPolicyReconciler` in `controllers/schedulingpolicy_controller.go`.
