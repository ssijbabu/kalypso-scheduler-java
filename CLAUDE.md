# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Does

Kalypso Scheduler is a Kubernetes operator (migrated from Go/Kubebuilder to Java/java-operator-sdk) that transforms high-level control-plane abstractions into Kubernetes manifests and delivers them to GitOps repos via Flux and GitHub PRs. The original Go source lives at https://github.com/microsoft/kalypso-scheduler.

## Commands

```bash
# Build
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=BaseRepoReconcilerTest

# Full build + tests
mvn clean verify

# Run locally (requires kubeconfig)
mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"

# Docker
docker build -t kalypso-scheduler:latest .
```

## Architecture

### Framework (PINNED — do not change without explicit approval)
- **java-operator-sdk: 5.3.2**
- fabric8 Kubernetes client: 6.11.0
- Jackson: 2.16.1, Freemarker: 2.3.32, kohsuke/github-api: 1.321

### Dependency Rules
All code must use only the APIs and idioms of the library versions declared in `pom.xml`. **Do not upgrade or downgrade any existing dependency version.** New dependencies may be added if genuinely needed, but existing versions are frozen. Before suggesting an API, verify it exists in the declared version — do not assume newer APIs are available.

### Package Structure
```
io.kalypso.scheduler/
├── KalypsoSchedulerOperator.java    ← main entry point
├── api/v1alpha1/                    ← CRD model classes (Xxx extends CustomResource<Spec,Status>)
├── controllers/                     ← one Reconciler<T> per CRD
│   └── shared/                      ← shared controller utilities
├── services/                        ← business logic (FluxService, TemplateProcessingService, GitHubService)
├── model/                           ← data transfer objects (TemplateContext, etc.)
├── operator/                        ← operator wiring and configuration
└── exception/                       ← custom runtime exceptions
```

### CRDs (group: `scheduler.kalypso.io/v1alpha1`)
Template, ClusterType, ConfigSchema, BaseRepo, Environment, WorkloadRegistration, Workload, DeploymentTarget, SchedulingPolicy, Assignment, AssignmentPackage, GitOpsRepo

### Controllers (8 total)
BaseRepo, Environment, WorkloadRegistration, Workload, SchedulingPolicy, Assignment, AssignmentPackage, GitOpsRepo. Template and ConfigSchema have no dedicated controllers.

### Key Patterns

**Reconciler skeleton:**
```java
@ControllerConfiguration
public class BaseRepoReconciler implements Reconciler<BaseRepo> {
    private final KubernetesClient kubernetesClient;
    private final FluxService fluxService;

    @Override
    public UpdateControl<BaseRepo> reconcile(BaseRepo resource, Context<BaseRepo> context) {
        try {
            // orchestration only — push heavy work to services
            return UpdateControl.noUpdate();
        } catch (Exception e) {
            logger.error("Failed to reconcile BaseRepo: {}", resource.getMetadata().getName(), e);
            return UpdateControl.patchResourceAndRequeue(resource);
        }
    }
}
```

**Go → Java migration mapping:**
| Go | Java |
|---|---|
| `Reconcile(ctx context.Context)` | `reconcile(T resource, Context<T> context)` |
| `ctrl.Result{RequeueAfter: 3s}` | `UpdateControl.patchResourceAndRequeue()` with delay |
| `.Watches()` | `@EventSource` annotations |
| `Map[string]interface{}` | `Map<String, Object>` + Jackson |

**Template processing:** Centralized in `TemplateProcessingService` using Freemarker. Standard template context keys: `DeploymentTargetName`, `Namespace`, `Environment`, `Workspace`, `Workload`, `Labels`, `ClusterType`, `ConfigData`, `Repo`, `Path`, `Branch`.

**Flux resources:** Created/deleted via `FluxService` using fabric8 custom resource API. Use `ownerReferences` and labels for traceability. Namespace, API group, and version must be configurable.

**GitHub PRs:** Implemented in `GitHubService` using kohsuke/github-api. Token provided via env var only. Use deterministic branch naming.

## Mandatory Rules

### Documentation
Every public class and method requires JavaDoc (purpose, params, return, exceptions). Complex logic requires inline comments. **No documentation = no merge.**

