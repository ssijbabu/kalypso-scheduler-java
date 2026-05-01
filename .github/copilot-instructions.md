# Kalypso Scheduler Java Operator - Agent Instructions

## Overview

This repository contains the Java implementation of the Kalypso Scheduler Operator, migrated from the original Go implementation. The migration uses **java-operator-sdk 5.3.2** and follows the detailed plan in `MIGRATION_PLAN.md`.

**Original Go Repository**: https://github.com/microsoft/kalypso-scheduler

---

## Critical Instructions for All Tasks

### 1. Documentation Requirements ⚠️ MANDATORY

**Every code change, feature, or implementation must include comprehensive documentation.**

- **JavaDoc Comments**: All public classes, methods, and interfaces must have JavaDoc comments explaining:
  - Purpose and responsibility
  - Parameters and return values
  - Exceptions that may be thrown
  - Example usage when applicable

- **Inline Comments**: Complex logic must include clear inline comments explaining:
  - What the code does
  - Why it's implemented that way
  - Any non-obvious design decisions

- **README/Documentation Files**: 
  - Update relevant documentation files when adding new components
  - Add architecture diagrams when introducing new subsystems
  - Document configuration options in `application.properties`

- **Code Comments Format**:
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

**No documentation = No approval for merge**

---

### 2. Framework & Technology Stack

**Required Framework**: `io.javaoperatorsdk:operator-sdk:5.3.2`

**Mandatory Library Versions**:
- java-operator-sdk: **5.3.2** (DO NOT change)
- Kubernetes Client: 6.11.0+
- Jackson: 2.16.1+
- Freemarker: 2.3.32+
- slf4j: 2.0.11+

**Technology Standards**:
- Language: Java 17+
- Build Tool: Maven
- Testing Framework: JUnit 5 + Mockito
- Logging: SLF4J with Logback
- YAML Processing: Jackson YAML
- Template Engine: Freemarker
- GitHub API: kohsuke/github

Update `pom.xml` if you need to add new dependencies, but do NOT change core framework versions without explicit approval.

---

### 3. Package Structure

Maintain the following Maven directory structure:

```
kalypso-scheduler-java/
├── src/main/java/io/kalypso/scheduler/
│   ├── KalypsoSchedulerOperator.java          (main entry point)
│   ├── api/v1alpha1/                          (CRD models)
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

---

### 4. Coding Standards

#### Naming Conventions
- **Classes**: PascalCase (e.g., `BaseRepoReconciler`)
- **Methods**: camelCase (e.g., `reconcile()`, `getConfigData()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_FLUX_NAMESPACE`)
- **Variables**: camelCase (e.g., `baseRepo`, `clusterType`)

#### Code Quality
- **Classes**: Single responsibility principle - one class, one purpose
- **Methods**: Keep methods focused and testable (ideally < 50 lines)
- **Exception Handling**: Use custom exceptions for operator-specific errors
- **Null Safety**: Use Optional<T> where appropriate, avoid null when possible
- **Immutability**: Use final fields and immutable collections where safe

#### Example Pattern
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

### 5. Testing Requirements

Every new feature must have corresponding tests.

#### Test Coverage
- **Unit Tests**: Test individual methods, utilities, validators
- **Integration Tests**: Test reconcilers with mocked KubernetesClient
- **Test Naming**: `<Class>Test.java` with methods like `test<Feature><Scenario><Outcome>()`

#### Example Test Structure
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

### 6. Migration Status Tracking

The migration follows the plan in `MIGRATION_PLAN.md` with daily milestones.

**When implementing each day's tasks:**
1. Create a branch: `day-N-<feature-description>`
2. Implement all classes for that day
3. Write comprehensive tests
4. Update MIGRATION_PLAN.md with completion status
5. Document any deviations from the plan
6. Create a PR with detailed description

**Update the checklist in MIGRATION_PLAN.md**:
```markdown
- [x] Day 0: Maven project structure, dependencies, build working
- [ ] Day 1: Template & ClusterType CRDs compiled, tests passing
- [ ] Day 2: ConfigSchema & BaseRepo CRDs with status support
...
```

---

### 7. Configuration & Property Management

- All configuration should be externalizable via `application.properties`
- Hard-coded values are NOT allowed
- Use `@ConfigurationProperties` or property injection for configuration
- Document configuration options in comments

Example:
```java
@Component
public class FluxConfiguration {
    
    @Value("${flux.default-namespace:flux-system}")
    private String defaultNamespace;
    
    @Value("${flux.git-repository-version:v1beta2}")
    private String gitRepositoryVersion;
    
    // Getters...
}
```

---

### 8. Error Handling & Logging

#### Logging Levels
- **ERROR**: Reconciliation failures, critical issues
- **WARN**: Retries, resource not found, non-critical errors
- **INFO**: Major reconciliation events, status updates
- **DEBUG**: Detailed step-by-step execution (enabled for development)

#### Example Logging Pattern
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

#### Custom Exceptions
Create operator-specific exceptions:
```java
public class TemplateProcessingException extends RuntimeException {
    public TemplateProcessingException(String message) {
        super(message);
    }
    
    public TemplateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### 9. Go ↔ Java Migration Reference

When implementing features from the Go code:

| Go Pattern | Java Implementation |
|---|---|
| `Reconcile(ctx context.Context)` | `reconcile(T resource, Context<T> context)` |
| `ctrl.Result{RequeueAfter: 3s}` | `UpdateControl.updateResourceAndRequeue()` with delay |
| Status conditions management | io.kubernetes.client.openapi.models.V1Condition |
| Field indexing | java-operator-sdk's field indexing via SetupWithManager |
| Watchers (.Watches()) | @EventSource annotations |
| Map[string]interface{} | Map<String, Object> with Jackson serialization |

Reference the Go source: https://github.com/microsoft/kalypso-scheduler

---

### 10. Build & Deployment

#### Building
```bash
mvn clean package
```

#### Running Locally
```bash
mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"
```

#### Docker Build (when applicable)
```bash
docker build -t kalypso-scheduler:latest .
docker run --kubeconfig ~/.kube/config kalypso-scheduler:latest
```

---

### 11. Performance & Security Considerations

- **Memory**: Test with realistic workloads; watch for memory leaks in reconciliation loops
- **API Calls**: Batch Kubernetes API operations where possible
- **GitHub Tokens**: Never commit tokens; use environment variables
- **RBAC**: Document required Kubernetes RBAC permissions
- **Input Validation**: Validate all user-provided input (git repos, templates, etc.)

---

### 12. Communication & PR Guidelines

When creating Pull Requests:

1. **Title Format**: `[Day N] Feature Name - Brief Description`
2. **Description**: 
   - What: What was implemented
   - Why: Why it was needed
   - How: How it works and key design decisions
   - Tests: What tests were added
   - Deviations: Any deviations from MIGRATION_PLAN.md

3. **Reviewable Size**: PRs should be focused on one day's work

---

## Quick Reference Checklist

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

---

## Key Contacts & Resources

- **Go Source Repository**: https://github.com/microsoft/kalypso-scheduler
- **java-operator-sdk Documentation**: https://javaoperatorsdk.io/
- **Kubernetes Client Documentation**: https://github.com/fabric8io/kubernetes-client
- **Migration Plan**: `/MIGRATION_PLAN.md`

---

**Last Updated**: 2026-05-01  
**Status**: Day 0 Complete - Bootstrap Ready for Day 1