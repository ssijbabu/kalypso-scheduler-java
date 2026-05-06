# Day 4 Summary — DeploymentTarget & SchedulingPolicy CRDs

## Completed Tasks

### ✅ CRD Model Classes

- [x] `DeploymentTarget` custom resource (`api/v1alpha1/DeploymentTarget.java`)
- [x] `DeploymentTargetList` list wrapper
- [x] `DeploymentTargetSpec` — `name` (String), `labels` (Map<String,String>), `environment` (String), `manifests` (RepositoryReference); label constants `WORKSPACE_LABEL` and `WORKLOAD_LABEL`
- [x] `DeploymentTargetStatus` — conditions list
- [x] `SchedulingPolicy` custom resource (`api/v1alpha1/SchedulingPolicy.java`)
- [x] `SchedulingPolicyList` list wrapper
- [x] `spec/Selector.java` — embedded POJO with `workspace` (String) and `labelSelector` (Map<String,String>)
- [x] `SchedulingPolicySpec` — `deploymentTargetSelector: Selector`, `clusterTypeSelector: Selector`
- [x] `SchedulingPolicyStatus` — conditions list

### ✅ CRD Generation

Two new CRDs generated at compile time:

- `target/classes/META-INF/fabric8/deploymenttargets.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/schedulingpolicies.scheduler.kalypso.io-v1.yml`

### ✅ Unit Tests (5 new per class — 10 new)

**`DeploymentTargetSerializationTest`** (5 tests):
- Full round-trip with all fields including `labels` map and `manifests` RepositoryReference
- Optional `environment` omitted when null
- `labels` map entries survive round-trip
- `WORKSPACE_LABEL` / `WORKLOAD_LABEL` constants have correct string values
- Partial JSON deserialization

**`SchedulingPolicySerializationTest`** (5 tests):
- Full round-trip with both `deploymentTargetSelector` and `clusterTypeSelector`
- `Selector.workspace` and `Selector.labelSelector` survive round-trip
- Null selector fields omitted from JSON
- Empty `labelSelector` map serializes as `{}`
- Partial JSON deserialization with one selector absent

### ✅ Integration Tests (2 new)

1. `testDeploymentTargetResourceCrudRoundTrip` — Create + get; all fields including `labels` map and `manifests` RepositoryReference survive API server round-trip
2. `testSchedulingPolicyResourceCrudRoundTrip` — Create + get; both selectors with `workspace` and `labelSelector` survive

---

## Key Design Decisions

### Label constants on `DeploymentTargetSpec`

`WORKSPACE_LABEL` and `WORKLOAD_LABEL` are `public static final String` constants on `DeploymentTargetSpec` (not in a separate constants class) because they are semantically tied to the `DeploymentTarget` schema. The `WorkloadReconciler` (Day 9) will use these constants when setting labels on generated `DeploymentTarget` CRs.

```java
public static final String WORKSPACE_LABEL = "workload.scheduler.kalypso.io/workspace";
public static final String WORKLOAD_LABEL   = "workload.scheduler.kalypso.io/workload";
```

### `Selector` as a standalone embedded POJO

`SchedulingPolicySpec` uses `Selector` in two fields (`deploymentTargetSelector`, `clusterTypeSelector`). `Selector` is its own class in the `spec/` package rather than an inner class so it can be reused if future CRDs need the same selector pattern.

`labelSelector` in `Selector` is `Map<String, String>` (matchLabels-style), which is the common Kubernetes pattern for simple label matching. A full `LabelSelector` object with `matchExpressions` is not needed at this stage.

---

## Project Structure After Day 4

```
src/main/java/io/kalypso/scheduler/api/v1alpha1/
├── DeploymentTarget.java                 # new
├── DeploymentTargetList.java             # new
├── SchedulingPolicy.java                 # new
├── SchedulingPolicyList.java             # new
├── spec/
│   ├── DeploymentTargetSpec.java         # new — WORKSPACE_LABEL, WORKLOAD_LABEL constants
│   ├── Selector.java                     # new — embedded POJO for SchedulingPolicySpec
│   └── SchedulingPolicySpec.java         # new
└── status/
    ├── DeploymentTargetStatus.java       # new
    └── SchedulingPolicyStatus.java       # new

src/test/java/io/kalypso/scheduler/api/v1alpha1/
├── DeploymentTargetSerializationTest.java   # new — 5 unit tests
└── SchedulingPolicySerializationTest.java   # new — 5 unit tests
```

---

## Build Verification

```bash
mvn clean verify
```

```
[INFO] Tests run: 65, Failures: 0, Errors: 0, Skipped: 0   ← surefire (unit, cumulative)
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0   ← failsafe (integration, cumulative)
[INFO] BUILD SUCCESS
[INFO] Total time: ~13 s
```

---

## Ready for Day 5

- [x] Completed as part of Days 3–5 combined implementation — see DAY5_SUMMARY.md

---

**Status**: ✅ Day 4 COMPLETE — DeploymentTarget & SchedulingPolicy CRDs deployed and tested

**Next Milestone**: Day 5 — Assignment, AssignmentPackage & GitOpsRepo CRDs

---

*Created: 2026-05-06*
*Framework: java-operator-sdk 5.3.2*
*Build Tool: Maven*
*Java Version: 17*
