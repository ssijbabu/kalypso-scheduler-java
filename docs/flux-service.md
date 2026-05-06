# FluxService — Developer Guide

## Who this is for

This document explains the `FluxService` class from scratch. No prior knowledge of
Flux, GitOps, or Kubernetes operators is assumed. If you can read Java and you know
roughly what a Kubernetes resource is (like a Pod or a Deployment), you have enough
background.

---

## 1. The problem FluxService solves

The Kalypso operator's job is to look at high-level configuration objects in Kubernetes
(like `BaseRepo`, `Environment`, `WorkloadRegistration`) and make sure the right
Kubernetes manifests end up applied to the right clusters.

Those manifests live in **Git repositories** — not inside the operator itself. The
operator does not clone repos, apply YAML files, or manage Git directly. Instead, it
delegates that entire job to **Flux**.

`FluxService` is the bridge between the Kalypso operator and Flux. It is the only
place in the codebase that creates or deletes Flux resources.

---

## 2. What is Flux?

Flux is a set of Kubernetes controllers (programs that run inside the cluster) that
watch Git repositories and apply whatever YAML files they find there to the cluster.
It is the standard tool for GitOps — the practice of using Git as the source of truth
for what should be running in a cluster.

Flux is **not** part of this operator. It runs independently and is already installed
in the cluster before the Kalypso operator starts. Kalypso simply tells Flux "please
watch this repo" by creating Flux's own custom resources.

---

## 3. The two Flux resources Kalypso creates

Flux uses two resource types to pull manifests from a Git repo and apply them to a
cluster. Kalypso creates both, always as a pair with the same name.

### 3.1 GitRepository (`source.toolkit.fluxcd.io/v1`)

This resource tells the Flux **source controller** to clone a Git repository and keep
the local copy up to date. It answers the question: *"Which repo, which branch, how
often?"*

Key fields Kalypso sets:

| Field | What it means |
|---|---|
| `spec.url` | The HTTPS or SSH URL of the Git repository |
| `spec.ref.branch` | The branch to track |
| `spec.ref.commit` | Optional — pin to a specific commit SHA instead of the branch tip |
| `spec.interval` | How often Flux checks for new commits (Kalypso uses `10s`) |
| `spec.secretRef.name` | The name of the Kubernetes Secret that holds the Git credentials |

Without `secretRef`, Flux can only clone public repos. Private GitHub repos require a
Secret containing an SSH key or a Personal Access Token. Kalypso always sets this.

### 3.2 Kustomization (`kustomize.toolkit.fluxcd.io/v1`)

This resource tells the Flux **kustomize controller** to take the files that the
source controller cloned and apply them to the cluster. It answers: *"Which source,
which folder, apply where?"*

Key fields Kalypso sets:

| Field | What it means |
|---|---|
| `spec.sourceRef.kind` | Always `GitRepository` |
| `spec.sourceRef.name` | The name of the GitRepository to read from |
| `spec.path` | The folder inside the repo where the manifests live |
| `spec.interval` | How often to re-apply even if nothing changed (Kalypso uses `10s`) |
| `spec.prune` | `true` — Flux deletes cluster resources that are removed from the repo |
| `spec.targetNamespace` | The Kubernetes namespace where the manifests are applied |

`prune: true` is important. Without it, if a manifest is deleted from Git, the
corresponding resource stays in the cluster indefinitely. With `prune: true`, Flux
removes it automatically.

---

## 4. Where these resources live — the namespace design

This is one of the most important things to understand. The two resource types live in
**different namespaces**:

```
flux-system (namespace)
    └── GitRepository "kalypso-java-baserepo"   ← created by Kalypso
    └── Kustomization "kalypso-java-baserepo"   ← created by Kalypso

kalypso-java (namespace)
    └── BaseRepo "baserepo"                     ← the Kalypso CRD that triggered creation
    └── (whatever the repo's manifests deploy)  ← applied by Flux into this namespace
```

**Why `flux-system`?**

The Flux controllers only watch the `flux-system` namespace for their own resources by
default. If you create a `GitRepository` in a different namespace, the source controller
ignores it. All `GitRepository` and `Kustomization` resources must live in `flux-system`.

