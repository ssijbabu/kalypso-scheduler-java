# Day 10 Summary: SchedulingPolicyReconciler

## Completed Tasks

1. **SchedulingPolicyReconciler** (`controllers/SchedulingPolicyReconciler.java`)
   - Mirrors `schedulingpolicy_controller.go` from the Go operator
   - Core scheduling loop: lists all `DeploymentTarget` and `ClusterType` resources in the namespace, applies selectors, creates an `Assignment` for every matching (DT, CT) pair
   - Assignment naming: `{policyName}-{dtName}-{ctName}`
   - Garbage-collects stale `Assignment` resources via `SCHEDULING_POLICY_LABEL` label
   - Implements `Cleaner<SchedulingPolicy>` — deletes all owned Assignments on cleanup

2. **Selector semantics** (`matchesSelector`)
   - `null` selector matches everything (default: select all)
   - Workspace filter: checks `DeploymentTargetSpec.WORKSPACE_LABEL` metadata label
   - Label selector: matchLabels AND logic — every required key/value must be present
   - Both conditions AND-ed when both are non-null

3. **Unit Tests** (`SchedulingPolicyReconcilerTest`)
   - 18 tests covering `matchesSelector` (null, workspace, label, AND), `computeDesiredAssignmentNames` (cartesian product, empty inputs), `buildAssignment` (labels, ownerRef, spec)

## Key Design Decisions

- **Static `SCHEDULING_POLICY_LABEL`**: Applied to every Assignment created by a policy. Used as a list selector for both garbage collection and cleanup, avoiding owner reference traversals.
- **Assignment as cartesian product**: Every matching (DT, CT) pair produces exactly one Assignment. This mirrors the Go operator's `for dt range matched { for ct range matched { ... } }` loop.
- **`computeDesiredAssignmentNames` returns a Set**: Enables O(1) membership check in the stale-deletion loop.

## Build Verification

`mvn clean test` — all tests passing.

## What's Next

Day 11: `AssignmentReconciler`
