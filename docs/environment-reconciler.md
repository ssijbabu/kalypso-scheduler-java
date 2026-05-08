# EnvironmentReconciler — Developer Guide

## Who this is for

This document explains the `EnvironmentReconciler` class from scratch. No prior knowledge of
Kubernetes operators, java-operator-sdk, Flux, or the Go operator is assumed. If you can read
Java and you know roughly what a Kubernetes namespace is, you have enough background.

---

## 1. The problem EnvironmentReconciler solves

In the Kalypso model, an `Environment` represents a logical deployment environment such as
`dev`, `staging`, or `prod`. When an environment is created, two things need to happen in
the cluster:

1. A **Kubernetes namespace** must be created with the same name as the environment. This
   namespace is where the environment's workloads will eventually be deployed.
2. **Flux resources** (`GitRepository` + `Kustomization`) must be created in `flux-system`
   so that Flux can sync the environment's control-plane manifests from Git into that namespace.

`EnvironmentReconciler` performs both steps whenever an `Environment` CRD is created or updated.

---

## 2. What is an `Environment`?

An `Environment` is a CRD describing the Git coordinates for an environment's control-plane
manifests:

```yaml
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Environment
metadata:
  name: prod              # this name is also the Kubernetes namespace created
  namespace: kalypso-java
spec:
  controlPlane:
    repo:   https://github.com/org/control-plane
    branch: main
    path:   ./environments/prod
```

Unlike `BaseRepo`, the `Environment` spec uses `controlPlane` (a `RepositoryReference`) rather
than flat fields.

---

## 3. Namespace naming — a key design decision

The Kubernetes namespace that the reconciler creates is named after the environment's
`metadata.name`, **not** its `metadata.namespace`.

| Resource | Kubernetes object | Name |
|---|---|---|
| `Environment.metadata.namespace` | namespace where the CRD lives | `kalypso-java` |
| `Environment.metadata.name` | name of the CRD | `prod` |
| Created namespace | the deployment target | `prod` |

This mirrors the Go operator:
```go
targetNamespace = environment.Name   // NOT environment.Namespace
```

The Flux Kustomization's `targetNamespace` is also set to the environment name (`prod`), so
that Flux applies the manifests into the `prod` namespace.

---

## 4. Flux resource naming

Same pattern as `BaseRepoReconciler`, matching Go:
```go
name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)
```

Java equivalent:
```java
static String buildFluxResourceName(String namespace, String name) {
    return namespace + "-" + name;
}
```

An `Environment` named `prod` in namespace `kalypso-java` creates Flux resources named
`kalypso-java-prod` in `flux-system`.

---

## 5. What happens on reconcile — step by step

```java
reconciler.reconcile(environment, context);
```

**Step 1 — Compute names**

```java
String environmentName = resource.getMetadata().getName();    // "prod"
String fluxName = namespace + "-" + environmentName;          // "kalypso-java-prod"
```

**Step 2 — Create the Kubernetes namespace**

```java
Namespace ns = new NamespaceBuilder()
    .withNewMetadata().withName("prod").endMetadata()
    .build();
kubernetesClient.namespaces().resource(ns).serverSideApply();
```

Uses server-side apply so the call is **idempotent** — if the namespace already exists, the
call is a no-op.

**Step 3 — Create Flux resources**

```java
fluxService.createFluxReferenceResources(
    "kalypso-java-prod",              // Flux resource name
    "flux-system",                    // Flux namespace
    "prod",                           // targetNamespace = environment name (not its own namespace!)
    controlPlane.getRepo(),
    controlPlane.getBranch(),
    controlPlane.getPath(),
    null);                            // no commit pin for environments
```

**Step 4 — Update status**

On success: sets `status.conditions[Ready]=True` with reason `EnvironmentReady`.  
On error: sets `status.conditions[Ready]=False` with reason `ReconcileError`.

---

## 6. What happens on deletion

Same finalizer-based pattern as `BaseRepoReconciler`. On deletion:

1. JOSDK calls `cleanup()` (not `reconcile()`).
2. `cleanup()` deletes the Flux resources from `flux-system`.
3. `cleanup()` deletes the Kubernetes namespace.
4. Returns `DeleteControl.defaultDelete()` — JOSDK removes the finalizer.

Order: Flux resources are deleted first, then the namespace. Errors in either step are logged
but do not prevent the finalizer from being removed.

```java
@Override
public DeleteControl cleanup(Environment resource, Context<Environment> context) {
    fluxService.deleteFluxReferenceResources(fluxName, FluxService.DEFAULT_FLUX_NAMESPACE);
    kubernetesClient.namespaces().withName(environmentName).delete();
    return DeleteControl.defaultDelete();
}
```

---

## 7. Status conditions

| State | `status` | `reason` | `message` |
|---|---|---|---|
| Success | `True` | `EnvironmentReady` | "Namespace and Flux resources created successfully" |
| Error | `False` | `ReconcileError` | (the exception message) |

---

## 8. Correspondence with the Go operator

| Go (`environment_controller.go`) | Java (`EnvironmentReconciler.java`) |
|---|---|
| `name := fmt.Sprintf("%s-%s", req.Namespace, req.Name)` | `buildFluxResourceName(namespace, name)` |
| Create `&corev1.Namespace{...}` then `r.Create(ctx, ns)` | `kubernetesClient.namespaces().resource(ns).serverSideApply()` |
| `targetNamespace = environment.Name` | `environmentName` (not `environment.Namespace`) |
| `controllerutil.AddFinalizer(...)` | `Cleaner<Environment>` — JOSDK manages automatically |
| `flux.CreateFluxReferenceResources(...)` | `fluxService.createFluxReferenceResources(...)` |
| `flux.DeleteFluxReferenceResources(...)` | `fluxService.deleteFluxReferenceResources(...)` |
| `r.Delete(ctx, namespace)` | `kubernetesClient.namespaces().withName(name).delete()` |

---

## 9. Files involved

```
src/main/java/io/kalypso/scheduler/
└── controllers/
    ├── shared/
    │   └── StatusConditionHelper.java
    └── EnvironmentReconciler.java

src/test/java/io/kalypso/scheduler/
└── controllers/
    └── EnvironmentReconcilerTest.java  11 unit tests
```

---

## 10. Why `serverSideApply` instead of `create`?

The Go operator calls `r.Create(ctx, ns)` and handles the `AlreadyExists` error explicitly.
In Java, `serverSideApply()` achieves the same result in one call: if the namespace exists,
it is a no-op; if it does not exist, it is created. This is idempotent by design and removes
the need for an explicit `AlreadyExists` check.