**Why a different `targetNamespace`?**

The Kustomization resource lives in `flux-system`, but the manifests it applies should
land in the namespace of the Kalypso CRD (e.g. `kalypso-java`). The `targetNamespace`
field tells Flux where to send the applied resources, even though the Kustomization
itself lives elsewhere.

This mirrors the Go operator constant:
```go
const DefaulFluxNamespace = "flux-system"
```

---

## 5. The Git credentials secret

Flux needs to authenticate with GitHub (or any private Git host) to clone the repo.
It reads credentials from a Kubernetes `Secret`.

The secret must:
- Live in `flux-system` (the same namespace as the `GitRepository`)
- Be named `gh-repo-secret` by default (configurable via `flux.secret-name` in
  `application.properties`)
- Contain either an SSH private key or an HTTPS token in the format Flux expects

Kalypso does **not** create this secret. It must exist in the cluster before any
Kalypso `BaseRepo`, `Environment`, or `WorkloadRegistration` resource is created.
The `FluxService` only *references* the secret by name.

This mirrors the Go operator constant:
```go
const RepoSecretName = "gh-repo-secret"
```

---

## 6. The FluxService class — step by step

### 6.1 Construction

```java
FluxService fluxService = new FluxService(kubernetesClient);
// or, with a custom secret name read from application.properties:
FluxService fluxService = new FluxService(kubernetesClient, "my-custom-secret");
```

The service holds:
- A `KubernetesClient` — the fabric8 client used to talk to the Kubernetes API server
- A `secretName` — the name of the Git credentials secret (default: `gh-repo-secret`)
- An `ObjectMapper` — Jackson mapper used internally to serialize specs to JSON

### 6.2 `createFluxReferenceResources`

```java
fluxService.createFluxReferenceResources(
    name,            // name of both Flux resources (e.g. "kalypso-java-baserepo")
    namespace,       // where Flux resources live — always "flux-system"
    targetNamespace, // where manifests are applied — the CRD's namespace
    url,             // Git repo URL
    branch,          // branch to track
    path,            // folder inside the repo
    commit           // optional SHA — pass null or "" to track branch head
);
```

**What happens internally, step by step:**

**Step 1 — Build the `GitRepository` resource**

The private `buildGitRepository` method constructs a `GenericKubernetesResource`
object (fabric8's way of representing any Kubernetes resource without a typed Java
class). It:

1. Creates a `GitRepositoryRef` POJO with `branch` set and optionally `commit` (only
   if the commit string is non-empty — an empty string is treated the same as null and
   the field is omitted from the JSON).
2. Creates a `LocalObjectReference` POJO with `name` set to the configured secret name.
3. Creates a `GitRepositorySpec` POJO with `url`, `interval` (`"10s"`), the ref, and
   the secretRef.
4. Creates a `GenericKubernetesResource`, sets `apiVersion` to
   `source.toolkit.fluxcd.io/v1`, `kind` to `GitRepository`, and the metadata.
5. Uses Jackson's `ObjectMapper.convertValue()` to turn the `GitRepositorySpec` POJO
   into a plain `Map<String, Object>`, then sets it as the `spec` field on the resource.

The result looks like this in YAML:

```yaml
apiVersion: source.toolkit.fluxcd.io/v1
kind: GitRepository
metadata:
  name: kalypso-java-baserepo
  namespace: flux-system
spec:
  url: https://github.com/org/control-plane
  interval: 10s
  ref:
    branch: main
  secretRef:
    name: gh-repo-secret
```

**Step 2 — Submit the `GitRepository` via server-side apply**

```java
kubernetesClient
    .genericKubernetesResources(GIT_REPO_CONTEXT)  // tells fabric8 which CRD to talk to
    .inNamespace(namespace)                         // targets flux-system
    .resource(gitRepo)                              // the resource we just built
    .serverSideApply();                             // create-or-update atomically
```

`genericKubernetesResources` takes a `ResourceDefinitionContext` — a descriptor that
tells the fabric8 client the API group (`source.toolkit.fluxcd.io`), version (`v1`),
kind (`GitRepository`), and that it is namespaced. Without this context, fabric8 would
not know how to route the HTTP request.

