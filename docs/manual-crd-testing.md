# Manual CRD Testing Guide

How to verify each reconciler does what it is supposed to do, using `kubectl` against a live cluster with the operator running.

## Setup

Deploy the operator first — see [deploying-operator.md](deploying-operator.md).

All resources below go into the `kalypso-java` namespace unless noted.

```bash
NS=kalypso-java
```

---

## 1. WorkloadRegistration → WorkloadRegistrationReconciler

**What it does**: Creates a Flux `GitRepository` + `Kustomization` pair in `flux-system`.

**Prerequisite**: Flux v2 installed, a Secret named `kalypso-git-secret` in `flux-system` with SSH/HTTPS credentials for the repo.

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: WorkloadRegistration
metadata:
  name: my-app
  namespace: $NS
spec:
  workspace: my-workspace
  workload:
    repo: https://github.com/org/my-app
    branch: main
    path: ./
EOF
```

**Check**:
```bash
# Flux GitRepository created in flux-system
kubectl get gitrepository -n flux-system kalypso-java-my-app

# Flux Kustomization created in flux-system
kubectl get kustomization -n flux-system kalypso-java-my-app

# Operator set Ready=True on the WorkloadRegistration
kubectl get workloadregistration my-app -n $NS -o jsonpath='{.status.conditions[0]}'
```

Expected: `{"reason":"FluxResourcesCreated","status":"True","type":"Ready"}`.

**Cleanup**:
```bash
kubectl delete workloadregistration my-app -n $NS
# Operator's cleanup() deletes the Flux resources automatically via the finalizer.
```

---

## 2. BaseRepo → BaseRepoReconciler

**What it does**: Same Flux pair pattern as WorkloadRegistration but for the base (platform) repository.

**Prerequisite**: Same as #1.

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: BaseRepo
metadata:
  name: platform
  namespace: $NS
spec:
  repo: https://github.com/org/platform
  branch: main
  path: ./base
EOF
```

**Check**:
```bash
kubectl get gitrepository -n flux-system kalypso-java-platform
kubectl get kustomization -n flux-system kalypso-java-platform
kubectl get baserepo platform -n $NS -o jsonpath='{.status.conditions[0]}'
```

**Cleanup**:
```bash
kubectl delete baserepo platform -n $NS
```

---

## 3. Environment → EnvironmentReconciler

**What it does**: Same Flux pair pattern for the environment-level repository.

**Prerequisite**: Same as #1.

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Environment
metadata:
  name: staging
  namespace: $NS
spec:
  repo: https://github.com/org/environments
  branch: main
  path: ./staging
EOF
```

**Check**:
```bash
kubectl get gitrepository -n flux-system kalypso-java-staging
kubectl get kustomization -n flux-system kalypso-java-staging
kubectl get environment staging -n $NS -o jsonpath='{.status.conditions[0]}'
```

**Cleanup**:
```bash
kubectl delete environment staging -n $NS
```

---

## 4. Workload → WorkloadReconciler

**What it does**: Creates one `DeploymentTarget` per entry in `spec.deploymentTargets`. Resolves the workspace label by looking up a `WorkloadRegistration` with the same name.

**Step 1** — Create the WorkloadRegistration (provides the workspace value):

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: WorkloadRegistration
metadata:
  name: my-app
  namespace: $NS
spec:
  workspace: my-workspace
  workload:
    repo: https://github.com/org/my-app
    branch: main
    path: ./
EOF
```

**Step 2** — Create the Workload:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Workload
metadata:
  name: my-app
  namespace: $NS
spec:
  deploymentTargets:
    - name: west
      manifests:
        repo: https://github.com/org/my-app
        branch: main
        path: ./targets/west
    - name: east
      manifests:
        repo: https://github.com/org/my-app
        branch: main
        path: ./targets/east
EOF
```

**Check**:
```bash
# Two DeploymentTargets created: {ns}-{workload}-{target}
kubectl get deploymenttargets -n $NS

# Expected names
kubectl get deploymenttarget kalypso-java-my-app-west -n $NS -o jsonpath='{.metadata.labels}'
kubectl get deploymenttarget kalypso-java-my-app-east -n $NS -o jsonpath='{.metadata.labels}'
# Labels must include:
#   workload.scheduler.kalypso.io/workspace: my-workspace
#   workload.scheduler.kalypso.io/workload:  my-app

# Workload status
kubectl get workload my-app -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=DeploymentTargetsReconciled
```

**Test stale-target deletion** — remove one target and verify its DT is deleted:

```bash
kubectl patch workload my-app -n $NS --type=merge \
  -p '{"spec":{"deploymentTargets":[{"name":"east","manifests":{"repo":"https://github.com/org/my-app","branch":"main","path":"./targets/east"}}]}}'

