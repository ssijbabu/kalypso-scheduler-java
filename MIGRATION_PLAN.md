# Kalypso Scheduler - Go to Java Migration Plan

## Overview

This document outlines the step-by-step migration plan to convert the Kalypso Scheduler operator from Go (Kubebuilder) to Java (java-operator-sdk v5.3.2).

**Timeline**: One CRD and its controller per day  
**Target**: Full functional parity with the Go implementation  
**Framework**: java-operator-sdk v5.3.2  
**Repository**: ssijbabu/kalypso-scheduler-java

---

## Architecture Summary

### Go Implementation (Source)

The Kalypso Scheduler is a Kubebuilder-based Kubernetes operator with 12 CRDs and 9 controllers:

**CRDs (in dependency order)**:
1. Template
2. ClusterType
3. ConfigSchema
4. BaseRepo
5. Environment
6. WorkloadRegistration
7. Workload
8. DeploymentTarget
9. SchedulingPolicy
10. Assignment
11. AssignmentPackage
12. GitOpsRepo

**Controllers**:
- BaseRepoReconciler
- EnvironmentReconciler
- WorkloadRegistrationReconciler
- WorkloadReconciler
- SchedulingPolicyReconciler
- AssignmentReconciler
- AssignmentPackageReconciler
- GitOpsRepoReconciler
- (Template and ConfigSchema have no dedicated controllers)

**Key Libraries**:
- Controller-runtime: Kubernetes client & reconciliation framework
- Flux API: GitRepository & Kustomization resources
- Sprig: Template function library
- GitHub API: PR creation & GitOps interactions

---

## Migration Strategy

### Phase 1: Bootstrap & Dependency Foundation (Day 0)

**Objectives**:
- Initialize java-operator-sdk project structure
- Configure Maven/Gradle build system
- Establish dependency mappings
- Create base project layout

**Tasks**:
1. Create Maven POM with java-operator-sdk 5.3.2 and dependencies
2. Set up package structure mirroring Go layout:
   - `io.kalypso.scheduler.api.v1alpha1` → CRD classes
   - `io.kalypso.scheduler.controllers` → Reconcilers
   - `io.kalypso.scheduler.scheduler` → Business logic
3. Add critical dependencies:
   - io.javaoperatorsdk:operator-sdk:5.3.2
   - io.fabric8:kubernetes-client:6.x (for Kubernetes API)
   - io.fabric8:kubernetes-model-core (Kubernetes models)
   - io.fabric8:kubernetes-model-apps (Deployments, etc.)
   - Flux API models (or HTTP client for Flux resources)
   - com.fasterxml.jackson (YAML/JSON serialization)
   - io.swagger:swagger-models (for OpenAPI annotations)
4. Create base configuration classes
5. Establish GitHub Actions CI/CD pipeline
6. Document build & run commands

**Deliverable**: Buildable Java project with correct Maven structure

---

### Phase 2: Core Data Models (Days 1-3)

Migrate CRD definitions in dependency order. Each CRD becomes a Java class annotated with `@Group`, `@Version`, `@Kind`.

#### Day 1: Template & ClusterType CRDs

**Template CRD Migration**:
- Create `Template.java` class (extends CustomResource)
- Map Go TemplateSpec → Java TemplateSpec
- Add validation annotations (`@Valid`, `@NotNull`, etc.)
- Add kubebuilder markers equivalent (via comments)
- Create TemplateList class
- Constants for template types (RECONCILER, NAMESPACE, CONFIG)

**ClusterType CRD Migration**:
- Create `ClusterType.java` class
- Map ClusterTypeSpec with fields:
  - reconciler: String
  - namespaceService: String
  - configType: String
- Add status subresource
- Create ClusterTypeList
- Add constants for config types (CONFIGMAP, ENVFILE)

**Expected Tasks**:
1. Write Template class with Spec/Status
2. Write ClusterType class with Spec/Status
3. Add Jackson annotations for YAML serialization
4. Write unit tests for serialization/deserialization
5. Verify CRD generation tools work correctly

**Deliverable**: Template & ClusterType classes compile and serialize correctly

---

#### Day 2: ConfigSchema & BaseRepo CRDs

**ConfigSchema CRD Migration**:
- Create `ConfigSchema.java` class
- Map ConfigSchemaSpec (placeholder structure based on usage)
- Add status condition support

