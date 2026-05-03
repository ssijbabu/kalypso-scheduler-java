# Kalypso Scheduler - AI Agent Instructions (java-operator-sdk)

This file is the authoritative instruction set for AI agents (Copilot) and contributors implementing the Kalypso Scheduler operator in Java using java-operator-sdk (jdk-operator-sdk). Follow these rules for every change. Non‑compliance will delay reviews and merges.

Version: java-operator-sdk 5.3.2 (PINNED)  
Java: 17+  
Build: Maven

-------------------------------------------------------------------------------
CONTENTS
- Scope & purpose
- Absolute requirements
- Project layout
- Coding & documentation rules
- Controller development (java-operator-sdk patterns)
- CRD model rules
- Template, Flux, GitHub integration rules
- Validation & schema
- Testing & CI
- Kubernetes Operator Best Practices
- Common antipatterns (what not to do)
- Branching/PR rules & review checklist
- Troubleshooting & references
-------------------------------------------------------------------------------

SCOPE & PURPOSE
This repository implements Kalypso Scheduler — an operator that transforms high-level control-plane abstractions (Template, Workload, SchedulingPolicy, etc.) into manifests and delivers them to GitOps repos using Flux and GitHub PRs. All agent-contributed code must follow the guidance below.

ABSOLUTE REQUIREMENTS
- java-operator-sdk: **5.3.2** (DO NOT change without RFC & explicit approval).
- Java 17+; Maven as build tool.
- No secrets committed. Use environment variables / Kubernetes Secrets.
- All non-trivial code changes must include tests (unit + reconciler integration as applicable).
- Every public class/method must have JavaDoc. Complex logic must have inline comments.

PROJECT LAYOUT (required)
- Base package: `io.kalypso.scheduler`
- CRDs: `io.kalypso.scheduler.api.v1alpha1`
  - `Xxx.java` extends `CustomResource<Spec, Status>`
  - `XxxList.java`
- Reconcilers: `io.kalypso.scheduler.controllers` — one `Reconciler<T>` per CRD
- Services/business logic: `io.kalypso.scheduler.services`
- Models/utilities: `io.kalypso.scheduler.model`
- Operator wiring/config: `io.kalypso.scheduler.operator`
- Exceptions: `io.kalypso.scheduler.exception`
- Tests mirror packages under `src/test/java` (JUnit 5 + Mockito)

CODING & DOCUMENTATION RULES (MANDATORY)
- JavaDoc for all public APIs: purpose, parameters, return, exceptions, short example if helpful.
- Inline comments for non-obvious logic: what, why, alternatives considered.
- Add a one-line summary header in commits and a short paragraph explaining rationale and test evidence.
- No hard-coded values — use `application.properties` or env vars.
- If code is ported/derived from elsewhere, add a single-line "Ported-from: <file/URL/line>" reference.

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

CONTROLLER DEVELOPMENT (java-operator-sdk patterns)
- Implement `io.javaoperatorsdk.operator.api.reconciler.Reconciler<T>` and register the controller (annotation or programmatic registration).
- Return `UpdateControl<T>` or `DeleteControl` appropriately:
  - `UpdateControl.noUpdate()` — nothing changed
  - `UpdateControl.updateStatus()` or `UpdateControl.patchResourceAndRequeue(T)` — when status or spec changes require update or requeue
- Prefer constructor-based dependency injection of `KubernetesClient` and services.
- Use EventSources and EventSourceManager for external watchers; avoid ad-hoc polling in reconcile.
- Field indexing: register indexes for fields frequently used in `List()` queries to avoid full-list scans.
- Always use the status subresource for conditions; do not store transient state in spec or annotations unless deliberate and documented.
- Use finalizers to guarantee cleanup of external resources (Flux artifacts, Git branches) on deletion.
- Avoid long synchronous work in reconcile — use async patterns or offload background tasks and requeue.

CRD MODEL RULES
- Maintain group/version/kind: `scheduler.kalypso.io/v1alpha1`.
- Use Jackson/fabric8 annotations to match existing JSON field names and shapes.
- Provide `Spec` and `Status` classes and set `status` as subresource in CRD YAML.
- Include validation annotations/comments where useful (min length, enum, required).
- Keep CRD schemas stable across releases; any breaking change requires version policy (v1alpha1 → v1beta1) and documentation.

