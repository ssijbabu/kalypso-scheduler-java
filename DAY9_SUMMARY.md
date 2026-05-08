# Day 9 Summary: WorkloadRegistrationReconciler & WorkloadReconciler

## Completed Tasks

1. **WorkloadRegistrationReconciler** (`controllers/WorkloadRegistrationReconciler.java`)
   - Mirrors `workloadregistration_controller.go` from the Go operator
   - Creates Flux `GitRepository` + `Kustomization` for each `WorkloadRegistration`
   - Resource name pattern: `{namespace}-{name}` (same as `BaseRepoReconciler`)
   - Implements `Cleaner<WorkloadRegistration>` — JOSDK auto-manages finalizers
   - Status: `Ready=True` reason `FluxResourcesCreated`; `Ready=False` reason `FluxError`

2. **WorkloadReconciler** (`controllers/WorkloadReconciler.java`)
   - Mirrors `workload_controller.go` from the Go operator
   - Creates/updates/deletes `DeploymentTarget` child resources to match `spec.deploymentTargets`
   - DeploymentTarget naming: `{namespace}-{workloadName}-{targetName}`
   - Workspace resolved by looking up `WorkloadRegistration` with the same name in the same namespace
   - Sets `WORKSPACE_LABEL` and `WORKLOAD_LABEL` metadata labels on each child `DeploymentTarget`
   - Uses `serverSideApply()` for idempotent create/update
   - Cleanup: deletes all DTs labelled `WORKLOAD_LABEL={workloadName}`

3. **Unit Tests** (`WorkloadRegistrationReconcilerTest`, `WorkloadReconcilerTest`)
   - `WorkloadRegistrationReconcilerTest`: 7 tests using `RecordingFluxService` hand-written test double
   - `WorkloadReconcilerTest`: 10 tests using subclass override of `reconcileDeploymentTargets`

4. **Registered in KalypsoSchedulerOperator** — both reconcilers added to `reconcilers()` list

## Key Design Decisions

- **Workspace resolution via WorkloadRegistration lookup**: The `WorkloadSpec` has no `workspace` field. The workspace is read from the associated `WorkloadRegistration` resource (same name, same namespace). If no registration is found, an empty string is used rather than failing reconciliation.
- **`reconcileDeploymentTargets` package-private**: Allows subclass-override in unit tests to avoid needing a real Kubernetes client.
- **Owner references on DeploymentTargets**: Each `DeploymentTarget` carries an `ownerReference` to its parent `Workload`, enabling Kubernetes GC to clean up automatically if the Workload is force-deleted without going through the finalizer.

## Build Verification

`mvn clean test` — all tests passing (unit suite at this stage: 63 tests).

## What's Next

Day 10: `SchedulingPolicyReconciler`
