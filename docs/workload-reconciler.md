# WorkloadReconciler

## Overview

Reconciler for `Workload` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `workload_controller.go` in the Go operator.

A `Workload` declares the set of deployment targets for an application. The reconciler maintains a 1:1 correspondence between `spec.deploymentTargets` entries and actual `DeploymentTarget` CRDs in the namespace.

## Reconcile Flow

```
Workload created/updated
  └─ resolveWorkspace(workloadName, namespace)
       └─ kubernetesClient.get(WorkloadRegistration, name=workloadName)
  └─ for each target in spec.deploymentTargets:
       └─ buildDeploymentTargetName(prefix, target.name)  → "{ns}-{wl}-{target}"
       └─ buildDeploymentTarget(...)                       → DeploymentTarget with labels + ownerRef
       └─ kubernetesClient.resource(dt).serverSideApply()
  └─ list existing DTs by WORKLOAD_LABEL={workloadName}
  └─ delete any not in desired set
  └─ status.conditions[Ready] = True / False
```

**Deletion (via `Cleaner<Workload>`):**

```
Workload deleted
  └─ list DeploymentTargets with WORKLOAD_LABEL={workloadName}
  └─ delete each
  └─ JOSDK removes finalizer
```

## Naming Convention

`DeploymentTarget` name = `"{namespace}-{workloadName}-{targetName}"`

Matches Go: `dtName := fmt.Sprintf("%s-%s", name, target.Name)` where `name = fmt.Sprintf("%s-%s", req.Namespace, req.Name)`.

## Labels Applied to DeploymentTargets

| Label | Value |
|---|---|
| `workload.scheduler.kalypso.io/workspace` | Resolved from `WorkloadRegistration.spec.workspace` |
| `workload.scheduler.kalypso.io/workload` | `workload.metadata.name` |

## Workspace Resolution

The `WorkloadSpec` has no `workspace` field. Workspace is read from the `WorkloadRegistration` resource with the same name in the same namespace. If no registration is found, workspace defaults to `""`.

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `DeploymentTargetsReconciled` | All DTs in sync |
| `Ready=False` | `ReconcileError` | Kubernetes API call failed |

## Dependencies

- `KubernetesClient` — lists/creates/deletes `DeploymentTarget` and reads `WorkloadRegistration`

## Go Equivalence

Mirrors `WorkloadReconciler` in `controllers/workload_controller.go`.