**BaseRepo CRD Migration**:
- Create `BaseRepo.java` class
- Map BaseRepoSpec with fields:
  - repo: String (Git repository URL)
  - branch: String
  - path: String
  - commit: String (optional)
- Add status conditions (Ready condition)
- Create BaseRepoList

**Expected Tasks**:
1. Write ConfigSchema class
2. Write BaseRepo class
3. Add Conditions support (metav1.Condition equivalent)
4. Implement status subresource patterns
5. Create test fixtures

**Deliverable**: ConfigSchema & BaseRepo classes with status support

---

#### Day 3: Environment, WorkloadRegistration, Workload CRDs

**Environment CRD Migration**:
- Create `Environment.java` class
- Map EnvironmentSpec with:
  - controlPlane: RepositoryReference
- Add status conditions
- Create EnvironmentList

**WorkloadRegistration CRD Migration**:
- Create `WorkloadRegistration.java` class
- Map WorkloadRegistrationSpec with:
  - workload: RepositoryReference (repo, branch, path)
  - workspace: String
- Add status conditions
- Create WorkloadRegistrationList

**Workload CRD Migration**:
- Create `Workload.java` class
- Map WorkloadSpec with:
  - deploymentTargets: List<DeploymentTarget>
  - (nested DeploymentTargetSpec)
- Add status conditions
- Create WorkloadList

**Expected Tasks**:
1. Create RepositoryReference utility class (used by multiple CRDs)
2. Write Environment, WorkloadRegistration, Workload classes
3. Implement nested spec classes properly
4. Add list selector support (used for scheduling)
5. Test label matching logic

**Deliverable**: Environment, WorkloadRegistration, Workload classes functional

---

### Phase 3: Complex Data Models (Days 4-5)

#### Day 4: DeploymentTarget, SchedulingPolicy CRDs

**DeploymentTarget CRD Migration**:
- Create `DeploymentTarget.java` class
- Map DeploymentTargetSpec with:
  - name: String
  - labels: Map<String, String>
  - environment: String
  - manifests: RepositoryReference
- Add label constants (WORKSPACE_LABEL, WORKLOAD_LABEL)
- Add status conditions
- Create DeploymentTargetList

**SchedulingPolicy CRD Migration**:
- Create `SchedulingPolicy.java` class
- Map SchedulingPolicySpec with:
  - deploymentTargetSelector: Selector
  - clusterTypeSelector: Selector
- Implement Selector class (workspace, labelSelector)
- Add status conditions
- Create SchedulingPolicyList

**Expected Tasks**:
1. Create Selector & LabelSelector utility classes
2. Write DeploymentTarget with nested specs
3. Write SchedulingPolicy with complex selectors
4. Implement label matching utilities
5. Test selector logic against various label combinations

**Deliverable**: DeploymentTarget & SchedulingPolicy fully modeled

---

#### Day 5: Assignment, AssignmentPackage, GitOpsRepo CRDs

**Assignment CRD Migration**:
- Create `Assignment.java` class
- Map AssignmentSpec with:
  - clusterType: String
  - deploymentTarget: String
- Add status conditions
- Create AssignmentList

**AssignmentPackage CRD Migration**:
- Create `AssignmentPackage.java` class
- Map AssignmentPackageSpec with:
  - reconcilerManifests: List<String>
  - reconcilerManifestsContentType: String
  - namespaceManifests: List<String>
  - namespaceManifestsContentType: String
  - configManifests: List<String>
  - configManifestsContentType: String
- Add label constants (CLUSTER_TYPE_LABEL, DEPLOYMENT_TARGET_LABEL)
- Add content type constants (YAML, SH)
- Add status conditions
- Create AssignmentPackageList

**GitOpsRepo CRD Migration**:
- Create `GitOpsRepo.java` class
- Map GitOpsRepoSpec with:
  - repo: String
  - branch: String
  - path: String
- Add status conditions
- Create GitOpsRepoList

**Expected Tasks**:
1. Write Assignment class
2. Write AssignmentPackage with multiple manifest lists
3. Write GitOpsRepo class
4. Test YAML serialization with large manifest lists
5. Verify status conditions propagate correctly

**Deliverable**: All 12 CRDs fully implemented and tested

---

### Phase 4: Supporting Infrastructure (Days 6-7)

#### Day 6: Flux Integration & External Resource Handling

**Objectives**: Implement Flux resource creation/deletion logic