TEMPLATE PROCESSING
- Engine: Freemarker (recommended). Centralize templating in `TemplateProcessingService`.
- Provide standard functions to templates: `toYaml(Object)`, `stringify(Object)`, `hash(Object)`, `unquote(String)`.
- Define standard template context: DeploymentTargetName, Namespace, Environment, Workspace, Workload, Labels (Map<String,String>), ClusterType, ConfigData (Map<String,Object>), Repo, Path, Branch.
- Unit-test templates with input fixtures + expected outputs. Include negative tests for missing keys and type mismatches.

FLUX INTEGRATION
- Implement `FluxService` to create/delete Flux CRDs (GitRepository, Kustomization).
- Prefer fabric8 models if available; otherwise define minimal Jackson POJOs for Flux CRDs and post via the Fabric8 `KubernetesClient.customResource(...)`.
- Make flux namespace, repo secret name, and API group/version configurable.
- Use ownerReferences and labels where applicable so resources are traceable to the originating CR.

GITHUB / PR CREATION
- Implement `GitHubService` using a stable library (e.g., org.kohsuke:github-api) and minimal scopes for tokens.
- Use deterministic branch naming and directory layout for PR content.
- Make all GitHub tokens/config provided via secrets/env vars and documented in README/CI.
- Add retry/backoff and rate-limit handling for GitHub operations; surface errors in resource status.

VALIDATION & SCHEMA
- Use JSON Schema validation (everit or similar) for config validation against `ConfigSchema` CRDs.
- Validation errors must be reflected as status conditions (Ready=False) with clear messages and remediation steps.

TESTING & CI
- Unit tests: JUnit 5 + Mockito. Aim for fast, deterministic tests for services and utilities.
- Reconciler unit tests: mock `KubernetesClient` and dependencies; assert UpdateControl and side-effects.
- Integration tests: use java-operator-sdk testing utilities or fabric8 mock server. Verify whole reconcile flows.
- E2E tests: Kind/Testcontainers with mocked GitHub endpoint.
- CI must run `mvn clean verify`. Long-running integration/E2E may run in nightly pipelines.
- Tests must be runnable locally: provide README steps.

KUBERNETES OPERATOR BEST PRACTICES (ADOPT THESE)
1. Idempotency
   - Reconcile must be idempotent: running multiple times leads to the same end state.
   - Use `Create/Update` semantic with checks for existing resources and compare-and-update patterns (avoid blind replace).
2. Small, fast reconcile loops
   - Keep `reconcile()` orchestration-only; push heavy CPU or network work to services or background workers.
   - If long-running work is needed, use asynchronous orchestration and requeue with status updates.
3. Use status subresource for observed state
   - Keep spec user-declared desired state; only set observed values and conditions in status.
   - Always persist status via status update APIs; avoid storing transient state in spec.
4. Finalizers for cleanup
   - Use finalizers to ensure external systems (Flux resources, Git branches) are cleaned up before resource deletion.
   - Remove finalizer only after cleanup is successful.
5. OwnerReferences & Garbage Collection
   - Use ownerReferences for child resources when appropriate so Kubernetes garbage collects them.
   - For cross-namespace resources (Flux in `flux-system`), use labels and explicit deletion (ownerReferences cannot cross namespaces).
6. Field indexing & watchers
   - Register field indexes for frequently queried fields (e.g., assignments by `spec.clusterType`) to avoid expensive list operations.
   - Use event-driven watches for dependent resources to avoid polling.
7. Rate limiting & backoff
   - Respect API server limits; implement backoff and limited retry on API failures.
   - Use leader election for high-availability operators to avoid double-processing.
8. Concurrency & thread-safety
   - Avoid shared mutable state across reconcilers; use thread-safe client instances (fabric8 client is thread-safe).
   - Limit per-resource concurrency to avoid race conditions; java-operator-sdk has configuration for controller concurrency.
9. Minimal privileges
   - Restrict RBAC to minimum required permissions; avoid cluster-admin unless required and documented.
10. Observability
    - Emit structured logs (key fields) and useful status conditions.
    - Add metrics and health/readiness probes for operator process.
11. Deterministic resource naming
    - Avoid randomized names for resources that need subsequent updates; use deterministic names derived from the owner resource.
12. Avoid side effects before status is persisted
    - Persist status (or at least record intent) before making irreversible external changes where possible to aid recovery.

