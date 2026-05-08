# AssignmentReconciler

## Overview

Reconciler for `Assignment` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `assignment_controller.go` in the Go operator.

An `Assignment` links a `ClusterType` to a `DeploymentTarget`. The reconciler renders the cluster type's templates against the deployment target's context (with gathered config data), producing an `AssignmentPackage` that holds the final Kubernetes manifests ready for GitOps delivery.

## Reconcile Flow

```
Assignment created/updated
  └─ fetch ClusterType by spec.clusterType
  └─ fetch DeploymentTarget by spec.deploymentTarget
  └─ gatherConfigData(namespace, clusterTypeName, dtName)
       └─ list ConfigMaps with CLUSTER_TYPE_LABEL={clusterTypeName}
       └─ list ConfigMaps with DEPLOYMENT_TARGET_LABEL={dtName}  (de-duplicated)
       └─ merge all data maps (DT-labelled values overwrite CT-labelled values)
  └─ validateConfigData(namespace, clusterTypeName, configData)
       └─ find ConfigSchema for clusterType (optional — skip if absent)
       └─ serialize schema to JSON → configValidationService.validateValues()
  └─ buildTemplateContext(ct, dt, configData)
  └─ buildAssignmentPackage(name, namespace, ct, ctx, assignment)
       └─ fetchTemplate(ct.spec.reconciler) → render → reconcilerManifests
       └─ fetchTemplate(ct.spec.namespaceService) → render → namespaceManifests
       └─ listTemplates(type=CONFIG) → render first → configManifests
  └─ kubernetesClient.resource(pkg).serverSideApply()
  └─ status.conditions[Ready] = True / False
```

**Deletion (via `Cleaner<Assignment>`):**

```
Assignment deleted
  └─ list AssignmentPackages with ASSIGNMENT_LABEL={assignmentName}
  └─ delete each
  └─ JOSDK removes finalizer
```

## Template Context Keys

| Key | Source |
|---|---|
| `DeploymentTargetName` | `dt.metadata.name` |
| `Namespace` | `TemplateProcessingService.buildTargetNamespace(dt, ct)` = `"{env}-{ct}-{dt}"` |
| `Environment` | `dt.spec.environment` |
| `Workspace` | `dt.metadata.labels[WORKSPACE_LABEL]` |
| `Workload` | `dt.metadata.labels[WORKLOAD_LABEL]` |
| `ClusterType` | `ct.metadata.name` |
| `ConfigData` | merged ConfigMap data |
| `Manifests` | `{repo, branch, path}` from `dt.spec.manifests` |
| `Labels` | `dt.spec.labels` |

## Labels Applied to AssignmentPackage

| Label | Value |
|---|---|
| `scheduler.kalypso.io/clusterType` | `ct.metadata.name` |
| `scheduler.kalypso.io/deploymentTarget` | `dt.metadata.name` |
| `scheduler.kalypso.io/assignment` | `assignment.metadata.name` |

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `PackageRendered` | `AssignmentPackage` rendered and applied |
| `Ready=False` | `ConfigValidationFailed` | Config data failed JSON Schema validation |
| `Ready=False` | `ReconcileError` | Any other exception |

## Dependencies

- `KubernetesClient` — fetches `ClusterType`, `DeploymentTarget`, `ConfigSchema`, `Template`; applies and deletes `AssignmentPackage`
- `TemplateProcessingService` — Freemarker template rendering
- `ConfigValidationService` — JSON Schema validation of gathered config

## Go Equivalence

Mirrors `AssignmentReconciler` in `controllers/assignment_controller.go`.