**Tasks**:
1. **Flux Model Classes**:
   - Create GitRepository model (source.toolkit.fluxcd.io/v1beta2)
   - Create Kustomization model (kustomize.toolkit.fluxcd.io/v1beta2)
   - Use fabric8 kubernetes-client models or custom implementations

2. **FluxService Class** (equivalent to Go flux.go):
   - `CreateFluxReferenceResources()` method
   - `DeleteFluxReferenceResources()` method
   - Properties: name, namespace, targetNamespace, url, branch, path

3. **KubernetesClient Integration**:
   - Extend KubernetesClient for Flux APIs
   - Create/Update/Delete GitRepository resources
   - Create/Update/Delete Kustomization resources

4. **Error Handling**:
   - Implement retry logic
   - Handle resource conflicts
   - Status condition updates on failure

**Deliverable**: FluxService working with fabric8 client

---

#### Day 7: Template Processing & GitHub Integration

**Objectives**: Implement manifest templating & GitOps PR creation

**Tasks**:
1. **Template Engine**:
   - Replace Go text/template with Java Freemarker or Handlebars
   - Create TemplateProcessor class
   - Implement template variable context class (TemplateContext):
     - deploymentTargetName: String
     - namespace: String
     - environment: String
     - workspace: String
     - workload: String
     - labels: Map<String, String>
     - clusterType: String
     - configData: Map<String, Object>
     - repo: String
     - path: String
     - branch: String

2. **Template Functions** (replace Sprig):
   - toYaml() - serialize objects to YAML
   - stringify() - convert objects to string
   - hash() - content addressing
   - unquote() - remove quotes
   - (Standard Freemarker functions for others)

3. **GitHub API Integration**:
   - Use kohsuke/github library or GitHub API client
   - Implement GitHubClient class
   - Methods:
     - createPullRequest(repo, branch, content, message)
     - checkRepositoryAccess(repo, token)
   - Handle authentication (token-based)

4. **ConfigSchema Validation**:
   - Implement schema validation for config data
   - Use JSON Schema validation library (everit-org/json-schema)

**Deliverable**: Template processing and GitHub PR creation functional

---

### Phase 5: Controllers & Business Logic (Days 8-14)

Controllers use java-operator-sdk's `@ControllerConfiguration` annotation pattern.

#### Day 8: BaseRepoReconciler & EnvironmentReconciler

**BaseRepoReconciler**:
- Extends `Reconciler<BaseRepo>` from java-operator-sdk
- Implements `reconcile(BaseRepo, Context)` method
- **Logic**:
  1. Fetch BaseRepo instance
  2. Check deletion timestamp
  3. If deleted: DeleteFluxReferenceResources()
  4. If not deleted: CreateFluxReferenceResources()
  5. Update status conditions (Ready: true/false)
  6. Return Result (requeue delay)

- **Watchers**: None (no dependent resources)
- **Field Indexing**: None required
- **Testing**: Unit tests with mocked KubernetesClient

**EnvironmentReconciler**:
- Similar pattern to BaseRepoReconciler
- **Additional Logic**:
  1. Create/Delete namespace for environment
  2. Create/Delete Flux resources
  3. Update status

- **Watchers**: Watch ClusterType changes? (verify from Go code intent)
- **Field Indexing**: Index by name

**Expected Tasks**:
1. Implement BaseRepoReconciler with Flux integration
2. Implement EnvironmentReconciler with namespace creation
3. Create Result/RetryableResult patterns
4. Implement status condition management helper
5. Write integration tests with KubernetesServer test framework

**Deliverable**: Two simple reconcilers working end-to-end

---

#### Day 9: WorkloadRegistrationReconciler & WorkloadReconciler

**WorkloadRegistrationReconciler**:
- Similar to BaseRepoReconciler
- Creates Flux resources to fetch Workload definitions

**WorkloadReconciler**:
- More complex: Creates/Updates/Deletes DeploymentTarget resources
- **Logic**:
  1. Fetch Workload and list existing DeploymentTargets
  2. Reconcile desired vs. actual DeploymentTargets
  3. Delete DeploymentTargets not in Workload spec
  4. Create/Update DeploymentTargets in spec
  5. Set labels on DeploymentTargets (workload, workspace)
  6. Update status conditions

- **Watchers**: 
  - Watch DeploymentTarget (owns them)
  - Field indexing by "workload" label

- **Helper Methods**:
  - buildDeploymentTargetName()
  - reconcileDeploymentTargets()

