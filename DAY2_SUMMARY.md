# Day 2 Summary — ConfigSchema & BaseRepo CRDs

## Completed Tasks

### ✅ CRD Model Classes

- [x] `ConfigSchema` custom resource (`api/v1alpha1/ConfigSchema.java`)
- [x] `ConfigSchemaList` list wrapper (`api/v1alpha1/ConfigSchemaList.java`)
- [x] `ConfigSchemaSpec` with `clusterType` (String) and `schema` (free-form Object) fields
- [x] `ConfigSchemaStatus` with Kubernetes `Condition` list
- [x] `BaseRepo` custom resource (`api/v1alpha1/BaseRepo.java`)
- [x] `BaseRepoList` list wrapper (`api/v1alpha1/BaseRepoList.java`)
- [x] `BaseRepoSpec` with `repo`, `branch`, `path` (required) and `commit` (optional) fields
- [x] `BaseRepoStatus` with Kubernetes `Condition` list

### ✅ CRD Generation

Four CRDs now generated at compile time by `crd-generator-maven-plugin:7.6.1`:

- `target/classes/META-INF/fabric8/configschemas.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/baserepoes.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/clustertypes.scheduler.kalypso.io-v1.yml` (Day 1)
- `target/classes/META-INF/fabric8/templates.scheduler.kalypso.io-v1.yml` (Day 1)

### ✅ Unit Tests (25 passing — 10 new)

**`ConfigSchemaSerializationTest`** (5 tests):
- `ConfigSchemaSpec` round-trips with all fields (clusterType + nested schema)
- `ConfigSchemaSpec` round-trips with clusterType only (null schema)
- Null `schema` field is excluded from serialized JSON (`@JsonInclude(NON_NULL)`)
- Partial JSON deserialization (missing optional fields)
- Nested schema structure preserved (additionalProperties, required array)

**`BaseRepoSerializationTest`** (5 tests):
- `BaseRepoSpec` round-trips with all four fields including optional `commit`
- `BaseRepoSpec` round-trips without `commit` — field remains null
- Null `commit` is excluded from serialized JSON
- Partial JSON deserialization (required fields only)
- SSH-style repo URLs serialize correctly

### ✅ Integration Tests (11 passing — 4 new)

**`OperatorIntegrationIT`** — 4 new tests added:
1. `testConfigSchemaResourceCrudRoundTrip` — Create + get ConfigSchema; nested schema map survives API server round-trip
2. `testConfigSchemaWithNoSchemaBodyRoundTrip` — ConfigSchema with no schema body accepted; null preserved
3. `testBaseRepoResourceCrudRoundTrip` — Create + get BaseRepo with all fields including commit
4. `testBaseRepoWithoutCommitRoundTrip` — BaseRepo without optional commit field; null preserved

### ✅ New Dependency Added

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-api</artifactId>
    <version>7.6.1</version>
    <scope>provided</scope>
