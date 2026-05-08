# GitOpsRepoReconciler

## Overview

Reconciler for `GitOpsRepo` (`scheduler.kalypso.io/v1alpha1`) resources. Mirrors `gitopsrepo_controller.go` in the Go operator.

A `GitOpsRepo` describes the target GitHub repository where rendered manifests should be delivered via Pull Request. This reconciler aggregates all `AssignmentPackage` manifests in the namespace and opens a PR in the target repository.

## Reconcile Flow

```
GitOpsRepo created/updated
  └─ allPoliciesReady(namespace)
       └─ list SchedulingPolicies → check Ready=True on each
       └─ return early with Ready=False (PoliciesNotReady) if any not ready
  └─ list AssignmentPackages in namespace
       └─ return early with Ready=False (NoPackages) if list is empty
  └─ buildFileContents(spec.path, packages)
       └─ for each package: read CLUSTER_TYPE_LABEL, DEPLOYMENT_TARGET_LABEL
       └─ build path "{basePath}/{clusterType}/{deploymentTarget}/{role}.{ext}"
  └─ extractRepoFullName(spec.repo)   → strips "https://{host}/" and ".git"
  └─ gitHubService.createPullRequest(repoFullName, title, spec.branch, fileContents)
  └─ status.conditions[Ready] = True (PRCreated) or False (ReconcileError)
```

**Deletion (via `Cleaner<GitOpsRepo>`):**

Immediate `DeleteControl.defaultDelete()` — PRs are external state in GitHub; no Kubernetes-side cleanup is needed.

## GitOps File Structure

```
{spec.path}/{clusterType}/{deploymentTarget}/reconciler.yaml   (or .sh)
{spec.path}/{clusterType}/{deploymentTarget}/namespace.yaml    (or .sh)
{spec.path}/{clusterType}/{deploymentTarget}/platform-config.yaml (if configManifests present)
```

Mirrors Go's `getTree` function in `githubrepo.go`.

## Dependency Gate

The reconciler gates on **all `SchedulingPolicy` resources** being `Ready=True` before creating the PR. If any policy is not ready, the Assignment chain is incomplete and the PR would commit partial manifests.

An empty `SchedulingPolicy` list passes the gate (nothing to wait for).

## `extractRepoFullName`

Converts a full HTTPS URL to the `"org/repo"` short form required by `GitHubService`:
- `https://github.com/org/repo` → `org/repo`
- `https://github.com/org/repo.git` → `org/repo`
- `https://myghe.example.com/org/repo` → `org/repo` (arbitrary host)

## Status Conditions

| Condition | Reason | Meaning |
|---|---|---|
| `Ready=True` | `PRCreated` | PR opened in target repository |
| `Ready=False` | `PoliciesNotReady` | One or more SchedulingPolicies are not Ready |
| `Ready=False` | `NoPackages` | No AssignmentPackage resources found in namespace |
| `Ready=False` | `ReconcileError` | Exception during PR creation |

## Dependencies

- `KubernetesClient` — lists `SchedulingPolicy` and `AssignmentPackage` resources
- `GitHubService` — creates the Pull Request via the GitHub API

## Go Equivalence

Mirrors `GitOpsRepoReconciler` in `controllers/gitopsrepo_controller.go`.
