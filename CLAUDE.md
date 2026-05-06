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

## Current Status
Day 0 (bootstrap) is complete. Only `TemplateCustomResource` and its Spec/Status classes exist under `api/v1alpha/`. All controllers, services, models, and exceptions are planned but not yet implemented. See `MIGRATION_PLAN.md` for the 14-day roadmap.

## Configuration
All runtime configuration is in `src/main/resources/application.properties`. Logging is configured in `src/main/resources/logback.xml` (io.kalypso=DEBUG, io.javaoperatorsdk=INFO, io.fabric8=WARN). Never add hard-coded values to source — always add a property key.