</dependency>
```

Compile-time only (excluded from shaded JAR). Required for the `@PreserveUnknownFields` annotation.

---

## Key Design Decisions

### `ConfigSchemaSpec.schema` field type: `Object` + `@PreserveUnknownFields`

The `schema` field must accept arbitrary JSON Schema structures (`type`, `properties`, `required`, `additionalProperties`, etc.). Three field types were evaluated:

| Type | CRD generated schema | Server-side apply result |
|---|---|---|
| `Map<String, Object>` | `additionalProperties: {type: "object"}` | ❌ Rejects string values like `"object"` |
| `JsonNode` | `additionalProperties: {type: "object"}` | ❌ Same problem |
| `Object` + `@PreserveUnknownFields` | `x-kubernetes-preserve-unknown-fields: true` | ✅ Accepts any structure |

The `@PreserveUnknownFields` annotation from `io.fabric8:crd-generator-api:7.6.1` (the same version as the CRD generator Maven plugin) is the correct solution. It adds `x-kubernetes-preserve-unknown-fields: true` to the field's CRD schema, which tells the API server to skip structural schema validation for that field.

### `BaseRepoSpec.commit` is optional (`@JsonInclude(NON_NULL)`)

The `commit` field is optional by design — when absent, the operator tracks the tip of the specified `branch`. `@JsonInclude(NON_NULL)` on the spec class ensures null fields are omitted from serialized JSON, keeping the API resource clean.

### `DefaultKubernetesResourceList` over deprecated `CustomResourceList`

List wrappers use `DefaultKubernetesResourceList<T>` from `io.fabric8.kubernetes.api.model`, consistent with Day 1. `CustomResourceList` is deprecated in fabric8 6.x.

---

## Issues Encountered and Resolved

| # | Issue | Root Cause | Fix |
|---|---|---|---|
| 1 | `ConfigSchemaList` used deprecated `CustomResourceList` | Copy-paste from wrong base class | Changed to `DefaultKubernetesResourceList<ConfigSchema>` |
| 2 | `testConfigSchemaResourceCrudRoundTrip` fails with HTTP 500 | CRD schema generated `additionalProperties: {type: "object"}` — API server rejected string values in schema map | Added `@PreserveUnknownFields` from `crd-generator-api:7.6.1` (provided scope) |
| 3 | `Map<String, Object>` and `JsonNode` both produced same bad CRD schema | fabric8 7.x CRD generator types both nested object values as `type: object` | Used plain `Object` field type with `@PreserveUnknownFields` annotation |
| 4 | Maven incremental compiler didn't pick up annotation change | CRD generator runs in `process-classes` phase, after `compile` | Always use `mvn clean verify` — confirmed necessary |

---

## Project Structure After Day 2

```
src/main/java/io/kalypso/scheduler/
└── api/v1alpha1/
    ├── ConfigSchema.java              # new
    ├── ConfigSchemaList.java          # new
    ├── BaseRepo.java                  # new
    ├── BaseRepoList.java              # new
    ├── spec/
    │   ├── ConfigSchemaSpec.java      # new — Object schema field + @PreserveUnknownFields
    │   └── BaseRepoSpec.java          # new — repo, branch, path, commit fields
    └── status/
        ├── ConfigSchemaStatus.java    # new
        └── BaseRepoStatus.java        # new

src/test/java/io/kalypso/scheduler/
├── api/v1alpha1/
│   ├── ConfigSchemaSerializationTest.java   # new — 5 unit tests
│   └── BaseRepoSerializationTest.java       # new — 5 unit tests
└── it/
    └── OperatorIntegrationIT.java           # updated — 4 new IT tests, 11 total
```

---

## Build Verification

```bash
# Full build + unit + integration tests (mandatory)
mvn clean verify
```

### Test Results

```
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0   ← surefire (unit)
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0   ← failsafe (integration)
[INFO] BUILD SUCCESS
[INFO] Total time: 12.701 s
```

---

## Ready for Day 3

**Day 3 Tasks** (Next):
- [ ] Create `Environment` CRD with `controlPlane` (RepositoryReference) spec
- [ ] Create `WorkloadRegistration` CRD with `workload` (RepositoryReference) and `workspace` fields
- [ ] Create `Workload` CRD with `deploymentTargets` list spec
- [ ] Create shared `RepositoryReference` utility class (used by Environment + WorkloadRegistration)
- [ ] Write unit tests for all three CRD serialization
- [ ] Update integration tests to cover new CRDs
- [ ] Update `MIGRATION_PLAN.md` with Day 3 completion

---

**Status**: ✅ Day 2 COMPLETE — ConfigSchema & BaseRepo CRDs deployed and tested against live cluster

**Next Milestone**: Day 3 — Environment, WorkloadRegistration & Workload CRDs

---

*Created: 2026-05-06*
*Framework: java-operator-sdk 5.3.2*
*Build Tool: Maven*
*Java Version: 17*