### Coding Standards
- No hard-coded values — all configuration via `application.properties` or environment variables
- Use custom exceptions (`TemplateProcessingException`, `ConfigValidationException`, etc.) for operator errors
- Use `Optional<T>` rather than null
- Methods should stay under ~50 lines; keep `reconcile()` orchestration-only
- Reconcile must be **idempotent** — multiple runs must converge to the same state
- Use finalizers for cleanup of external resources (Flux artifacts, Git branches) on deletion
- Use deterministic child resource names derived from owner metadata
- Status subresource for observed state; never write operator state into spec

### Operator Antipatterns to Avoid
- Blocking I/O or slow API calls directly inside `reconcile()`
- Full cluster `list()` without field/label selectors or field indexing
- Swallowing exceptions without setting `status.conditions` (Ready=False)
- Static/global mutable state shared across reconcilers
- Logging secrets or tokens

### Testing
- `@ExtendWith(MockitoExtension.class)` for reconciler unit tests; mock `KubernetesClient` and services
- Method naming: `test<Feature><Scenario><Outcome>()`
- Integration tests (`*IT.java`) deploy the operator to the local Docker Desktop Kubernetes cluster and are **mandatory** — never skip with `-DskipITs`
- CI must pass `mvn clean verify`

### Migration Tracking
Update `MIGRATION_PLAN.md` checklist when completing each day's tasks. Branch names: `day-N-<short-desc>`. PR titles: `[Day N] Feature - brief description`.

After completing each migration day, create a `DAY<N>_SUMMARY.md` file following the same format as `BOOTSTRAP_DAY0_SUMMARY.md` and `DAY1_SUMMARY.md`: completed tasks, key design decisions, issues encountered and resolved, project structure changes, build verification results, and what's next.

## Known Mistakes to Avoid

These are concrete mistakes made during this migration — non-obvious failures specific to this project's library versions and patterns.

### Docker / Image
- **Wrong**: `FROM eclipse-temurin:17-jre-alpine` in Dockerfile.
  **Why**: Alpine image has no ARM64 manifest; fails on Apple Silicon.
  **Correct**: Use `FROM eclipse-temurin:17-jre` (Debian-based, multi-arch).

### java-operator-sdk 5.3.2
- **Wrong**: Calling `operator.installShutdownHook()` with no arguments.
  **Why**: JOSDK 5.3.2 requires a `Duration` argument; zero-arg overload doesn't exist in this version.
  **Correct**: `operator.installShutdownHook(Duration.ofSeconds(30))`.

- **Wrong**: Calling `operator.start()` when `reconcilers()` returns an empty list.
  **Why**: JOSDK 5.3.2 throws `OperatorException: No Controller exists. Exiting!` with no registered controllers.
  **Correct**: Guard with `if (reconcilers.isEmpty()) { /* passive mode — block on Thread.join() */ }` and skip `operator.start()` entirely.

### Maven Build
- **Wrong**: Running `mvn verify` (without `clean`) after editing source files.
  **Why**: Maven's incremental compiler caches stale `.class` files; old bytecode ends up in the shaded JAR. CRD generator also only runs in `process-classes` phase — not triggered by `compile` alone.
  **Correct**: Always use `mvn clean verify` to force full recompilation and CRD regeneration.

### Logging
- **Wrong**: Relying on `src/main/resources/logback.xml` for logging configuration.
  **Why**: The project uses `log4j-slf4j2-impl` + `log4j-core` as the SLF4J backend; Log4j2 silently ignores `logback.xml`. Default Log4j2 level is ERROR, so all `logger.info()` calls are discarded.
  **Correct**: Configure logging in `src/main/resources/log4j2.xml`.

### fabric8 Kubernetes Client 6.x
- **Wrong**: Using `client.resources(...).resource(...).createOrReplace()` in tests or production code.
  **Why**: `createOrReplace()` is deprecated in fabric8 6.x.
  **Correct**: Use `serverSideApply()` instead.

- **Wrong**: Using `CustomResourceList<T>` as the base class for CRD list types.
  **Why**: `CustomResourceList` is deprecated in fabric8 6.x.
  **Correct**: Use `DefaultKubernetesResourceList<T>` from `io.fabric8.kubernetes.api.model`.

