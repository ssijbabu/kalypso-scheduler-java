# Kalypso Scheduler Java Operator - Combined Agent Instructions

This file is the authoritative instruction set for AI agents (Copilot) and contributors implementing the Kalypso Scheduler operator in Java using java-operator-sdk (jdk-operator-sdk). Follow these rules for every change. Non‑compliance will delay reviews and merges.

**Version**: java-operator-sdk 5.3.2 (PINNED)  
**Java**: 17+  
**Build**: Maven

---

## TABLE OF CONTENTS

1. [Overview](#overview)
2. [Scope & Purpose](#scope--purpose)
3. [Absolute Requirements](#absolute-requirements)
4. [Framework & Technology Stack](#framework--technology-stack)
5. [Project Layout & Structure](#project-layout--structure)
6. [Coding & Documentation Rules](#coding--documentation-rules)
7. [Naming Conventions & Code Standards](#naming-conventions--code-standards)
8. [Controller Development (java-operator-sdk Patterns)](#controller-development-java-operator-sdk-patterns)
9. [CRD Model Rules](#crd-model-rules)
10. [Template Processing](#template-processing)
11. [Flux Integration](#flux-integration)
12. [GitHub / PR Creation](#github--pr-creation)
13. [Validation & Schema](#validation--schema)
14. [Configuration & Property Management](#configuration--property-management)
15. [Error Handling & Logging](#error-handling--logging)
16. [Testing & CI](#testing--ci)
17. [Kubernetes Operator Best Practices](#kubernetes-operator-best-practices)
18. [Common Antipatterns (DO NOT DO THESE)](#common-antipatterns-do-not-do-these)
19. [Migration Status Tracking](#migration-status-tracking)
20. [Build & Deployment](#build--deployment)
21. [Performance & Security Considerations](#performance--security-considerations)
22. [Branching & PR Rules](#branching--pr-rules)
23. [PR Review Checklist](#pr-review-checklist)
24. [Communication & PR Guidelines](#communication--pr-guidelines)
25. [Troubleshooting & Debugging](#troubleshooting--debugging)
26. [Go ↔ Java Migration Reference](#go--java-migration-reference)
27. [Quick Reference Checklist](#quick-reference-checklist)
28. [Key Contacts & Resources](#key-contacts--resources)
29. [Governance](#governance)

---

## OVERVIEW

This repository contains the Java implementation of the Kalypso Scheduler Operator, migrated from the original Go implementation. The migration uses **java-operator-sdk 5.3.2** and follows the detailed plan in `MIGRATION_PLAN.md`.

**Original Go Repository**: https://github.com/microsoft/kalypso-scheduler

---

## SCOPE & PURPOSE

This repository implements Kalypso Scheduler — an operator that transforms high-level control-plane abstractions (Template, Workload, SchedulingPolicy, etc.) into manifests and delivers them to GitOps repos using Flux and GitHub PRs. All agent-contributed code must follow the guidance below.

---

## ABSOLUTE REQUIREMENTS

- **java-operator-sdk**: **5.3.2** (DO NOT change without RFC & explicit approval)
- **Java**: 17+; Maven as build tool
- **No secrets committed**: Use environment variables / Kubernetes Secrets
- **Testing**: All non-trivial code changes must include tests (unit + reconciler integration as applicable)
- **Documentation**: Every public class/method must have JavaDoc. Complex logic must have inline comments
- **No hard-coded values**: Use `application.properties` or env vars exclusively

---

## FRAMEWORK & TECHNOLOGY STACK

### Required Framework
- `io.javaoperatorsdk:operator-sdk:5.3.2`

### Mandatory Library Versions
- java-operator-sdk: **5.3.2** (DO NOT change)
- Kubernetes Client: 6.11.0+
- Jackson: 2.16.1+
- Freemarker: 2.3.32+
- slf4j: 2.0.11+
- JUnit: 5.10.0+
- Mockito: 5.7.0+

### Technology Standards
- **Language**: Java 17+
- **Build Tool**: Maven
- **Testing Framework**: JUnit 5 + Mockito
- **Logging**: SLF4J with Logback
- **YAML Processing**: Jackson YAML
- **Template Engine**: Freemarker
- **GitHub API**: kohsuke/github

**Note**: Update `pom.xml` if you need to add new dependencies, but do NOT change core framework versions without explicit approval.

---

## PROJECT LAYOUT & STRUCTURE

### Required Directory Structure

Base package: `io.kalypso.scheduler`

```
kalypso-scheduler-java/
├── src/main/java/io/kalypso/scheduler/
│   ├── KalypsoSchedulerOperator.java          (main entry point)
│   ├── crds/                                   (CRD models)
│   │   ├── Template.java
│   │   ├── ClusterType.java
│   │   ├── ...
│   │   └── spec/                              (Spec classes)
│   ├── controllers/                            (Reconcilers)
│   │   ├── BaseRepoReconciler.java
│   │   ├── EnvironmentReconciler.java
│   │   ├── ...
│   │   └── shared/                             (shared controller utilities)
│   ├── operator/                               (operator configuration)
│   │   ├── OperatorConfiguration.java
│   │   └── KubernetesClientProvider.java
│   ├── services/                               (business logic)
│   │   ├── FluxService.java
│   │   ├── TemplateProcessingService.java
│   │   ├── GitHubService.java
│   │   └── ...
│   ├── model/                                  (utility classes)
│   │   ├── TemplateContext.java
│   │   ├── TemplateProcessingResult.java
│   │   └── ...
│   └── exception/                              (custom exceptions)
│       ├── TemplateProcessingException.java
│       ├── ConfigValidationException.java
│       └── ...
├── src/main/resources/
│   ├── application.properties
│   └── logback.xml
├── src/test/java/io/kalypso/scheduler/
│   ├── controllers/
│   ├── services/
│   └── ...
└── pom.xml
```

### CRD Package Organization
- **CRDs**: `io.kalypso.scheduler.api.v1alpha1`
  - `Xxx.java` extends `CustomResource<Spec, Status>`
  - `XxxList.java`
- **Reconcilers**: `io.kalypso.scheduler.controllers` — one `Reconciler<T>` per CRD
- **Services/business logic**: `io.kalypso.scheduler.services`
- **Models/utilities**: `io.kalypso.scheduler.model`
- **Operator wiring/config**: `io.kalypso.scheduler.operator`
- **Exceptions**: `io.kalypso.scheduler.exception`
- **Tests**: Mirror packages under `src/test/java` (JUnit 5 + Mockito)

---

## CODING & DOCUMENTATION RULES

### MANDATORY Documentation Requirements ⚠️

**Every code change, feature, or implementation must include comprehensive documentation.**

#### JavaDoc Comments
All public classes, methods, and interfaces must have JavaDoc comments explaining:
- Purpose and responsibility
- Parameters and return values
- Exceptions that may be thrown
- Example usage when applicable

#### Inline Comments
Complex logic must include clear inline comments explaining:
- What the code does
- Why it's implemented that way
- Any non-obvious design decisions

#### README/Documentation Files
- Update relevant documentation files when adding new components
- Add architecture diagrams when introducing new subsystems
- Document configuration options in `application.properties`
- include mermaid diagram in the documentation.

#### Code Comments Format (REQUIRED)
```java
/**
 * Reconciles the Assignment CRD to generate AssignmentPackage manifests.
 *
 * This reconciler performs the following steps:
 * 1. Fetches the referenced ClusterType, DeploymentTarget, and Templates
 * 2. Gathers configuration data from ConfigMaps
 * 3. Validates configuration against ConfigSchema
 * 4. Renders templates with context data
 * 5. Creates/Updates the AssignmentPackage resource
 *
 * @param assignment The Assignment resource being reconciled
 * @param context The reconciliation context
 * @return UpdateControl with the reconciliation result
 * @throws Exception if template rendering or validation fails
 */
public UpdateControl<Assignment> reconcile(Assignment assignment, Context<Assignment> context) {
    // Implementation with clear inline comments
}
```

#### Additional Documentation Guidelines
- Add a one-line summary header in commits and a short paragraph explaining rationale and test evidence
- If code is ported/derived from elsewhere, add a single-line "Ported-from: <file/URL/line>" reference

**CRITICAL**: No documentation = No approval for merge

---

## NAMING CONVENTIONS & CODE STANDARDS

### Naming Conventions
- **Classes**: PascalCase (e.g., `BaseRepoReconciler`)
- **Methods**: camelCase (e.g., `reconcile()`, `getConfigData()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_FLUX_NAMESPACE`)
- **Variables**: camelCase (e.g., `baseRepo`, `clusterType`)

### Code Quality Standards
- **Single Responsibility**: One class, one purpose
- **Method Length**: Keep methods focused and testable (ideally < 50 lines)
- **Exception Handling**: Use custom exceptions for operator-specific errors
- **Null Safety**: Use `Optional<T>` where appropriate, avoid null when possible
- **Immutability**: Use final fields and immutable collections where safe
- **Constructor-Based Injection**: Prefer constructor injection for dependencies

### Example Pattern - BaseRepoReconciler
```java
@ControllerConfiguration
public class BaseRepoReconciler implements Reconciler<BaseRepo> {

    private final KubernetesClient kubernetesClient;
    private final FluxService fluxService;
    private final Logger logger;

    /**
     * Constructs BaseRepoReconciler with dependencies.
     *
     * @param kubernetesClient The Kubernetes API client
     * @param fluxService Service for managing Flux resources
     */
    public BaseRepoReconciler(KubernetesClient kubernetesClient, FluxService fluxService) {
        this.kubernetesClient = kubernetesClient;
        this.fluxService = fluxService;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Reconciles a BaseRepo resource by creating Flux resources.
     *
     * @param baseRepo The resource being reconciled
     * @param context The reconciliation context
     * @return UpdateControl indicating success or retry
     */
    @Override
    public UpdateControl<BaseRepo> reconcile(BaseRepo baseRepo, Context<BaseRepo> context) {
        try {
            // Implementation
            return UpdateControl.noUpdate();
        } catch (Exception e) {
            logger.error("Failed to reconcile BaseRepo", e);
            return UpdateControl.patchResourceAndRequeue(baseRepo);
        }
    }
}
```

---

## CONTROLLER DEVELOPMENT (java-operator-sdk Patterns)

### Implementation Requirements
- Implement `io.javaoperatorsdk.operator.api.reconciler.Reconciler<T>` and register the controller (annotation or programmatic registration)
- Use constructor-based dependency injection of `KubernetesClient` and services
- Always use the status subresource for conditions; do not store transient state in spec or annotations unless deliberate and documented

### UpdateControl Return Patterns
Return `UpdateControl<T>` or `DeleteControl` appropriately:
- `UpdateControl.noUpdate()` — nothing changed
- `UpdateControl.updateStatus()` or `UpdateControl.patchResourceAndRequeue(T)` — when status or spec changes require update or requeue
- `UpdateControl.updateResourceAndRequeue()` with delay for scheduled retries
- `DeleteControl.defaultDelete()` or `DeleteControl.noFinalizerNeeded()` for cleanup

### Advanced Patterns
- **EventSources and EventSourceManager**: Use for external watchers; avoid ad-hoc polling in reconcile
- **Field Indexing**: Register indexes for fields frequently used in `List()` queries to avoid full-list scans
- **Finalizers**: Guarantee cleanup of external resources (Flux artifacts, Git branches) on deletion
- **Async Patterns**: Avoid long synchronous work in reconcile — use async patterns or offload background tasks and requeue

---

## CRD MODEL RULES

- **Group/Version/Kind**: Maintain `scheduler.kalypso.io/v1alpha1`
- **Jackson/Fabric8 Annotations**: Use to match existing JSON field names and shapes
- **Spec and Status Classes**: Provide both and set `status` as subresource in CRD YAML
- **Validation Annotations/Comments**: Include where useful (min length, enum, required)
- **Stability Policy**: Keep CRD schemas stable across releases; any breaking change requires version policy (v1alpha1 → v1beta1) and documentation

---

## TEMPLATE PROCESSING

### Template Engine
- **Primary Engine**: Freemarker (recommended)
- **Centralization**: Centralize templating in `TemplateProcessingService`

### Standard Template Functions
Provide standard functions to templates:
- `toYaml(Object)` — Convert object to YAML
- `stringify(Object)` — Convert to string
- `hash(Object)` — Generate hash
- `unquote(String)` — Remove surrounding quotes

### Standard Template Context
Define standard template context with fields:
- `DeploymentTargetName` — Target name
- `Namespace` — Target namespace
- `Environment` — Environment identifier
- `Workspace` — Workspace name
- `Workload` — Workload specification
- `Labels` — `Map<String,String>`
- `ClusterType` — Cluster type specification
- `ConfigData` — `Map<String,Object>`
- `Repo` — Repository reference
- `Path` — Path within repository
- `Branch` — Git branch name

### Testing Requirements
- Unit-test templates with input fixtures + expected outputs
- Include negative tests for missing keys and type mismatches
- Test template rendering in isolation from reconcilers

---

## FLUX INTEGRATION

### Implementation Approach
- Implement `FluxService` to create/delete Flux CRDs (GitRepository, Kustomization)
- Prefer fabric8 models if available; otherwise define minimal Jackson POJOs for Flux CRDs and post via the Fabric8 `KubernetesClient.customResource(...)`
- Make flux namespace, repo secret name, and API group/version **configurable**

### Resource Management
- Use ownerReferences and labels where applicable so resources are traceable to the originating CR
- Configure Flux resources via `application.properties` for maximum flexibility

---

## GITHUB / PR CREATION

### Implementation Requirements
- Implement `GitHubService` using a stable library (e.g., org.kohsuke:github-api) with minimal scopes for tokens
- Use **deterministic branch naming** and directory layout for PR content
- Make all GitHub tokens/config provided via **secrets/env vars** and **documented in README/CI**

### Error Handling & Recovery
- Add **retry/backoff** and **rate-limit handling** for GitHub operations
- Surface errors in resource status with clear remediation steps
- Never commit tokens; use environment variables

---

## VALIDATION & SCHEMA

- Use JSON Schema validation (everit or similar) for config validation against `ConfigSchema` CRDs
- Validation errors must be reflected as status conditions (Ready=False) with clear messages and remediation steps
- Provide user-friendly error messages that describe the issue and how to fix it

---

## CONFIGURATION & PROPERTY MANAGEMENT

- **All configuration must be externalizable** via `application.properties`
- **Hard-coded values are NOT allowed** — use environment variables as fallback
- Use `@ConfigurationProperties` or `@Value` property injection for configuration
- Document configuration options in comments

### Example Configuration Pattern
```java
@Component
public class FluxConfiguration {
    
    @Value("${flux.default-namespace:flux-system}")
    private String defaultNamespace;
    
    @Value("${flux.git-repository-version:v1beta2}")
    private String gitRepositoryVersion;
    
    /**
     * Gets the default Flux namespace.
     *
     * @return The namespace where Flux resources are created
     */
    public String getDefaultNamespace() {
        return defaultNamespace;
    }
    
    /**
     * Gets the Flux GitRepository API version.
     *
     * @return The API version (e.g., v1beta2)
     */
    public String getGitRepositoryVersion() {
        return gitRepositoryVersion;
    }
}
```

---

## ERROR HANDLING & LOGGING

### Logging Levels
- **ERROR**: Reconciliation failures, critical issues that require user attention
- **WARN**: Retries, resource not found, non-critical errors that might resolve
- **INFO**: Major reconciliation events, status updates, significant milestones
- **DEBUG**: Detailed step-by-step execution (enabled for development/troubleshooting)

### Example Logging Pattern
```java
logger.info("Starting reconciliation for BaseRepo: {}", baseRepo.getMetadata().getName());

try {
    // Do work
    logger.debug("Created Flux GitRepository resource");
} catch (ApiException e) {
    logger.error("Failed to create Flux resources for BaseRepo: {}", 
        baseRepo.getMetadata().getName(), e);
    return UpdateControl.patchResourceAndRequeue(baseRepo);
}
```

### Custom Exceptions
Create operator-specific exceptions with clear inheritance:

```java
public class TemplateProcessingException extends RuntimeException {
    /**
     * Creates a TemplateProcessingException with a message.
     *
     * @param message Description of the template processing error
     */
    public TemplateProcessingException(String message) {
        super(message);
    }
    
    /**
     * Creates a TemplateProcessingException with a message and cause.
     *
     * @param message Description of the error
     * @param cause The underlying exception
     */
    public TemplateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Sensitive Information
- **Never log secrets, tokens, or unredacted config data**
- Mask sensitive fields in error messages
- Only log safe, non-sensitive fields
- Use `.redact()` methods for sensitive values when available

---

## TESTING & CI

### Test Coverage Requirements
- **Unit Tests**: JUnit 5 + Mockito. Test individual methods, utilities, validators
- **Reconciler Integration Tests**: Mock `KubernetesClient` and dependencies; assert UpdateControl and side-effects
- **Integration Tests**: Use java-operator-sdk testing utilities or fabric8 mock server. Verify whole reconcile flows
- **E2E Tests**: Kind/Testcontainers with mocked GitHub endpoint
- **Naming Convention**: `<Class>Test.java` with methods like `test<Feature><Scenario><Outcome>()`

### CI Pipeline Requirements
- CI must run `mvn clean verify`
- Long-running integration/E2E may run in nightly pipelines
- Tests must be runnable locally: provide README steps
- All tests must pass before merge

### Example Test Structure
```java
@ExtendWith(MockitoExtension.class)
class BaseRepoReconcilerTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private FluxService fluxService;

    private BaseRepoReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new BaseRepoReconciler(kubernetesClient, fluxService);
    }

    @Test
    void testReconcileWithValidBaseRepoCreatesFluxResources() {
        // Arrange
        BaseRepo baseRepo = createTestBaseRepo();

        // Act
        UpdateControl<BaseRepo> result = reconciler.reconcile(baseRepo, null);

        // Assert
        assertThat(result).isNotNull();
        verify(fluxService).createFluxReferenceResources(any());
    }
}
```

---

## KUBERNETES OPERATOR BEST PRACTICES

### 1. Idempotency
- Reconcile must be idempotent: running multiple times leads to the same end state
- Use `Create/Update` semantic with checks for existing resources and compare-and-update patterns (avoid blind replace)
- Design for resumption: ensure any reconcile can be restarted safely

### 2. Small, Fast Reconcile Loops
- Keep `reconcile()` orchestration-only; push heavy CPU or network work to services or background workers
- If long-running work is needed, use asynchronous orchestration and requeue with status updates
- Target: Reconcile completes in < 5 seconds for typical resources

### 3. Use Status Subresource for Observed State
- Keep spec user-declared desired state; only set observed values and conditions in status
- Always persist status via status update APIs; avoid storing transient state in spec
- Use status.conditions for expressing resource health and readiness

### 4. Finalizers for Cleanup
- Use finalizers to ensure external systems (Flux resources, Git branches) are cleaned up before resource deletion
- Remove finalizer only after cleanup is successful
- Implement idempotent cleanup: multiple cleanup attempts should be safe

### 5. OwnerReferences & Garbage Collection
- Use ownerReferences for child resources when appropriate so Kubernetes garbage collects them
- For cross-namespace resources (Flux in `flux-system`), use labels and explicit deletion (ownerReferences cannot cross namespaces)
- Clean up external resources explicitly when no ownerReference can be used

### 6. Field Indexing & Watchers
- Register field indexes for frequently queried fields (e.g., assignments by `spec.clusterType`) to avoid expensive list operations
- Use event-driven watches for dependent resources to avoid polling
- Implement secondary watchers for resources you depend on using `@EventSource`

### 7. Rate Limiting & Backoff
- Respect API server limits; implement backoff and limited retry on API failures
- Use exponential backoff with jitter for retries
- Use leader election for high-availability operators to avoid double-processing

### 8. Concurrency & Thread-Safety
- Avoid shared mutable state across reconcilers; use thread-safe client instances (fabric8 client is thread-safe)
- Limit per-resource concurrency to avoid race conditions; java-operator-sdk has configuration for controller concurrency
- Use immutable data structures for shared state

### 9. Minimal Privileges (RBAC)
- Restrict RBAC to minimum required permissions; avoid cluster-admin unless required and documented
- Create explicit Role/ClusterRole enumerating exact resources (CRDs, core resources) the operator needs
- Document required RBAC permissions in README

### 10. Observability
- Emit structured logs (key fields) and useful status conditions
- Add metrics and health/readiness probes for operator process
- Expose operator metrics via `/metrics` endpoint for Prometheus

### 11. Deterministic Resource Naming
- Avoid randomized names for resources that need subsequent updates; use deterministic names derived from the owner resource
- This enables reliable update-without-fetch patterns

### 12. Status Persistence Before External Changes
- Persist status (or at least record intent) before making irreversible external changes where possible to aid recovery
- Example: Set status condition to "InProgress" before calling GitHub API

---

## COMMON ANTIPATTERNS (DO NOT DO THESE)

### 1. Long Blocking Operations in Reconcile
- **Anti**: Calling slow external APIs or running CPU-intensive tasks synchronously inside `reconcile()`
- **Consequence**: Timeouts, slow requeues, poor operator responsiveness
- **Fix**: Offload to a background worker / async tasks and requeue; report progress via status

### 2. Full Cluster List on Every Reconcile
- **Anti**: Doing `client.list()` for resources across the cluster/namespace without index/selector every reconcile
- **Consequence**: O(n) cost each reconcile; high API server load
- **Fix**: Use field selectors, label selectors, and field indexing; cache where safe

### 3. Mutating Spec During Reconcile
- **Anti**: Writing changes into the resource's spec (user-declared desired state)
- **Consequence**: User confusion and unexpected drift
- **Fix**: Use status for operator-owned state; only modify spec when it is user-intended

### 4. Silent Error Swallowing
- **Anti**: Catching exceptions and not returning an error or setting status condition
- **Consequence**: Failures invisible, operator appears healthy while not functioning
- **Fix**: Log errors with context and set status condition (Ready=False) and return requeue or error

### 5. Global Mutable State
- **Anti**: Using static global caches or mutable singletons to store resource state across threads
- **Consequence**: Race conditions, hard-to-debug failures
- **Fix**: Use thread-safe caches or client-provided caches; prefer stateless reconcilers with injected services

### 6. Hard-coded Resource Names, Tokens, or Cluster Details
- **Anti**: Embedding secrets, cluster URLs, or non-configurable names in code
- **Consequence**: Inflexible deployments and security risk
- **Fix**: Read from `application.properties` or environment and document required secrets

### 7. Creating Resources with Unpredictable Names
- **Anti**: Using random suffixes for child resources that you must later update
- **Consequence**: Hard to detect and update existing resources reliably
- **Fix**: Use deterministic names derived from owner metadata

### 8. Overly Broad RBAC
- **Anti**: Granting cluster-admin to the operator by default
- **Consequence**: Security risk and least-privilege violation
- **Fix**: Enumerate exact resources the operator needs (CRDs, core resources) and create minimal Role/ClusterRole

### 9. Assumptions About Event Ordering
- **Anti**: Assuming a create event will always arrive before related update events
- **Consequence**: Missed reconciles and race conditions
- **Fix**: Design reconcile to be idempotent and recover from any event order

### 10. Logging Sensitive Information
- **Anti**: Logging secrets, tokens, or unredacted config data
- **Consequence**: Leaked secrets in logs
- **Fix**: Mask secrets and only log safe fields

### 11. Incomplete Resource Cleanup
- **Anti**: Failing to delete child resources or external artifacts when parent resource is deleted
- **Consequence**: Orphaned resources, cluster bloat, security issues
- **Fix**: Implement finalizers with comprehensive cleanup logic

### 12. Resource Fetch Without Fallback
- **Anti**: `get()` without checking NotFound; fails hard if resource doesn't exist
- **Consequence**: Unnecessary errors and poor error messages
- **Fix**: Use `getOptional()` or catch `KubernetesClientException` and handle gracefully

---

## MIGRATION STATUS TRACKING

The migration follows the plan in `MIGRATION_PLAN.md` with daily milestones.

### When Implementing Each Day's Tasks
1. Create a branch: `day-N-<feature-description>`
2. Implement all classes for that day
3. Write comprehensive tests
4. Update MIGRATION_PLAN.md with completion status
5. Document any deviations from the plan
6. Create a PR with detailed description

### Update the Checklist in MIGRATION_PLAN.md
```markdown
- [x] Day 0: Maven project structure, dependencies, build working
- [ ] Day 1: Template & ClusterType CRDs compiled, tests passing
- [ ] Day 2: ConfigSchema & BaseRepo CRDs with status support
...
```

---

## BUILD & DEPLOYMENT

### Building the Project
```bash
mvn clean package
```

### Running Locally
```bash
mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"
```

### Docker Build (when applicable)
```bash
docker build -t kalypso-scheduler:latest .
docker run --kubeconfig ~/.kube/config kalypso-scheduler:latest
```

### Verification
```bash
mvn clean verify  # Runs all tests and checks
mvn test          # Run unit tests only
```

---

## PERFORMANCE & SECURITY CONSIDERATIONS

### Memory Performance
- Test with realistic workloads; watch for memory leaks in reconciliation loops
- Monitor heap usage and GC pauses
- Use profiling tools to identify bottlenecks

### API Call Optimization
- Batch Kubernetes API operations where possible
- Use field indexing to reduce list() calls
- Cache stable resources appropriately
- Implement circuit breakers for external API calls

### GitHub Token Security
- Never commit tokens; use environment variables
- Implement token rotation strategies
- Use minimal scopes for GitHub API access
- Store tokens in Kubernetes Secrets, not ConfigMaps

### RBAC Security
- Document required Kubernetes RBAC permissions
- Create minimal Role/ClusterRole (never use cluster-admin)
- Review permissions quarterly

### Input Validation
- Validate all user-provided input (git repos, templates, etc.)
- Sanitize template inputs to prevent injection
- Validate ConfigSchema against CRD specifications
- Implement schema validation before processing

---

## BRANCHING & PR RULES

### Branch Naming Conventions
- **Daily Tasks**: `day-N-<short-desc>` (e.g., `day-1-template-crd`)
- **Features**: `feature/<short-desc>` (e.g., `feature/github-pr-creation`)
- **Bug Fixes**: `fix/<short-desc>` (e.g., `fix/reconciler-timeout`)

### PR Title Format
```
[Day N] Feature Name - Short Description
```

### PR Description Requirements
- **What**: What was implemented
- **Why**: Why it was needed
- **How**: How it works and key design decisions
- **Tests**: What tests were added
- **Deviations**: Any deviations from MIGRATION_PLAN.md
- **References**: Links to relevant issues or design docs

### Commit Message Format
```
[Day N] Short summary (50 chars max)

Detailed explanation (72 chars per line).
Link to MIGRATION_PLAN.md or issue if applicable.

Example:
[Day 1] Implement Template CRD and controller

- Added Template CRD model extending CustomResource
- Implemented TemplateReconciler with field indexing
- Added comprehensive unit and integration tests
- Updated MIGRATION_PLAN.md with completion status

See MIGRATION_PLAN.md day 1 tasks
Fixes #125
```

---

## PR REVIEW CHECKLIST

Before submitting any PR, ensure all items are complete:

- [ ] Build passes: `mvn clean package`
- [ ] Unit tests added & passing: `mvn test`
- [ ] Integration tests added (or test plan presented)
- [ ] JavaDoc added for all public APIs
- [ ] Inline comments for complex logic
- [ ] No hard-coded credentials or sensitive data
- [ ] `application.properties` documented/updated
- [ ] Logging at appropriate levels (no sensitive data in logs)
- [ ] java-operator-sdk remains 5.3.2 (or RFC exists for change)
- [ ] No compiler warnings
- [ ] Naming conventions followed
- [ ] MIGRATION_PLAN.md updated with progress
- [ ] README or architecture docs updated if needed
- [ ] Code follows all patterns in this guide
- [ ] No test failures or flaky tests
- [ ] Performance impact assessed

---

## COMMUNICATION & PR GUIDELINES

### Title Format
```
[Day N] Feature Name - Brief Description
```

### Description Template
```markdown
## What
Brief description of what was implemented

## Why
Why this feature/change is needed

## How
How it works and key design decisions

## Tests
- Unit tests for [component]: tests/path/ComponentTest.java
- Integration tests for [component]: tests/path/ComponentIntegrationTest.java
- Manual testing steps: ...

## Deviations
Any deviations from MIGRATION_PLAN.md

## References
- MIGRATION_PLAN.md Day N
- Related issue: #XXX
```

### PR Size Expectations
- **Reviewable Size**: PRs should be focused on one day's work (typically 300-800 lines)
- **Avoid**: Large PRs with multiple days' work combined
- **Rationale**: Easier review, faster feedback, safer merges

---

## TROUBLESHOOTING & DEBUGGING

### Running Locally
```bash
mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"
```

### Enabling Debug Logging
Increase logging in `logback.xml` or `application.properties`:

**In `logback.xml`:**
```xml
<logger name="io.kalypso.scheduler" level="DEBUG"/>
```

**In `application.properties`:**
```properties
logging.level.io.kalypso.scheduler=DEBUG
logging.level.io.javaoperatorsdk=DEBUG
```

### Testing Utilities
- **Unit Tests**: Use fabric8 mock server or operator-sdk-testing
- **Integration Tests**: Use java-operator-sdk testing utilities
- **E2E Tests**: Use Kind/Testcontainers with mocked GitHub endpoint

### GitHub Testing
- Use sandbox repo + short-lived token for testing PR flows
- Mock GitHub responses in tests using org.mockserver or similar
- Never test against production GitHub repos

### Common Issues
- **ReconcilationInProgress**: Check for slow reconcile loops or blocking operations
- **FieldIndexNotFound**: Ensure field indexes are registered with manager
- **OutOfMemory**: Profile heap usage; watch for resource leaks in loops
- **API Rate Limits**: Implement backoff and jitter for retries

---

## GO ↔ JAVA MIGRATION REFERENCE

When implementing features from the Go code, use these patterns:

| Go Pattern | Java Implementation |
|---|---|
| `Reconcile(ctx context.Context)` | `reconcile(T resource, Context<T> context)` |
| `ctrl.Result{RequeueAfter: 3s}` | `UpdateControl.updateResourceAndRequeue()` with delay |
| Status conditions management | `io.kubernetes.client.openapi.models.V1Condition` |
| Field indexing | java-operator-sdk's field indexing via SetupWithManager |
| Watchers `.Watches()` | `@EventSource` annotations |
| `Map[string]interface{}` | `Map<String, Object>` with Jackson serialization |
| `client.List()` with selector | `KubernetesClient.list()` with `ListOptions` |
| `ctrlutil.SetControllerReference()` | `KubernetesResourceUtil.setOwnerReference()` |
| Finalizers | `Reconciler.cleanup()` and `finalizer.add()/remove()` |
| Logging | SLF4J with Logback |

**Reference the Go source**: https://github.com/microsoft/kalypso-scheduler

---

## QUICK REFERENCE CHECKLIST

Before submitting any code:

- [ ] All public classes/methods have JavaDoc
- [ ] Complex logic has inline comments
- [ ] Code follows naming conventions
- [ ] Unit tests pass (`mvn test`)
- [ ] Build succeeds (`mvn clean package`)
- [ ] No hard-coded values (use properties)
- [ ] Custom exceptions used for operator-specific errors
- [ ] Logging statements at appropriate levels
- [ ] MIGRATION_PLAN.md updated with progress
- [ ] README or architecture docs updated if needed
- [ ] No test failures or warnings
- [ ] java-operator-sdk 5.3.2 used (not changed)
- [ ] No compiler warnings
- [ ] Configuration externalized via properties
- [ ] No sensitive data logged or committed
- [ ] RBAC documented if new permissions needed
- [ ] Performance impact assessed
- [ ] Idempotency verified
- [ ] Finalizers implemented where needed
- [ ] Error messages user-friendly

---

## KEY CONTACTS & RESOURCES

### Project References
- **Original Go Repository**: https://github.com/microsoft/kalypso-scheduler
- **Migration Plan**: `/MIGRATION_PLAN.md`
- **This File**: `.github/COMBINED-INSTRUCTIONS.md`

### Framework Documentation
- **java-operator-sdk Documentation**: https://javaoperatorsdk.io/
- **Kubernetes Client Documentation**: https://github.com/fabric8io/kubernetes-client
- **Freemarker Documentation**: https://freemarker.apache.org/
- **Kohsuke GitHub API**: https://github.com/kohsuke/github-api
- **Jackson YAML**: https://github.com/FasterXML/jackson-dataformats-text

### CI/CD & Build Tools
- **Maven**: https://maven.apache.org/
- **JUnit 5**: https://junit.org/junit5/
- **Mockito**: https://site.mockito.org/

---

## GOVERNANCE

This file is authoritative for agent behavior in this repository. Changes to mandatory rules (docs policy, pinned SDK version, test requirements) must be proposed by an RFC-style PR and approved by maintainers.

### Change Process for Instructions
1. Create a draft PR with proposed changes
2. Link to this file and reference specific sections
3. Provide rationale for changes
4. Wait for maintainer approval before merging
5. Update last updated date and status

**Last Updated**: 2026-05-01  
**Status**: Combined from coding-instructions.md and copilot-instructions.md
**Consolidated**: All content merged without loss

---

**END OF DOCUMENT**
