# Day 5 Summary — Assignment, AssignmentPackage & GitOpsRepo CRDs

## Completed Tasks

### ✅ CRD Model Classes

- [x] `Assignment` custom resource (`api/v1alpha1/Assignment.java`)
- [x] `AssignmentList` list wrapper
- [x] `AssignmentSpec` — `clusterType: String`, `deploymentTarget: String`
- [x] `AssignmentStatus` — conditions list
- [x] `AssignmentPackage` custom resource (`api/v1alpha1/AssignmentPackage.java`)
- [x] `AssignmentPackageList` list wrapper
- [x] `AssignmentPackageSpec` — six fields (three manifest lists + three content-type enums); label constants `CLUSTER_TYPE_LABEL` and `DEPLOYMENT_TARGET_LABEL`
- [x] `AssignmentPackageStatus` — conditions list
- [x] `GitOpsRepo` custom resource (`api/v1alpha1/GitOpsRepo.java`)
- [x] `GitOpsRepoList` list wrapper
- [x] `GitOpsRepoSpec` — `repo: String`, `branch: String`, `path: String`
- [x] `GitOpsRepoStatus` — conditions list

### ✅ CRD Generation

Three new CRDs generated at compile time:

- `target/classes/META-INF/fabric8/assignments.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/assignmentpackages.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/gitopsrepos.scheduler.kalypso.io-v1.yml`

Total CRDs generated across all days: **10** (all 12 planned CRDs minus the 2 — Template and ConfigSchema — which were already in place from Days 1–2).

### ✅ Unit Tests (5 new per class — 15 new)

**`AssignmentSerializationTest`** (5 tests):
- Full round-trip with `clusterType` and `deploymentTarget`
- Null fields excluded from JSON
- Partial JSON deserialization
- Empty spec round-trips without error
- Both fields independently serializable

**`AssignmentPackageSerializationTest`** (5 tests):
- Full round-trip with all six fields populated
- Empty manifest lists serialize as `[]`, not omitted
- `ContentType` enum values serialize to lowercase (`"yaml"`, `"sh"`)
- `CLUSTER_TYPE_LABEL` / `DEPLOYMENT_TARGET_LABEL` constants have correct string values
- Null content-type fields excluded from JSON

**`GitOpsRepoSerializationTest`** (5 tests):
- Full round-trip with `repo`, `branch`, `path`
- Null fields excluded from JSON
- Partial JSON deserialization
- HTTPS and SSH repo URLs both serialize correctly
- Path field preserves leading `./`

### ✅ Integration Tests (3 new — 19 total)

1. `testAssignmentResourceCrudRoundTrip` — Create + get; `clusterType` and `deploymentTarget` survive API server round-trip
2. `testAssignmentPackageResourceCrudRoundTrip` — Create + get; manifest lists and `ContentType` enum values survive round-trip
3. `testGitOpsRepoResourceCrudRoundTrip` — Create + get; all three Git coordinate fields survive

---

## Key Design Decisions

### `AssignmentPackageSpec` reuses `ContentType` enum from Day 1

The content-type fields (`reconcilerManifestsContentType`, `namespaceManifestsContentType`, `configManifestsContentType`) use the same `ContentType` enum (`YAML`/`SH`) defined in Day 1, rather than plain `String`. This ensures consistent serialization (`"yaml"`, `"sh"`) and compile-time safety when the `AssignmentPackageReconciler` (Day 12) processes manifests.

### Label constants on `AssignmentPackageSpec`

`CLUSTER_TYPE_LABEL` and `DEPLOYMENT_TARGET_LABEL` are `public static final String` constants on `AssignmentPackageSpec`, consistent with how `DeploymentTargetSpec` holds its label constants (Day 4).

```java
public static final String CLUSTER_TYPE_LABEL      = "scheduler.kalypso.io/clusterType";
public static final String DEPLOYMENT_TARGET_LABEL = "scheduler.kalypso.io/deploymentTarget";
```

