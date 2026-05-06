# Day 1 Summary — Template & ClusterType CRDs

## Completed Tasks

### ✅ CRD Model Classes

- [x] `Template` custom resource (`api/v1alpha1/Template.java`)
- [x] `TemplateList` list wrapper (`api/v1alpha1/TemplateList.java`)
- [x] `TemplateSpec` with type and manifests fields
- [x] `TemplateManifest` with name, template source, and content type
- [x] `TemplateType` enum (`reconciler`, `namespace`, `config`)
- [x] `ContentType` enum (`yaml`, `sh`)
- [x] `TemplateStatus` with Kubernetes `Condition` list
- [x] `ClusterType` custom resource (`api/v1alpha1/ClusterType.java`)
- [x] `ClusterTypeList` list wrapper (`api/v1alpha1/ClusterTypeList.java`)
- [x] `ClusterTypeSpec` with reconciler, namespaceService, configType fields
- [x] `ClusterConfigType` enum (`configmap`, `envfile`)
- [x] `ClusterTypeStatus` with Kubernetes `Condition` list

### ✅ CRD Generation

Fabric8 `crd-generator-maven-plugin:7.6.1` auto-generates CRD YAML at compile time:

- `target/classes/META-INF/fabric8/templates.scheduler.kalypso.io-v1.yml`
- `target/classes/META-INF/fabric8/clustertypes.scheduler.kalypso.io-v1.yml`

Both CRDs are in API group `scheduler.kalypso.io/v1alpha1` and are namespace-scoped.

### ✅ Unit Tests (15 passing)

**`TemplateSerializationTest`** (9 tests):
- `TemplateType` enum values serialize to lowercase JSON strings
- `ContentType` enum values serialize to lowercase JSON strings
- `TemplateManifest` round-trips through Jackson without data loss
- `TemplateSpec` round-trips with all fields
- Null manifest list is replaced with an empty list (null-safety guard)

**`ClusterTypeSerializationTest`** (6 tests):
- `ClusterConfigType` enum values serialize to lowercase JSON strings
- Full `ClusterTypeSpec` round-trip with all fields
- Partial JSON deserialization with only required fields present
- Null fields are excluded from serialized JSON (`@JsonInclude(NON_NULL)`)

### ✅ Integration Test Pipeline

End-to-end `mvn verify` pipeline wired via `pom.xml`:

| Phase | Step | Action |
|---|---|---|
| `pre-integration-test` | docker-build | `docker build -t kalypso-scheduler:latest .` |
| `pre-integration-test` | apply-crds | `kubectl apply -f target/classes/META-INF/fabric8/` |
| `pre-integration-test` | k8s-deploy | `kubectl apply -f k8s/` |
| `pre-integration-test` | k8s-wait | `kubectl rollout status deployment/kalypso-scheduler -n kalypso-java --timeout=2m` |
| `integration-test` | failsafe | Runs `*IT.java` against the live cluster |
| `post-integration-test` | k8s-cleanup | `kubectl delete -f k8s/ --ignore-not-found=true` |

### ✅ Integration Tests (7 passing)

**`OperatorIntegrationIT`** — runs against the live Docker Desktop cluster:
1. `testOperatorDeploymentIsRunning` — Deployment has ≥1 ready replica
2. `testOperatorPodHasNoRestarts` — Pod restart count is 0
3. `testOperatorLogsShowStartupAndNoErrors` — Startup log line appears within 30 s
4. `testTemplateResourceCrudRoundTrip` — Create + get Template, all fields survive round-trip
5. `testTemplateWithMultipleManifestsRoundTrip` — Template with 2 manifests (yaml + sh)
6. `testClusterTypeResourceCrudRoundTrip` — Create + get ClusterType, all fields survive round-trip
7. `testClusterTypeWithEnvfileConfigTypeRoundTrip` — ENVFILE enum serializes correctly; null field absent

### ✅ Kubernetes Deployment Manifests

- [x] `k8s/00-namespace.yaml` — `kalypso-java` namespace
- [x] `k8s/01-rbac.yaml` — ServiceAccount, ClusterRole, ClusterRoleBinding
- [x] `k8s/02-deployment.yaml` — Operator Deployment (`imagePullPolicy: Never` for Docker Desktop)

### ✅ Operator Entry Point Reworked

`KalypsoSchedulerOperator.java` updated with **passive mode**: JOSDK 5.3.2 rejects `operator.start()` when no reconcilers are registered (Days 1–7 have none). When `reconcilers()` returns an empty list, the operator logs a startup confirmation and blocks on `Thread.currentThread().join()` without calling `operator.start()`.

---

## Key Design Decisions

### Enum JSON serialization
All enums use `@JsonProperty("lowercase-value")` on each constant so Jackson serializes them to the lowercase string the Kubernetes API server expects (e.g. `"reconciler"` not `"RECONCILER"`).

### Null-safety on manifests list
`TemplateSpec.setManifests(null)` silently replaces null with an empty `ArrayList` to prevent NPEs during reconciliation.

### `serverSideApply()` over `createOrReplace()`
Integration tests use `client.resources(...).resource(...).serverSideApply()` — the `createOrReplace()` method is deprecated in fabric8 6.x.