# west DT should be gone within seconds
kubectl get deploymenttarget kalypso-java-my-app-west -n $NS   # should return NotFound
kubectl get deploymenttarget kalypso-java-my-app-east -n $NS   # should still exist
```

**Cleanup**:
```bash
kubectl delete workload my-app -n $NS
kubectl delete workloadregistration my-app -n $NS
# DeploymentTargets are deleted by the Workload's cleanup() finalizer.
```

---

## 5. SchedulingPolicy → SchedulingPolicyReconciler

**What it does**: Creates an `Assignment` for every matching `(DeploymentTarget, ClusterType)` pair. Assignment name = `{policy}-{dt}-{ct}`.

**Step 1** — Create a DeploymentTarget (or reuse one from test 4):

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: DeploymentTarget
metadata:
  name: dt-east
  namespace: $NS
  labels:
    workload.scheduler.kalypso.io/workspace: my-workspace
    workload.scheduler.kalypso.io/workload: my-app
spec:
  name: east
  environment: staging
EOF
```

**Step 2** — Create a ClusterType:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: ClusterType
metadata:
  name: aks-large
  namespace: $NS
spec:
  reconciler: reconciler-template
  namespaceService: namespace-template
EOF
```

**Step 3** — Create a SchedulingPolicy selecting by workspace:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: SchedulingPolicy
metadata:
  name: my-policy
  namespace: $NS
spec:
  deploymentTargetSelector:
    workspace: my-workspace
EOF
```

**Check**:
```bash
# Assignment created: {policy}-{dt}-{ct}
kubectl get assignment my-policy-dt-east-aks-large -n $NS

# Assignment has the schedulingPolicy label
kubectl get assignment my-policy-dt-east-aks-large -n $NS \
  -o jsonpath='{.metadata.labels.scheduler\.kalypso\.io/schedulingPolicy}'
# Expected: my-policy

# Assignment spec
kubectl get assignment my-policy-dt-east-aks-large -n $NS -o jsonpath='{.spec}'
# Expected: {"clusterType":"aks-large","deploymentTarget":"dt-east"}

# SchedulingPolicy status
kubectl get schedulingpolicy my-policy -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=AssignmentsReconciled
```

**Test stale-assignment deletion** — add a second DT that doesn't match the selector:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: DeploymentTarget
metadata:
  name: dt-west
  namespace: $NS
  labels:
    workload.scheduler.kalypso.io/workspace: other-workspace
spec:
  name: west
  environment: staging
EOF
```

The policy selects workspace `my-workspace`, so `dt-west` must NOT get an Assignment.

```bash
kubectl get assignment my-policy-dt-west-aks-large -n $NS   # must return NotFound
```

**Cleanup**:
```bash
kubectl delete schedulingpolicy my-policy -n $NS
# Operator cleanup() deletes all Assignments labelled with this policy.
kubectl delete clustertype aks-large -n $NS
kubectl delete deploymenttarget dt-east dt-west -n $NS
```

---

## 6. Assignment → AssignmentReconciler

**What it does**: Fetches Templates referenced by the ClusterType, renders them with Freemarker using a context built from the ClusterType + DeploymentTarget + ConfigMaps, and creates an `AssignmentPackage`.

**Step 1** — Create two Templates (reconciler and namespace-service):

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Template
metadata:
  name: reconciler-template
  namespace: $NS
spec:
  type: RECONCILER
  manifests:
    - name: reconciler.yaml
      contentType: YAML
      template: |
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: reconciler-${DeploymentTargetName}
        data:
          clusterType: ${ClusterType}
          namespace: ${Namespace}
---
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Template
metadata:
  name: namespace-template
  namespace: $NS
spec:
  type: NAMESPACE
  manifests:
    - name: namespace.yaml
      contentType: YAML
      template: |
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: namespace-${DeploymentTargetName}
        data:
          workspace: ${Workspace}
          environment: ${Environment}
EOF
```

**Step 2** — Create a ClusterType referencing the templates:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: ClusterType
metadata:
  name: aks-large
  namespace: $NS
spec:
  reconciler: reconciler-template
  namespaceService: namespace-template
EOF
```

**Step 3** — Create a DeploymentTarget with environment:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: DeploymentTarget
metadata:
  name: dt-east
  namespace: $NS
  labels:
    workload.scheduler.kalypso.io/workspace: my-workspace
    workload.scheduler.kalypso.io/workload: my-app
spec:
  name: east
  environment: staging
EOF
```