### All 12 CRDs now complete

With Day 5, the full CRD inventory is in place. All 12 CRDs from the Go operator now have Java model classes, generated CRD YAMLs, unit tests, and integration tests.

---

## All 12 CRDs — Complete Inventory

| CRD | Short Name | Day | Controller (planned) |
|---|---|---|---|
| Template | `tmpl` | 1 | none |
| ClusterType | `ct` | 1 | none |
| ConfigSchema | `cschema` | 2 | none |
| BaseRepo | `br` | 2 | Day 8 |
| Environment | `env` | 3 | Day 8 |
| WorkloadRegistration | `wreg` | 3 | Day 9 |
| Workload | `wl` | 3 | Day 9 |
| DeploymentTarget | `dt` | 4 | (created by WorkloadReconciler) |
| SchedulingPolicy | `sp` | 4 | Day 10 |
| Assignment | `asgn` | 5 | Day 11 |
| AssignmentPackage | `apkg` | 5 | Day 12 |
| GitOpsRepo | `gor` | 5 | Days 12–13 |

---

## Project Structure After Day 5

```
src/main/java/io/kalypso/scheduler/api/v1alpha1/
├── Assignment.java                       # new
├── AssignmentList.java                   # new
├── AssignmentPackage.java                # new
├── AssignmentPackageList.java            # new
├── GitOpsRepo.java                       # new
├── GitOpsRepoList.java                   # new
├── spec/
│   ├── AssignmentSpec.java               # new
│   ├── AssignmentPackageSpec.java        # new — CLUSTER_TYPE_LABEL, DEPLOYMENT_TARGET_LABEL
│   └── GitOpsRepoSpec.java               # new
└── status/
    ├── AssignmentStatus.java             # new
    ├── AssignmentPackageStatus.java      # new
    └── GitOpsRepoStatus.java             # new

src/test/java/io/kalypso/scheduler/api/v1alpha1/
├── AssignmentSerializationTest.java      # new — 5 unit tests
├── AssignmentPackageSerializationTest.java  # new — 5 unit tests
└── GitOpsRepoSerializationTest.java      # new — 5 unit tests
```

---

## Build Verification

```bash
mvn clean verify
```

```
[INFO] Tests run: 65, Failures: 0, Errors: 0, Skipped: 0   ← surefire (unit)
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0   ← failsafe (integration)
[INFO] BUILD SUCCESS
[INFO] Total time: ~13 s
```

### Cumulative test count (all days)

| Day | New unit tests | New IT tests | Running total (unit / IT) |
|---|---|---|---|
| 0 | 0 | 0 | 0 / 0 |
| 1 | 15 | 7 | 15 / 7 |
| 2 | 10 | 4 | 25 / 11 |
| 3–5 | 40 | 8 | 65 / 19 |

---

## Ready for Day 6

**Day 6 Tasks** (Next):
- [ ] Create `FluxService` in `services/` package
- [ ] Model Flux `GitRepository` (source.toolkit.fluxcd.io/v1beta2) using fabric8 custom resource API
- [ ] Model Flux `Kustomization` (kustomize.toolkit.fluxcd.io/v1beta2) using fabric8 custom resource API
- [ ] Implement `createFluxReferenceResources(name, namespace, url, branch, path)` method
- [ ] Implement `deleteFluxReferenceResources(name, namespace)` method
- [ ] Unit-test `FluxService` with mocked `KubernetesClient`
- [ ] Update `MIGRATION_PLAN.md` with Day 6 completion
- [ ] Run `/add-summary` to create DAY6_SUMMARY.md

---

**Status**: ✅ Day 5 COMPLETE — All 12 CRDs implemented, deployed, and tested against live cluster

**Next Milestone**: Day 6 — FluxService (Flux GitRepository & Kustomization integration)

---

*Created: 2026-05-06*
*Framework: java-operator-sdk 5.3.2*
*Build Tool: Maven*
*Java Version: 17*