### Log4j2 configuration
The project uses `log4j-slf4j2-impl` + `log4j-core` as the SLF4J backend. `logback.xml` is silently ignored by Log4j2. `src/main/resources/log4j2.xml` was created with:
- `io.kalypso` → DEBUG
- `io.javaoperatorsdk` → INFO
- `io.fabric8` → WARN

### Docker base image
`eclipse-temurin:17-jre` (Debian, multi-arch) is used instead of `eclipse-temurin:17-jre-alpine` (AMD64 only) to support Apple Silicon (ARM64) development machines.

### Log polling in integration tests
Without a readiness probe, Kubernetes marks the Pod ready as soon as the container process starts — before the JVM logs anything. The `pollForLog()` helper polls once per second for up to 30 seconds rather than doing a one-shot `getLog()`.

---

## Issues Encountered and Resolved

| # | Issue | Root Cause | Fix |
|---|---|---|---|
| 1 | Docker build fails on ARM64 | `eclipse-temurin:17-jre-alpine` has no ARM64 manifest | Changed base image to `eclipse-temurin:17-jre` |
| 2 | `installShutdownHook()` compile error | JOSDK 5.3.2 requires a `Duration` argument | Added `Duration.ofSeconds(30)` |
| 3 | Stale compiled class in shaded JAR | Maven incremental compiler cached broken bytecode | Always use `mvn clean verify` |
| 4 | `OperatorException: No Controller exists` | JOSDK 5.3.2 hard-fails with no registered controllers | Implemented passive mode — skip `operator.start()` when list is empty |
| 5 | `readyReplicas` null assertion failure | Kubernetes omits the field when count is 0 | Null-safe: `getReadyReplicas() != null ? ... : 0` |
| 6 | Empty pod logs — all INFO discarded | Log4j2 ignores `logback.xml`; default level is ERROR | Created `log4j2.xml` with correct levels |
| 7 | `createOrReplace()` deprecation warning | Method deprecated in fabric8 6.x | Replaced with `serverSideApply()` |
| 8 | Log check fails — pod logs empty at test time | No readiness probe; JVM not fully started yet | Implemented `pollForLog()` with 30-second timeout |

---

## Project Structure After Day 1

```
kalypso-scheduler-java/
├── pom.xml                                        # + failsafe-plugin, exec-plugin (5 executions)
├── Dockerfile                                     # eclipse-temurin:17-jre
├── k8s/
│   ├── 00-namespace.yaml                          # kalypso-java namespace
│   ├── 01-rbac.yaml                               # ServiceAccount + ClusterRole + Binding
│   └── 02-deployment.yaml                         # Operator Deployment
├── src/
│   ├── main/
│   │   ├── java/io/kalypso/scheduler/
│   │   │   ├── KalypsoSchedulerOperator.java      # Passive mode added
│   │   │   └── api/v1alpha1/
│   │   │       ├── Template.java
│   │   │       ├── TemplateList.java
│   │   │       ├── ClusterType.java
│   │   │       ├── ClusterTypeList.java
│   │   │       ├── spec/
│   │   │       │   ├── TemplateSpec.java
│   │   │       │   ├── TemplateManifest.java
│   │   │       │   ├── TemplateType.java
│   │   │       │   ├── ContentType.java
│   │   │       │   ├── ClusterTypeSpec.java
│   │   │       │   └── ClusterConfigType.java
│   │   │       └── status/
│   │   │           ├── TemplateStatus.java
│   │   │           └── ClusterTypeStatus.java
│   │   └── resources/
│   │       └── log4j2.xml                         # Created — replaces ignored logback.xml
│   └── test/
│       └── java/io/kalypso/scheduler/
│           ├── api/v1alpha1/
│           │   ├── TemplateSerializationTest.java  # 9 unit tests
│           │   └── ClusterTypeSerializationTest.java # 6 unit tests
│           └── it/
│               └── OperatorIntegrationIT.java      # 7 integration tests
```

---

## Build Verification

```bash
# Full build + unit + integration tests (mandatory)
mvn clean verify

# Unit tests only (no cluster required)
mvn clean test

# Skip integration tests (only for IDE/quick compile checks)
mvn clean verify -DskipITs
```

### Test Results

```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0   ← surefire (unit)
[INFO] Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0   ← failsafe (integration)
[INFO] BUILD SUCCESS
```

---

## Ready for Day 2

**Day 2 Tasks** (Next):
- [ ] Create `ConfigSchema` CRD class with JSON schema validation spec
- [ ] Create `BaseRepo` CRD class with Git repository spec
- [ ] Create corresponding Spec, Status, and List classes
- [ ] Write unit tests for CRD serialization
- [ ] Update integration tests to cover new CRDs
- [ ] Update `MIGRATION_PLAN.md` with Day 2 completion

---

**Status**: ✅ Day 1 COMPLETE — Template & ClusterType CRDs deployed and tested against live cluster

**Next Milestone**: Day 2 — ConfigSchema & BaseRepo CRDs

---

*Created: 2026-05-06*  
*Framework: java-operator-sdk 5.3.2*  
*Build Tool: Maven*  
*Java Version: 17*