**Expected Tasks**:
1. Implement WorkloadRegistrationReconciler (Flux creation)
2. Implement WorkloadReconciler (complex resource management)
3. Implement label-based resource tracking
4. Test cascading deletes
5. Test idempotent reconciliation

**Deliverable**: Workload-related reconcilers working

---

#### Day 10: SchedulingPolicyReconciler

**Complexity**: HIGH - Core scheduling logic

**SchedulingPolicyReconciler**:
- **Logic**:
  1. Fetch SchedulingPolicy
  2. List all ClusterTypes in namespace
  3. List all DeploymentTargets in namespace
  4. For each DeploymentTarget matching selector:
     - For each ClusterType matching selector:
       - Create Assignment CRD (clusterType, deploymentTarget)
  5. Delete old Assignments not in computed set
  6. Update status (Ready: true when all assignments created)

- **Watchers**:
  - Watch ClusterType (trigger re-scheduling on label changes)
  - Watch DeploymentTarget (trigger re-scheduling on label changes)
  - Own Assignment resources

- **Field Indexing**:
  - Index Assignment by clusterType field
  - Index ClusterType by name (for watches)

- **Helper Methods**:
  - matchesDeploymentTargetSelector()
  - matchesClusterTypeSelector()
  - computeAssignments() - returns Set<Assignment>
  - reconcileAssignments() - diff and update

**Expected Tasks**:
1. Implement label matching logic from SchedulingPolicy
2. Implement selector evaluation (workspace + labelSelector)
3. Implement Assignment creation/deletion
4. Add watchers for ClusterType and DeploymentTarget
5. Test complex selector matching scenarios
6. Test cascading reconciliation when ClusterType changes

**Deliverable**: SchedulingPolicy reconciliation working

---

#### Day 11: AssignmentReconciler

**Complexity**: HIGH - Template processing & manifest generation

**AssignmentReconciler**:
- **Logic**:
  1. Fetch Assignment
  2. Fetch referenced ClusterType, DeploymentTarget, Template
  3. Gather ConfigData from ConfigMaps with matching labels
  4. Validate ConfigData against ConfigSchema
  5. Generate AssignmentPackage by rendering templates:
     - Reconciler template (from ClusterType.reconciler)
     - Namespace template (from ClusterType.namespaceService)
     - Config template (if defined)
  6. Create/Update AssignmentPackage CRD
  7. Update status conditions

- **Watchers**:
  - Watch ClusterType (changes trigger re-rendering)
  - Watch DeploymentTarget (changes trigger re-rendering)
  - Watch Template (changes trigger re-rendering)
  - Watch ConfigMap (changes trigger re-rendering)
  - Own AssignmentPackage resources

- **Field Indexing**:
  - Index by clusterType field
  - Index by deploymentTarget field

- **Helper Methods**:
  - getConfigData() - gather ConfigMaps by labels
  - validateConfigData() - validate against ConfigSchema
  - generateAssignmentPackage() - render all templates
  - renderTemplate() - use TemplateProcessor
  - buildTemplateContext() - create context for Freemarker

**Template Rendering Data**:
```
DeploymentTargetName: "myapp-prod"
Namespace: "derived-from-template"
Environment: "prod"
Workspace: "team-a"
Workload: "myapp"
Labels: {purpose: prod, edge: false}
ClusterType: "large"
ConfigData: {REGION: "west-us", DB_URL: "..."}
Repo: "https://github.com/..."
Path: "./prod"
Branch: "main"
```

**Expected Tasks**:
1. Implement Freemarker template engine integration
2. Implement TemplateContext builder
3. Implement template function library (toYaml, etc.)
4. Implement ConfigData gathering from ConfigMaps
5. Implement ConfigSchema validation
6. Implement AssignmentPackage generation
7. Add watchers for all dependency resources
8. Test template rendering with various data types
9. Test error handling (missing configs, invalid schemas)

**Deliverable**: Assignment → AssignmentPackage flow working

---

#### Day 12: AssignmentPackageReconciler & GitOpsRepoReconciler (Part 1)

**AssignmentPackageReconciler**:
- May be minimal or just status management
- **Logic**:
  1. Validate manifests can be parsed as YAML
  2. Check content types (yaml, sh)
  3. Update status (Ready: true if valid)

- **Watchers**: None (created by Assignment)