`serverSideApply()` means: "send this as a PATCH request with field manager
`fabric8`; if the resource does not exist, create it; if it exists, merge the fields
we own." This is idempotent — calling it 10 times in a row is safe and converges to
the same state. The Go operator uses a manual Get-then-Create-or-Update pattern; SSA
achieves the same result more cleanly.

**Step 3 — Build the `Kustomization` resource**

The private `buildKustomization` method constructs a second `GenericKubernetesResource`.
It:

1. Creates a `CrossNamespaceSourceReference` POJO with `kind: "GitRepository"` and
   `name` pointing at the GitRepository created in step 2.
2. Creates a `KustomizationSpec` POJO with `sourceRef`, `path`, `interval` (`"10s"`),
   `prune: true`, and `targetNamespace`.
3. Converts it to a `GenericKubernetesResource` with `apiVersion`
   `kustomize.toolkit.fluxcd.io/v1`.

The result in YAML:

```yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: kalypso-java-baserepo
  namespace: flux-system
spec:
  sourceRef:
    kind: GitRepository
    name: kalypso-java-baserepo
  path: ./environments
  interval: 10s
  prune: true
  targetNamespace: kalypso-java
```

**Step 4 — Submit the `Kustomization` via server-side apply**

Same pattern as step 2, but using the `KUSTOMIZATION_CONTEXT` descriptor.

After both submissions, Flux takes over automatically:
- The **source controller** notices the new `GitRepository`, clones the repo, and
  stores a reference to the cloned content internally.
- The **kustomize controller** notices the new `Kustomization`, reads the cloned
  content from the source controller, applies all YAML files found at `spec.path` into
  `spec.targetNamespace`.

Kalypso does not wait for this. It fires the request and moves on. Flux reconciles
asynchronously every `10s`.

### 6.3 `deleteFluxReferenceResources`

```java
fluxService.deleteFluxReferenceResources(name, namespace);
```

Deletes the `GitRepository` and then the `Kustomization` with the given name from the
given namespace. If either resource does not exist, the deletion is silently ignored
(Kubernetes returns a 404, which fabric8 treats as a no-op for `delete()`).

When the `Kustomization` is deleted, Flux's kustomize controller — because `prune: true`
was set — removes all the cluster resources it had previously applied from that
Kustomization. This is the cascading cleanup mechanism.

---

## 7. How a reconciler uses FluxService

The `FluxService` is not called directly by users — it is called by Kalypso's
reconcilers. Here is the pattern used in the Go `BaseRepoReconciler`, which the Java
`BaseRepoReconciler` (Day 8) will mirror:

```
User creates BaseRepo "my-repo" in namespace "kalypso-java"
    └── BaseRepoReconciler.reconcile() fires
        └── fluxService.createFluxReferenceResources(
                "kalypso-java-my-repo",          // name = namespace + "-" + resourceName
                "flux-system",                   // Flux resources always go here
                "kalypso-java",                  // manifests applied into the CRD's namespace
                "https://github.com/org/repo",   // from BaseRepo.spec.repo
                "main",                          // from BaseRepo.spec.branch
                "./environments",                // from BaseRepo.spec.path
                "")                              // no commit pin
            └── Flux GitRepository created in flux-system
            └── Flux Kustomization created in flux-system
                └── Flux clones the repo every 10s
                └── Flux applies manifests into kalypso-java

User deletes BaseRepo "my-repo"
    └── BaseRepoReconciler.reconcile() fires
        └── fluxService.deleteFluxReferenceResources(
                "kalypso-java-my-repo",
                "flux-system")
            └── Flux GitRepository deleted
            └── Flux Kustomization deleted
                └── Flux removes all previously applied resources from kalypso-java
```

---

## 8. Why not use typed Flux Java classes?

A natural question: why use `GenericKubernetesResource` instead of proper Java classes
like `GitRepository extends CustomResource<GitRepositorySpec, GitRepositoryStatus>`?

Two reasons:

**1. CRD generation conflict.** This operator uses a Maven plugin that automatically
generates CRD YAML files for every class that extends `CustomResource`. If `FluxService`
defined `GitRepository` that way, the plugin would generate a `gitrepositories.source.toolkit.fluxcd.io-v1.yml`
file and the build would apply it to the cluster — overwriting Flux's own CRD with an
incomplete version and breaking Flux.

