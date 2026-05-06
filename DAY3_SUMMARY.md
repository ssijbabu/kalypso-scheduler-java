# Day 3 Summary — Environment, WorkloadRegistration & Workload CRDs

## Completed Tasks

### ✅ Shared Utility Class

- [x] `spec/RepositoryReference.java` — embedded POJO with `repo`, `branch`, `path` fields; used by EnvironmentSpec, WorkloadRegistrationSpec, and WorkloadTarget

### ✅ CRD Model Classes

- [x] `Environment` custom resource (`api/v1alpha1/Environment.java`)
- [x] `EnvironmentList` list wrapper
- [x] `EnvironmentSpec` — `controlPlane: RepositoryReference`
- [x] `EnvironmentStatus` — conditions list
- [x] `WorkloadRegistration` custom resource
- [x] `WorkloadRegistrationList` list wrapper
- [x] `WorkloadRegistrationSpec` — `workload: RepositoryReference`, `workspace: String`
- [x] `WorkloadRegistrationStatus` — conditions list
- [x] `Workload` custom resource
- [x] `WorkloadList` list wrapper
- [x] `spec/WorkloadTarget.java` — embedded POJO (not a CRD); fields: `name` (String), `manifests` (RepositoryReference)
- [x] `WorkloadSpec` — `deploymentTargets: List<WorkloadTarget>` (initialized empty, null-safe setter)
- [x] `WorkloadStatus` — conditions list

### ✅ CRD Generation

Three new CRDs generated at compile time:

- `target/classes/META-INF/fabric8/environments.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/workloadregistrations.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/workloads.scheduler.kalypso.io-v1.yml`

### ✅ Unit Tests (5 new per class — 15 new)

**`EnvironmentSerializationTest`** (5 tests):
- Full round-trip with `controlPlane` RepositoryReference
- `controlPlane` omitted when null
- Individual `RepositoryReference` fields survive round-trip
- Partial JSON deserialization
- Null spec fields excluded from JSON

**`WorkloadRegistrationSerializationTest`** (5 tests):
- Full round-trip with `workload` RepositoryReference and `workspace`
- Optional `workspace` omitted when null
- `workload` RepositoryReference fields preserved
- Partial JSON deserialization
- Null `workspace` excluded from JSON

**`WorkloadSerializationTest`** (5 tests):
- Full round-trip with populated `deploymentTargets` list
- Empty `deploymentTargets` list serializes as `[]`
- Null `deploymentTargets` setter stores empty list (null-safety)
- Each `WorkloadTarget` preserves `name` and `manifests`
- Partial JSON deserialization

### ✅ Integration Tests (3 new)

1. `testEnvironmentResourceCrudRoundTrip` — Create + get; `controlPlane.repo/branch/path` survive API server
2. `testWorkloadRegistrationResourceCrudRoundTrip` — Create + get; `workload` RepositoryReference and `workspace` survive
3. `testWorkloadResourceCrudRoundTrip` — Create + get; `deploymentTargets` list with nested RepositoryReference survives

---

## Key Design Decisions

### `RepositoryReference` as a shared embedded POJO

Three CRDs in Day 3 (Environment, WorkloadRegistration, WorkloadTarget) all reference Git repositories with the same three fields (`repo`, `branch`, `path`). Rather than duplicating fields or repeating the pattern, a single `RepositoryReference` POJO is shared across all specs. Being `@JsonInclude(NON_NULL)`, any absent field is cleanly omitted from the CRD JSON.

### `WorkloadTarget` is an embedded spec, not a CRD

`WorkloadSpec.deploymentTargets` is a list of embedded `WorkloadTarget` objects — they describe what `DeploymentTarget` CRs the `WorkloadReconciler` (Day 9) should create, but are not themselves cluster resources. Naming it `WorkloadTarget` instead of `DeploymentTarget` avoids confusion with the `DeploymentTarget` CRD introduced in Day 4.

### Null-safe list setter pattern

All `List<T>` fields use the same null-safety guard established in Day 1:
```java
public void setDeploymentTargets(List<WorkloadTarget> targets) {
    this.deploymentTargets = targets != null ? targets : new ArrayList<>();
}
```

---

## Project Structure After Day 3

```
src/main/java/io/kalypso/scheduler/api/v1alpha1/
├── Environment.java                      # new
├── EnvironmentList.java                  # new
├── WorkloadRegistration.java             # new
├── WorkloadRegistrationList.java         # new
├── Workload.java                         # new
├── WorkloadList.java                     # new
├── spec/
│   ├── RepositoryReference.java          # new — shared embedded POJO
│   ├── EnvironmentSpec.java              # new
│   ├── WorkloadRegistrationSpec.java     # new
│   ├── WorkloadTarget.java               # new — embedded POJO in WorkloadSpec
│   └── WorkloadSpec.java                 # new
└── status/
    ├── EnvironmentStatus.java            # new
    ├── WorkloadRegistrationStatus.java   # new
    └── WorkloadStatus.java               # new

src/test/java/io/kalypso/scheduler/api/v1alpha1/
├── EnvironmentSerializationTest.java     # new — 5 unit tests
├── WorkloadRegistrationSerializationTest.java  # new — 5 unit tests
└── WorkloadSerializationTest.java        # new — 5 unit tests
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

## Ready for Day 4

- [x] Completed as part of Days 3–5 combined implementation — see DAY4_SUMMARY.md

---

**Status**: ✅ Day 3 COMPLETE — Environment, WorkloadRegistration & Workload CRDs deployed and tested

**Next Milestone**: Day 4 — DeploymentTarget & SchedulingPolicy CRDs

---

*Created: 2026-05-06*
*Framework: java-operator-sdk 5.3.2*
*Build Tool: Maven*
*Java Version: 17*