**GitOpsRepoReconciler (Part 1)** - Preparation:
- **Logic** (simplified for now):
  1. Fetch GitOpsRepo
  2. List all SchedulingPolicies in namespace
  3. Check if all SchedulingPolicy.Status.Conditions[Ready] = true
  4. If ready, list all Assignments
  5. Check if all Assignment.Status.Conditions[Ready] = true
  6. If all ready, prepare for GitOps PR creation

- **Watchers**:
  - Watch SchedulingPolicy (watch for Ready condition)
  - Watch Assignment (watch for Ready condition)
  - Potential watch of AssignmentPackage

- **Status Management**:
  - Set condition ReadyToPR based on dependency conditions

**Expected Tasks**:
1. Implement AssignmentPackageReconciler (simple validation)
2. Implement GitOpsRepoReconciler status aggregation logic
3. Create condition aggregation helpers
4. Test status propagation through chain
5. Verify all reconcilers update status correctly

**Deliverable**: Status conditions propagating through entire chain

---

#### Day 13: GitOpsRepoReconciler (Part 2) - PR Creation

**GitOpsRepoReconciler (Part 2)** - PR Creation:
- **Complex Logic**:
  1. List all AssignmentPackages in namespace
  2. For each AssignmentPackage:
     - Extract manifests from reconcilerManifests, namespaceManifests, configManifests
     - Organize by directory structure (cluster-type/deployment-target/)
  3. Create PR content:
     - Commit message: includes workload, cluster-type, deployment-target info
     - Branch name: auto-generated from timestamp/hash
     - File changes: manifests organized in Git paths
  4. Call GitHub API to create PR
  5. Update status with PR URL
  6. Handle errors (GitHub auth, repo not accessible)

- **PR Structure**:
  ```
  Base branch: (from GitOpsRepo.spec.branch)
  PR branch: (kalypso-generated-<timestamp>-<hash>)
  
  Directory structure:
  <path>/
    <environment>/
      <cluster-type>/
        <deployment-target>/
          reconciler.yaml
          namespace.yaml
          config.yaml (if exists)
  ```

- **Helper Methods**:
  - aggregateAssignmentPackages() - collect all in namespace
  - organizeManifestsForGitOps() - build file structure
  - buildPRContent() - structure changes
  - createPullRequest() - GitHub API call
  - formatCommitMessage() - include metadata

**Expected Tasks**:
1. Implement manifest aggregation from AssignmentPackages
2. Implement file structure organization
3. Implement GitHub PR creation
4. Add retry logic for GitHub API calls
5. Test with mock GitOpsRepo
6. Handle large PR scenarios (pagination if needed)
7. Add rollback/cleanup on PR creation failure

**Deliverable**: End-to-end PR creation working

---

#### Day 14: Integration & Testing

**Objectives**: Verify entire reconciliation chain, add tests, document

**Tasks**:
1. **Integration Tests**:
   - Create end-to-end test scenario (KubernetesServer framework)
   - Create all CRDs (BaseRepo, Environment, WorkloadRegistration, etc.)
   - Verify reconciliation chain completes
   - Verify final AssignmentPackage created
   - Mock GitHub API for PR validation

2. **Unit Tests Enhancement**:
   - Add tests for each reconciler individually
   - Test error paths and retry logic
   - Test label matching logic
   - Test template rendering edge cases

3. **Documentation**:
   - Update MIGRATION_PLAN.md with completion notes
   - Create ARCHITECTURE.md for Java implementation
   - Document differences from Go version (if any)
   - Create troubleshooting guide

4. **Build & Deployment**:
   - Verify Docker image builds correctly
   - Test operator startup with CRDs
   - Verify logs are clear and helpful
   - Add health checks

**Deliverable**: Complete, tested, documented operator

---

## Key Mapping: Go ↔ Java

### Framework Patterns

| Go (Kubebuilder) | Java (java-operator-sdk) |
|---|---|
| `func (r *Reconciler) Reconcile()` | `reconcile(T resource, Context context)` |
| `ctrl.Result{RequeueAfter: 3s}` | `UpdateControl.updateResourceAndRequeue()` or custom UpdateControl |
| Status conditions (metav1.Condition) | Java metav1.Condition equivalent |
| Field indexing (manager.GetFieldIndexer) | IndexFieldManager in SetupWithManager |
| Watchers (.Watches()) | `@EventSource` annotations |
| RBAC markers (+kubebuilder:rbac) | @RBAC annotations in Java |
| Controllers (.For().Owns().Watches()) | @ControllerConfiguration |

### Data Structure Mappings