**2. No official Java Flux SDK.** The Go operator imports Flux's own Go packages
(`github.com/fluxcd/source-controller/api/v1beta2`) and gets typed structs for free.
There is no equivalent Java library. Using `GenericKubernetesResource` is the standard
fabric8 approach for interacting with third-party CRDs whose types are not available
as Java classes.

The spec POJOs (`GitRepositorySpec`, `KustomizationSpec`, etc.) in the `flux/model`
package give us type safety and documentation at the spec-building level, while
`GenericKubernetesResource` handles the actual Kubernetes API interaction.

---

## 9. Key constants and configuration

| Location | Key | Value | Meaning |
|---|---|---|---|
| `FluxService.java` | `DEFAULT_FLUX_NAMESPACE` | `"flux-system"` | Where Flux resources are created |
| `FluxService.java` | `REPO_SECRET_NAME` | `"gh-repo-secret"` | Default Git credentials secret |
| `FluxService.java` | `DEFAULT_INTERVAL` | `"10s"` | How often Flux reconciles |
| `FluxService.java` | `FLUX_API_VERSION` | `"v1"` | Flux stable API version |
| `application.properties` | `flux.secret-name` | `gh-repo-secret` | Override the default secret name |
| `application.properties` | `flux.interval` | `10s` | Override the default interval |
| `application.properties` | `flux.default-namespace` | `flux-system` | Override the default namespace |

---

## 10. Files involved

```
src/main/java/io/kalypso/scheduler/
├── flux/model/
│   ├── GitRepositoryRef.java              branch + optional commit SHA
│   ├── GitRepositorySpec.java             url, interval, ref, secretRef
│   ├── LocalObjectReference.java          name of a Secret or ConfigMap
│   ├── CrossNamespaceSourceReference.java kind + name of a GitRepository
│   └── KustomizationSpec.java             sourceRef, path, interval, prune, targetNamespace
└── services/
    └── FluxService.java                   createFluxReferenceResources / deleteFluxReferenceResources

src/test/java/io/kalypso/scheduler/
├── services/
│   └── FluxServiceTest.java               5 unit tests on the build methods
└── it/
    └── OperatorIntegrationIT.java         testFluxServiceCreateAndDeleteReferenceResources

src/main/resources/
└── application.properties                 flux.secret-name, flux.interval, flux.default-namespace
```

---

## 11. Correspondence with the Go operator

The Go implementation lives in `controllers/flux.go`. Every design decision in the
Java `FluxService` has a direct equivalent:

| Go (`flux.go`) | Java (`FluxService.java`) |
|---|---|
| `type Flux interface { CreateFluxReferenceResources(...) }` | `FluxService.createFluxReferenceResources(...)` |
| `type Flux interface { DeleteFluxReferenceResources(...) }` | `FluxService.deleteFluxReferenceResources(...)` |
| `DefaulFluxNamespace = "flux-system"` | `DEFAULT_FLUX_NAMESPACE = "flux-system"` |
| `RepoSecretName = "gh-repo-secret"` | `REPO_SECRET_NAME = "gh-repo-secret"` |
| `FluxInterval = 10 * time.Second` | `DEFAULT_INTERVAL = "10s"` |
| `sourcev1.GitRepository{}` (typed struct) | `GenericKubernetesResource` + `GitRepositorySpec` POJO |
| `gitRepo.Spec.SecretRef = &meta.LocalObjectReference{Name: RepoSecretName}` | `spec.setSecretRef(secretRef)` |
| `kustomization.Spec.Prune = true` | `spec.setPrune(true)` |
| `client.Create(ctx, gitRepo)` or `client.Update(ctx, gitRepo)` | `.serverSideApply()` (idempotent, no pre-check needed) |
| `client.Delete(ctx, kustomization)` | `.delete()` |

The one intentional divergence: the Go operator uses Get-then-Create-or-Update; the
Java operator uses server-side apply, which is the modern Kubernetes-recommended
approach and is inherently idempotent without the extra GET request.
