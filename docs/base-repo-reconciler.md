# BaseRepoReconciler — Developer Guide

## Who this is for

This document explains the `BaseRepoReconciler` class from scratch. No prior knowledge of
Kubernetes operators, java-operator-sdk, or the Go operator is assumed. If you can read Java
and you know roughly what a Git repository is, you have enough background.

---

## 1. The problem BaseRepoReconciler solves

When a user creates a `BaseRepo` CRD resource in Kubernetes (e.g., "here is the Git repo for
the control plane"), the operator needs to tell Flux about it — so that Flux's controllers can
automatically sync the manifests from that repository into the cluster.

`BaseRepoReconciler` bridges the gap: it watches for `BaseRepo` resources and creates the
corresponding Flux `GitRepository` + `Kustomization` resource pair in the `flux-system`
namespace.

---

## 2. What is a `BaseRepo`?

A `BaseRepo` is a CRD (Custom Resource Definition) that describes a Git repository the operator
should track. Its spec has four fields:

```yaml
apiVersion: scheduler.kalypso.io/v1alpha1
kind: BaseRepo
metadata:
  name: control-plane
  namespace: kalypso-java
spec:
  repo:   https://github.com/org/control-plane  # required: Git URL
  branch: main                                   # required: branch to track
  path:   ./environments                         # required: path within the repo
  commit: ""                                     # optional: pin to a specific SHA
```

When a `BaseRepo` is created, the reconciler creates two Flux resources so that Flux syncs
the manifests from that repository.

---

## 3. Flux resource naming

The Go operator uses:
```go
name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)
```

The Java reconciler mirrors this exactly:
```java
static String buildFluxResourceName(String namespace, String name) {
    return namespace + "-" + name;
}
```

So a `BaseRepo` named `control-plane` in namespace `kalypso-java` creates Flux resources
named `kalypso-java-control-plane` in `flux-system`.

---

## 4. What happens on reconcile — step by step

```java
reconciler.reconcile(baseRepo, context);
```

**Step 1 — Compute the Flux resource name**

```java
String fluxName = namespace + "-" + name;  // e.g. "kalypso-java-control-plane"
```

**Step 2 — Create Flux resources**

Delegates to `FluxService.createFluxReferenceResources(...)`:

```java
fluxService.createFluxReferenceResources(
    fluxName,                            // name for both Flux resources
    FluxService.DEFAULT_FLUX_NAMESPACE,  // "flux-system"
    resource.getMetadata().getNamespace(), // targetNamespace (BaseRepo's own namespace)
    spec.getRepo(),
    spec.getBranch(),
    spec.getPath(),
    spec.getCommit());
```

This creates (or updates via server-side apply) a `GitRepository` and `Kustomization` in
`flux-system`. The Kustomization applies manifests into the `BaseRepo`'s own namespace.

**Step 3 — Update status**

On success: sets `status.conditions[Ready]=True` with reason `FluxResourcesCreated`.  
On error: sets `status.conditions[Ready]=False` with reason `FluxError` and the error message.

Returns `UpdateControl.patchStatus(resource)` so JOSDK patches the status subresource.

---

## 5. What happens on deletion — the finalizer

When a `BaseRepo` is deleted from Kubernetes, the operator must clean up the Flux resources
it created. Without cleanup, the Flux controllers would keep syncing from a repo that no
longer has an owner.

JOSDK automates this via the `Cleaner<T>` interface:

1. **Before first reconcile**: JOSDK automatically adds a finalizer to the `BaseRepo`
   resource. This prevents Kubernetes from immediately deleting it.
2. **On deletion**: When a user runs `kubectl delete baserepo control-plane`, Kubernetes
   sets the `deletionTimestamp` but does **not** remove the resource yet (because the
   finalizer is present). JOSDK detects this and calls `cleanup()` instead of `reconcile()`.
3. **In `cleanup()`**: The reconciler deletes the Flux resources.
4. **After `cleanup()` returns**: JOSDK removes the finalizer, and Kubernetes finishes
   deleting the `BaseRepo`.

This mirrors Go's `controllerutil.AddFinalizer` / `controllerutil.RemoveFinalizer` pattern.

```java
@Override
public DeleteControl cleanup(BaseRepo resource, Context<BaseRepo> context) {
    String fluxName = buildFluxResourceName(namespace, name);
    fluxService.deleteFluxReferenceResources(fluxName, FluxService.DEFAULT_FLUX_NAMESPACE);
    return DeleteControl.defaultDelete();  // signals JOSDK to remove the finalizer
}
```

Deletion errors are **logged but not rethrown** — the finalizer is always removed even on
error, to avoid leaving resources stuck in `Terminating` state indefinitely.

---

## 6. Status conditions

After each reconcile, the reconciler writes a `Ready` condition to `status.conditions`:

| State | `status` | `reason` | `message` |
|---|---|---|---|
| Success | `True` | `FluxResourcesCreated` | "Flux GitRepository and Kustomization created successfully" |
| Error | `False` | `FluxError` | (the exception message) |

You can inspect the condition with:
```bash
kubectl get baserepo control-plane -o jsonpath='{.status.conditions}'
```

---

## 7. Correspondence with the Go operator

| Go (`baserepo_controller.go`) | Java (`BaseRepoReconciler.java`) |
|---|---|
| `ctrl.Request{Namespace, Name}` | `resource.getMetadata().getNamespace()` / `.getName()` |
| `name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)` | `buildFluxResourceName(namespace, name)` |
| `controllerutil.AddFinalizer(baserepo, finalizer)` | `Cleaner<BaseRepo>` — JOSDK adds finalizer automatically |
| `controllerutil.RemoveFinalizer(baserepo, finalizer)` | `DeleteControl.defaultDelete()` — JOSDK removes finalizer |
| `flux.CreateFluxReferenceResources(...)` | `fluxService.createFluxReferenceResources(...)` |
| `flux.DeleteFluxReferenceResources(...)` | `fluxService.deleteFluxReferenceResources(...)` |
| `meta.SetStatusCondition(...)` | `StatusConditionHelper.setReady/setNotReady(...)` |

---

## 8. Files involved

```
src/main/java/io/kalypso/scheduler/
└── controllers/
    ├── shared/
    │   └── StatusConditionHelper.java
    └── BaseRepoReconciler.java

src/test/java/io/kalypso/scheduler/
└── controllers/
    └── BaseRepoReconcilerTest.java  9 unit tests
```

---

## 9. Key limitations

- **No spec change detection**: The reconciler calls `createFluxReferenceResources` on every
  reconcile regardless of whether the spec changed. This is intentional — server-side apply
  is idempotent, so calling it with the same values is a no-op.

- **Cleanup errors are swallowed**: If Flux resource deletion fails during cleanup, the error
  is logged but the finalizer is still removed. This prevents stuck resources but may leave
  orphaned Flux resources that need manual cleanup.
