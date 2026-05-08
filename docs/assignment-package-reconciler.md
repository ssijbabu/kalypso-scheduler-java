# AssignmentPackageReconciler

## Overview

Reconciler for `AssignmentPackage` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `assignmentpackage_controller.go` in the Go operator.

An `AssignmentPackage` is created by the `AssignmentReconciler` and contains the fully rendered manifests for a specific (ClusterType, DeploymentTarget) pair. This reconciler validates that the manifest strings are syntactically correct before the `GitOpsRepoReconciler` commits them to GitHub.

## Reconcile Flow

```
AssignmentPackage created/updated
  └─ validateManifests(spec)
       └─ validateGroup("reconcilerManifests", manifests, contentType)
       └─ validateGroup("namespaceManifests", manifests, contentType)
       └─ validateGroup("configManifests", manifests, contentType)
  └─ status.conditions[Ready] = True (ManifestsValid) or False (InvalidManifests)
```

**Deletion:**

No-op cleanup — `AssignmentPackage` resources are owned by their parent `Assignment` via `ownerReference`. Kubernetes garbage collection handles deletion automatically; no finalizer is needed.

## Validation Rules

| Content Type | Validation |
|---|---|
| `YAML` (or `null`) | Parsed with SnakeYAML `new Yaml().load(manifest)` — syntax error → `IllegalArgumentException` |
| `SH` | Checked to be non-null and non-blank |

All manifest groups (`reconcilerManifests`, `namespaceManifests`, `configManifests`) are validated. A `null` or empty list passes without error.

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `ManifestsValid` | All manifest strings pass validation |
| `Ready=False` | `InvalidManifests` | At least one manifest failed (error message names the group and index) |

## Dependencies

None — pure in-memory validation with no Kubernetes API calls.

## Go Equivalence

Mirrors `AssignmentPackageReconciler` in `controllers/assignmentpackage_controller.go`.