**Step 4** — Create the Assignment (normally created by SchedulingPolicyReconciler, but can be created manually):

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Assignment
metadata:
  name: my-assignment
  namespace: $NS
spec:
  clusterType: aks-large
  deploymentTarget: dt-east
EOF
```

**Check**:
```bash
# AssignmentPackage created with the same name
kubectl get assignmentpackage my-assignment -n $NS

# Verify rendered manifests
kubectl get assignmentpackage my-assignment -n $NS \
  -o jsonpath='{.spec.reconcilerManifests[0]}'
# Must contain "reconciler-dt-east" and clusterType: aks-large

kubectl get assignmentpackage my-assignment -n $NS \
  -o jsonpath='{.spec.namespaceManifests[0]}'
# Must contain workspace: my-workspace and environment: staging

# Assignment status
kubectl get assignment my-assignment -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=PackageRendered
```

**Namespace derivation check**: The `Namespace` context key is `{environment}-{clusterType}-{deploymentTarget}`:

```bash
# Should be "staging-aks-large-dt-east" in the rendered manifest's namespace data
kubectl get assignmentpackage my-assignment -n $NS \
  -o jsonpath='{.spec.reconcilerManifests[0]}' | grep namespace
```

**Cleanup**:
```bash
kubectl delete assignment my-assignment -n $NS
# AssignmentPackage is owned by the Assignment and GC'd automatically.
kubectl delete deploymenttarget dt-east -n $NS
kubectl delete clustertype aks-large -n $NS
kubectl delete template reconciler-template namespace-template -n $NS
```

---

## 7. AssignmentPackage → AssignmentPackageReconciler

**What it does**: Validates manifest syntax. Sets `Ready=True` if all manifests are valid YAML (or non-empty shell), `Ready=False` otherwise. No Kubernetes API calls beyond writing status.

**Test valid YAML**:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: AssignmentPackage
metadata:
  name: pkg-valid
  namespace: $NS
  labels:
    scheduler.kalypso.io/clusterType: aks-large
    scheduler.kalypso.io/deploymentTarget: dt-east
spec:
  reconcilerManifests:
    - |
      apiVersion: v1
      kind: ConfigMap
      metadata:
        name: test
  reconcilerManifestsContentType: YAML
EOF
```

```bash
kubectl get assignmentpackage pkg-valid -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=ManifestsValid
```

**Test invalid YAML**:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: AssignmentPackage
metadata:
  name: pkg-invalid
  namespace: $NS
  labels:
    scheduler.kalypso.io/clusterType: aks-large
    scheduler.kalypso.io/deploymentTarget: dt-east
spec:
  reconcilerManifests:
    - "key: [unclosed bracket"
  reconcilerManifestsContentType: YAML
EOF
```

```bash
kubectl get assignmentpackage pkg-invalid -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=False, reason=InvalidManifests, message mentions "reconcilerManifests[0]"
```

**Test shell content** (no YAML parsing, only non-empty check):

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: AssignmentPackage
metadata:
  name: pkg-sh
  namespace: $NS
  labels:
    scheduler.kalypso.io/clusterType: aks-large
    scheduler.kalypso.io/deploymentTarget: dt-east
spec:
  reconcilerManifests:
    - "#!/bin/bash\necho hello"
  reconcilerManifestsContentType: SH
EOF
```

```bash
kubectl get assignmentpackage pkg-sh -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=ManifestsValid
```

**Cleanup**:
```bash
kubectl delete assignmentpackage pkg-valid pkg-invalid pkg-sh -n $NS
```

---

## 8. GitOpsRepo → GitOpsRepoReconciler

**What it does**: Aggregates all `AssignmentPackage` resources in the namespace and opens a Pull Request in the target GitHub repository with the rendered manifest files. Gates on all `SchedulingPolicy` resources being `Ready=True`.

**Prerequisite**: A GitHub Personal Access Token with repo write access, exported as `GITHUB_TOKEN`. The token must be available to the operator pod.

**Step 1** — Ensure at least one valid `AssignmentPackage` exists (reuse `pkg-valid` from test 7).

**Step 2** — Create the GitOpsRepo:

```bash
kubectl apply -f - <<EOF
apiVersion: scheduler.kalypso.io/v1alpha1
kind: GitOpsRepo
metadata:
  name: my-gitops-repo
  namespace: $NS
spec:
  repo: https://github.com/YOUR-ORG/YOUR-GITOPS-REPO
  branch: kalypso/manifests
  path: manifests/
EOF
```