COMMON ANTIPATTERNS (DO NOT DO THESE)
- Long blocking operations in reconcile
  - Anti: Calling slow external APIs or running CPU-intensive tasks synchronously inside `reconcile()`.  
  - Consequence: Timeouts, slow requeues, poor operator responsiveness.
  - Fix: Offload to a background worker / async tasks and requeue; report progress via status.
- Full cluster list on every reconcile
  - Anti: Doing `client.list()` for resources across the cluster/namespace without index/selector every reconcile.
  - Consequence: O(n) cost each reconcile; high API server load.
  - Fix: Use field selectors, label selectors, and field indexing; cache where safe.
- Mutating spec during reconcile
  - Anti: Writing changes into the resource's spec (user-declared desired state).
  - Consequence: User confusion and unexpected drift.  
  - Fix: Use status for operator-owned state; only modify spec when it is user-intended.
- Silent error swallowing
  - Anti: Catching exceptions and not returning an error or setting status condition.
  - Consequence: Failures invisible, operator appears healthy while not functioning.
  - Fix: Log errors with context and set status condition (Ready=False) and return requeue or error.
- Global mutable state
  - Anti: Using static global caches or mutable singletons to store resource state across threads.
  - Consequence: Race conditions, hard-to-debug failures.
  - Fix: Use thread-safe caches or client-provided caches; prefer stateless reconcilers with injected services.
- Hard-coded resource names, tokens, or cluster details
  - Anti: Embedding secrets, cluster URLs, or non-configurable names in code.
  - Consequence: Inflexible deployments and security risk.
  - Fix: Read from `application.properties` or environment and document required secrets.
- Creating resources with unpredictable names
  - Anti: Using random suffixes for child resources that you must later update.
  - Consequence: Hard to detect and update existing resources reliably.
  - Fix: Use deterministic names derived from owner metadata.
- Overly broad RBAC
  - Anti: Granting cluster-admin to the operator by default.
  - Consequence: Security risk and least-privilege violation.
  - Fix: Enumerate exact resources the operator needs (CRDs, core resources) and create minimal Role/ClusterRole.
- Assumptions about event ordering
  - Anti: Assuming a create event will always arrive before related update events.
  - Consequence: Missed reconciles and race conditions.
  - Fix: Design reconcile to be idempotent and recover from any event order.
- Logging sensitive information
  - Anti: Logging secrets, tokens, or unredacted config data.
  - Consequence: Leaked secrets in logs.
  - Fix: Mask secrets and only log safe fields.

BRANCHING & PR RULES
- Branch names:
  - `day-N-<short-desc>` for daily tasks
  - `feature/<short-desc>` for features
- PR title format: `[Day N] <Feature> - short description`
- PR description must include: what/why/how tested, file list, config steps, references to design/plan.
- Commit messages: short summary, blank line, detailed explanation. Link MIGRATION_PLAN.md or issue if applicable.

PR REVIEW CHECKLIST (MUST COMPLETE)
- [ ] Build passes: `mvn clean package`
- [ ] Unit tests added & passing: `mvn test`
- [ ] Integration tests added (or test plan presented)
- [ ] JavaDoc added for public APIs
- [ ] Inline comments for complex logic
- [ ] No hard-coded credentials
- [ ] `application.properties` documented/updated
- [ ] Logging at appropriate levels
- [ ] java-operator-sdk remains 5.3.2 (or RFC exists)

TROUBLESHOOTING & DEBUGGING
- Run locally with kubeconfig: `mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"`
- Increase logging in `logback.xml` or `application.properties` for debug.
- Use fabric8 mock server or operator-sdk-testing for unit/integration tests.
- For GitHub: use sandbox repo + short-lived token for testing PR flows.

REFERENCES
- java-operator-sdk: https://javaoperatorsdk.io/
- fabric8 Kubernetes client: https://github.com/fabric8io/kubernetes-client
- Freemarker: https://freemarker.apache.org/
- Kohsuke GitHub API: https://github.com/kohsuke/github-api
- Jackson YAML: https://github.com/FasterXML/jackson-dataformats-text

GOVERNANCE
- This file is authoritative for agent behavior in this repository. Changes to mandatory rules (docs policy, pinned SDK version, test requirements) must be proposed by an RFC-style PR and approved by maintainers.

**Last updated:** YYYY-MM-DD