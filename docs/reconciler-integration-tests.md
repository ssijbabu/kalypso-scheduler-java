# Reconciler Integration Tests

## Overview

`ReconcilerIntegrationIT` verifies end-to-end reconciler behaviour against a live Kubernetes cluster — the **Docker Desktop** cluster, which the pre-integration-test Maven phase deploys the operator to.

These tests are in `src/test/java/io/kalypso/scheduler/it/` and run during `mvn verify` (failsafe phase). They complement the 187 unit tests by confirming that reconcilers create, update, and delete child resources as expected in a real cluster.

## What Is Tested

| Test (in `@Order` sequence) | Reconciler(s) | What It Verifies |
|---|---|---|
| `testWorkloadReconcilerCreatesDeploymentTargetWithCorrectLabels` | WorkloadReconciler | DT name `{ns}-{wl}-{target}`, workspace + workload labels |
| `testWorkloadReconcilerSetsReadyCondition` | WorkloadReconciler | `Ready=True` set on Workload status |
| `testWorkloadReconcilerDeletesRemovedDeploymentTarget` | WorkloadReconciler | Stale DT deleted when removed from spec |
| `testSchedulingPolicyReconcilerCreatesAssignmentForMatchingPair` | SchedulingPolicyReconciler | Assignment `{pol}-{dt}-{ct}`, `schedulingPolicy` label |
| `testSchedulingPolicyReconcilerSetsReadyCondition` | SchedulingPolicyReconciler | `Ready=True` set on SchedulingPolicy status |
| `testAssignmentPackageReconcilerSetsReadyTrueForValidManifests` | AssignmentPackageReconciler | `Ready=True` for syntactically valid YAML |
| `testAssignmentPackageReconcilerSetsReadyFalseForInvalidManifests` | AssignmentPackageReconciler | `Ready=False` for unclosed bracket YAML |
| `testEndToEndChainFromWorkloadToAssignmentPackage` | WorkloadReconciler → SchedulingPolicyReconciler → AssignmentReconciler → AssignmentPackageReconciler | Full chain: WR → Workload → DT → SP → Assignment → Package → Ready=True, manifest content |

## Pre-conditions

The Maven pre-integration-test phase handles all setup automatically:

```
docker build -t kalypso-scheduler:latest .
kubectl apply -f target/classes/META-INF/fabric8/   # CRDs
kubectl apply -f k8s/                                # namespace, RBAC, deployment
kubectl rollout status deployment/kalypso-scheduler -n kalypso-java --timeout=2m
```

Post-integration-test tears down the k8s resources but leaves CRDs intact.

## Polling Pattern

All IT tests use `pollForResource`, `pollForAbsence`, and `pollForCondition` — local polling loops (1 s interval) that check the API server until a condition is met or a timeout expires. This avoids flakiness from clock jitter.

```java
private <T, L> T pollForResource(Class<T> type, Class<L> listType,
                                  String name, int timeoutSeconds) {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
    while (System.currentTimeMillis() < deadline) {
        T resource = client.resources(type, listType).inNamespace(NS).withName(name).get();
        if (resource != null) return resource;
        sleep(1_000);
    }
    return null;
}
```

Default timeouts: **30 s** for most assertions, **60 s** for `AssignmentPackage` creation (requires template rendering).

## Test Ordering

Tests use `@TestInstance(PER_CLASS)` + `@TestMethodOrder(OrderAnnotation.class)` because tests 1–3 share Workload state:

- Test 1 creates `Workload r-wl` and `WorkloadRegistration r-wl`.
- Test 2 reads `r-wl` status (depends on test 1).
- Test 3 updates `r-wl` to zero targets and expects the DT to be deleted.

Tests 4–8 create their own isolated fixtures and do not depend on tests 1–3.

## End-to-End Template Format

The e2e test pre-creates two `Template` CRs with simple Freemarker content:

| Template | Type | Content |
|---|---|---|
| `r-e2e-rt` | RECONCILER | `apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: r-${DeploymentTargetName}\ndata:\n  clusterType: ${ClusterType}\n` |
| `r-e2e-nt` | NAMESPACE | `apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: n-${DeploymentTargetName}\ndata:\n  workspace: ${Workspace}\n` |

These render to valid YAML ConfigMaps, letting `AssignmentReconciler` complete without a real Flux or GitHub dependency.

## SSA and managedFields

When a resource retrieved with `.get()` is modified and then re-submitted via `serverSideApply()`, Kubernetes rejects the request unless `metadata.managedFields` is cleared first:

```java
wl.getMetadata().setManagedFields(null);
client.resources(Workload.class, WorkloadList.class)
        .inNamespace(NS).resource(wl).serverSideApply();
```

## Known Dependency: fabric8 7.6.1

JOSDK 5.3.2 was compiled against fabric8 **7.6.1** (not 6.x). At runtime, `ResourceOperations.addFinalizerWithSSA` calls `HasMetadata.initNameAndNamespaceFrom()` which was introduced in fabric8 7.x. Using fabric8 6.x causes `NoSuchMethodError` silently killing all reconciler threads.

`pom.xml` therefore pins `kubernetes.client.version=7.6.1` to match the JOSDK compile-time requirement.

## Running the Tests

```bash
# Full build + unit tests + integration tests
mvn clean verify

# Skip integration tests (unit tests only)
mvn test

# Integration tests only (operator must already be deployed)
mvn failsafe:integration-test
```

## Namespace and Resource Names

All IT resources live in `kalypso-java`. Resource names use the `r-` prefix to distinguish IT fixtures from any pre-existing operator state:

| Constant | Value | Description |
|---|---|---|
| `WL_DT` | `kalypso-java-r-wl-east` | DT created by WorkloadReconciler |
| `SP_ASSIGN` | `r-sp-pol-r-sp-dt-r-sp-ct` | Assignment created by SchedulingPolicyReconciler |
| `E2E_ASSIGN` | `r-e2e-pol-kalypso-java-r-e2e-east-r-e2e-ct` | Assignment + Package in e2e chain |