- **Wrong**: `assertNotNull(deployment.getStatus().getReadyReplicas())` in tests.
  **Why**: Kubernetes omits `readyReplicas` from the API response when the value is 0; fabric8 returns `null`.
  **Correct**: `int ready = status.getReadyReplicas() != null ? status.getReadyReplicas() : 0;`

### CRD Schema Generation (fabric8 crd-generator-maven-plugin 7.6.1)
- **Wrong**: Using `Map<String, Object>`, `JsonNode`, or plain `Object` for a free-form spec field (e.g. a JSON Schema body) without an annotation.
  **Why**: The CRD generator produces `additionalProperties: {type: "object"}` for all three, which causes the Kubernetes API server to reject non-object values (e.g. the string `"object"` in `{"type": "object"}`) when using server-side apply.
  **Correct**: Annotate the field with `@io.fabric8.crd.generator.annotation.PreserveUnknownFields` (requires `io.fabric8:crd-generator-api:7.6.1` as a `provided`-scope dependency). This generates `x-kubernetes-preserve-unknown-fields: true` in the CRD.

### Integration Tests
- **Wrong**: Fetching pod logs with a single `client.pods().withName(name).getLog()` call immediately after the deployment is ready.
  **Why**: Without a readiness probe, Kubernetes marks the Pod ready as soon as the container process starts — before the JVM has written any log lines.
  **Correct**: Poll with a loop (`pollForLog(podName, expectedLine, timeoutSeconds)`) that retries once per second until the expected line appears or a timeout elapses.

### Flux Integration (FluxService / GenericKubernetesResource)
- **Wrong**: Extending `CustomResource<Spec, Status>` for third-party CRD model classes (e.g. Flux `GitRepository`, `Kustomization`).
  **Why**: The `crd-generator-maven-plugin` scans all `CustomResource` subclasses and generates CRD YAMLs for them. Those YAMLs are applied to the cluster during `pre-integration-test` (`kubectl apply -f META-INF/fabric8/`), potentially overwriting the real Flux CRD definitions with an incomplete schema and breaking Flux.
  **Correct**: Model Flux resources as plain POJOs (Jackson annotations only). Use `GenericKubernetesResource` + `ResourceDefinitionContext` in the service to submit requests to the already-installed Flux CRDs.

- **Wrong**: Using `v1beta2` for Flux `GitRepository` and `Kustomization` resources, or guarding a Flux IT test only on CRD *existence*.
  **Why**: Flux 2.0+ promotes both resource types to `v1` and stops serving `v1beta2`. The CRD still exists, so an existence-only guard passes — but the PATCH call returns `404 Not Found` because the version endpoint is gone.
  **Correct**: Use `v1` as the API version. Guard the IT test by checking the specific version is served: `crd.getSpec().getVersions().stream().anyMatch(v -> "v1".equals(v.getName()) && Boolean.TRUE.equals(v.getServed()))`.

- **Wrong**: Using `@Mock(answer = Answers.RETURNS_DEEP_STUBS) KubernetesClient client` to mock `KubernetesClient` on Java 16+.
  **Why**: Mockito's inline mocker must instrument every class in the interface hierarchy, including `java.io.Closeable` and `java.lang.AutoCloseable` from `java.base`. On Java 16+ (and definitely Java 25) the module system blocks this, causing every test method in the class to fail with `MockitoException: Could not modify all classes`.
  **Correct**: Make the non-trivial logic (resource building) package-private and test it directly without a client mock. Reserve client-interaction verification for integration tests.

### General
- **Wrong**: Saving a project-level rule (e.g. "integration tests must always run") to personal Claude memory.
  **Why**: Personal memory is for user preferences that span projects; project rules belong in `CLAUDE.md` where they are versioned and visible to all contributors.
  **Correct**: Add project-specific rules directly to `CLAUDE.md`.

## Current Status
Day 0 (bootstrap) is complete. Only `TemplateCustomResource` and its Spec/Status classes exist under `api/v1alpha/`. All controllers, services, models, and exceptions are planned but not yet implemented. See `MIGRATION_PLAN.md` for the 14-day roadmap.

## Configuration
All runtime configuration is in `src/main/resources/application.properties`. Logging is configured in `src/main/resources/logback.xml` (io.kalypso=DEBUG, io.javaoperatorsdk=INFO, io.fabric8=WARN). Never add hard-coded values to source — always add a property key.