**Check**:
```bash
kubectl get gitopsrepo my-gitops-repo -n $NS -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=PRCreated  (a PR is opened in the target repo)
# Or Ready=False, reason=NoPackages      (if no AssignmentPackages exist)
# Or Ready=False, reason=PoliciesNotReady (if SchedulingPolicies are not Ready)
```

Check the target GitHub repository — a new branch `kalypso/manifests` and a Pull Request should appear with files at `manifests/{clusterType}/{deploymentTarget}/reconciler.yaml` and `namespace.yaml`.

**Cleanup**:
```bash
kubectl delete gitopsrepo my-gitops-repo -n $NS
```

---

## Full Chain Test

Run all reconcilers in sequence end-to-end:

```bash
NS=kalypso-java

# 1. Templates
kubectl apply -n $NS -f - <<'EOF'
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Template
metadata:
  name: rec-tmpl
spec:
  type: RECONCILER
  manifests:
    - name: reconciler.yaml
      contentType: YAML
      template: |
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: rec-${DeploymentTargetName}
        data:
          clusterType: ${ClusterType}
---
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Template
metadata:
  name: ns-tmpl
spec:
  type: NAMESPACE
  manifests:
    - name: namespace.yaml
      contentType: YAML
      template: |
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: ns-${DeploymentTargetName}
        data:
          workspace: ${Workspace}
EOF

# 2. ClusterType
kubectl apply -n $NS -f - <<'EOF'
apiVersion: scheduler.kalypso.io/v1alpha1
kind: ClusterType
metadata:
  name: aks-prod
spec:
  reconciler: rec-tmpl
  namespaceService: ns-tmpl
EOF

# 3. WorkloadRegistration + Workload → DeploymentTarget
kubectl apply -n $NS -f - <<'EOF'
apiVersion: scheduler.kalypso.io/v1alpha1
kind: WorkloadRegistration
metadata:
  name: my-app
spec:
  workspace: prod-ws
  workload:
    repo: https://github.com/org/my-app
    branch: main
    path: ./
---
apiVersion: scheduler.kalypso.io/v1alpha1
kind: Workload
metadata:
  name: my-app
spec:
  deploymentTargets:
    - name: west
      manifests:
        repo: https://github.com/org/my-app
        branch: main
        path: ./west
EOF

# 4. Wait for DeploymentTarget
echo "Waiting for DeploymentTarget..."
until kubectl get deploymenttarget kalypso-java-my-app-west -n $NS 2>/dev/null; do sleep 2; done
echo "DeploymentTarget created."

# 5. SchedulingPolicy → Assignment
kubectl apply -n $NS -f - <<'EOF'
apiVersion: scheduler.kalypso.io/v1alpha1
kind: SchedulingPolicy
metadata:
  name: my-policy
spec:
  deploymentTargetSelector:
    workspace: prod-ws
EOF

# 6. Wait for Assignment
echo "Waiting for Assignment..."
until kubectl get assignment my-policy-kalypso-java-my-app-west-aks-prod -n $NS 2>/dev/null; do sleep 2; done
echo "Assignment created."

# 7. Wait for AssignmentPackage
echo "Waiting for AssignmentPackage..."
until kubectl get assignmentpackage my-policy-kalypso-java-my-app-west-aks-prod -n $NS 2>/dev/null; do sleep 2; done
echo "AssignmentPackage created."

# 8. Check final condition
kubectl get assignmentpackage my-policy-kalypso-java-my-app-west-aks-prod -n $NS \
  -o jsonpath='{.status.conditions[0]}'
# Expected: Ready=True, reason=ManifestsValid
```

**Cleanup**:
```bash
kubectl delete schedulingpolicy my-policy -n $NS
kubectl delete workload my-app -n $NS
kubectl delete workloadregistration my-app -n $NS
kubectl delete clustertype aks-prod -n $NS
kubectl delete template rec-tmpl ns-tmpl -n $NS
```

---

## Tips

**Watch all Kalypso resources at once**:
```bash
watch kubectl get workloads,deploymenttargets,schedulingpolicies,assignments,assignmentpackages -n kalypso-java
```

**Stream operator logs**:
```bash
kubectl logs -n kalypso-java deployment/kalypso-scheduler -f
```

**Describe a resource for full status**:
```bash
kubectl describe assignmentpackage <name> -n kalypso-java
```

**Check conditions in table form**:
```bash
kubectl get assignmentpackage -n kalypso-java \
  -o custom-columns=NAME:.metadata.name,READY:.status.conditions[0].status,REASON:.status.conditions[0].reason
```
