# WorkloadRegistrationReconciler

## Overview

Reconciler for `WorkloadRegistration` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `workloadregistration_controller.go` in the Go operator.

Each `WorkloadRegistration` declares a Git repository that holds the workload's Kubernetes manifests. The reconciler creates a pair of Flux resources — a `GitRepository` and a `Kustomization` — so Flux can pull and apply those manifests to the target namespace.

## Reconcile Flow

```
WorkloadRegistration created/updated
  └─ buildFluxResourceName(namespace, name)          → "{namespace}-{name}"
  └─ fluxService.createFluxReferenceResources(...)   → GitRepository + Kustomization
  └─ status.conditions[Ready] = True / False
```

**Deletion (via `Cleaner<WorkloadRegistration>`):**

```
WorkloadRegistration deleted
  └─ fluxService.deleteFluxReferenceResources(...)   → removes GitRepository + Kustomization
  └─ JOSDK removes finalizer
```

## Key Parameters

| `FluxService` call argument | Source |
|---|---|
| `name` | `buildFluxResourceName(namespace, name)` = `"{ns}-{name}"` |
| `namespace` | `FluxService.DEFAULT_FLUX_NAMESPACE` (`"flux-system"`) |
| `targetNamespace` | `resource.getMetadata().getNamespace()` |
| `url` | `spec.workload.repo` |
| `branch` | `spec.workload.branch` |
| `path` | `spec.workload.path` |
| `commit` | `null` (WorkloadRegistration has no pinned commit) |

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `FluxResourcesCreated` | Flux resources applied successfully |
| `Ready=False` | `FluxError` | `FluxService` threw an exception |

## Dependencies

- `FluxService` — creates/deletes Flux GitRepository and Kustomization resources

## Go Equivalence

Mirrors `WorkloadRegistrationReconciler` in `controllers/workloadregistration_controller.go`. The Flux resource name pattern matches Go's `fmt.Sprintf("%s-%s", req.Namespace, req.Name)`.