| Go Type | Java Type | Notes |
|---|---|---|
| interface{} | Object (or generic Type<?>) | Use Jackson for serialization |
| map[string]string | Map<String, String> | |
| []string | List<String> | |
| *CustomResource | extends CustomResource<Spec, Status> | |
| metav1.Condition | io.kubernetes.client.openapi.models.V1Condition | |
| time.Duration | java.time.Duration | |
| ctrl.Request | ResourceID or similar | |

### Library Mappings

| Go Library | Java Library | Purpose |
|---|---|---|
| sigs.k8s.io/controller-runtime | io.javaoperatorsdk | Kubernetes operator framework |
| k8s.io/api, k8s.io/apimachinery | io.fabric8:kubernetes-client | Kubernetes API client |
| github.com/fluxcd/... | Custom models or HTTP client | Flux resource management |
| text/template + Sprig | Freemarker or Handlebars | Template processing |
| github.com/google/go-github | kohsuke/github | GitHub API interaction |
| gopkg.in/yaml.v3 | Jackson YAML | YAML serialization |

---

## Testing Strategy

### Unit Testing
- Mock KubernetesClient for each reconciler
- Test each reconciler method in isolation
- Test selector matching logic
- Test template rendering
- Framework: JUnit 5 + Mockito

### Integration Testing
- Use KubernetesServer test framework (from java-operator-sdk)
- Create full CRD instances
- Run reconciliation loops
- Verify state changes
- Framework: KubernetesServer + JUnit 5

### End-to-End Testing
- Deploy to local Kubernetes cluster (Kind/Minikube)
- Create real CRD instances
- Verify GitHub PR creation (with mocked GitHub)
- Framework: Testcontainers + custom test harness

---

## Deliverables Checklist

- [x] Day 0: Maven project structure, dependencies, build working
- [x] Day 1: Template & ClusterType CRDs compiled, tests passing
- [x] Day 2: ConfigSchema & BaseRepo CRDs with status support
- [x] Day 3: Environment, WorkloadRegistration, Workload CRDs
- [x] Day 4: DeploymentTarget, SchedulingPolicy CRDs
- [x] Day 5: Assignment, AssignmentPackage, GitOpsRepo CRDs
- [x] Day 6: FluxService functional with KubernetesClient
- [x] Day 7: Template processing, GitHub integration working
- [ ] Day 8: BaseRepoReconciler, EnvironmentReconciler operational
- [ ] Day 9: WorkloadRegistrationReconciler, WorkloadReconciler operational
- [ ] Day 10: SchedulingPolicyReconciler with label matching
- [ ] Day 11: AssignmentReconciler with template processing
- [ ] Day 12: AssignmentPackageReconciler, GitOpsRepoReconciler status
- [ ] Day 13: GitOpsRepoReconciler PR creation
- [ ] Day 14: Integration tests, documentation, build verified

---

## Risk Mitigation

### Known Challenges

1. **Flux Integration**: Flux resources may require custom HTTP client instead of fabric8
   - Mitigation: Research fabric8 CRD plugin or implement custom JSON serialization

2. **Template Engine**: Go's text/template + Sprig is powerful
   - Mitigation: Use Freemarker (more mature in Java) with custom functions

3. **GitHub API**: Requires reliable authentication
   - Mitigation: Use well-maintained kohsuke/github library, test with mock

4. **Status Condition Management**: Java enums vs. string-based Go conditions
   - Mitigation: Create helper classes for condition management

5. **Field Indexing**: Different pattern than Go
   - Mitigation: Use java-operator-sdk's field indexing capabilities

### Validation Points

- After Day 3: Verify all CRDs serialize/deserialize correctly
- After Day 7: Test template rendering with realistic data
- After Day 10: Verify label matching matches Go behavior
- After Day 13: Validate PR structure matches intended format

---

## Next Steps

1. Bootstrap Maven project with correct structure and dependencies
2. Create CRD classes with proper annotations
3. Implement reconcilers in dependency order
4. Write comprehensive tests for each component
5. Perform end-to-end validation
6. Document operational differences from Go version

---

## References

- Go Source: https://github.com/ssijbabu/kalypso-scheduler
- java-operator-sdk: https://javaoperatorsdk.io/
- Fabric8 Kubernetes Client: https://github.com/fabric8io/kubernetes-client
- Freemarker: https://freemarker.apache.org/
- kohsuke/github: https://github.com/kohsuke/github

