# Kalypso Scheduler Java Operator

[![Build Status](https://github.com/ssijbabu/kalypso-scheduler-java/actions/workflows/build.yml/badge.svg)](https://github.com/ssijbabu/kalypso-scheduler-java/actions)

A Java implementation of the Kalypso Scheduler Kubernetes operator, migrated from the original [Go implementation](https://github.com/microsoft/kalypso-scheduler).

## Overview

Kalypso Scheduler is responsible for scheduling applications and services on cluster types and uploading the result to the GitOps repo. This Java version uses **java-operator-sdk 5.3.2** to provide the same functionality with additional benefits of the Java ecosystem.

**Migration Status**: In Progress (See [MIGRATION_PLAN.md](./MIGRATION_PLAN.md) for detailed progress)

## Features

- ✅ Manages high-level control plane abstractions (ClusterType, Workload, Environment, etc.)
- ✅ Transforms abstractions into low-level Kubernetes manifests
- ✅ Creates pull requests to GitOps repositories with generated manifests
- ✅ Integrates with Flux for resource synchronization
- 🔄 Template-based manifest generation (using Freemarker)
- 🔄 GitHub API integration for PR creation
- 🔄 Configuration validation and management

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8.0 or higher
- Kubernetes 1.24+ cluster (or Kind/Minikube for local development)
- Flux installed on the cluster

### Building

```bash
git clone https://github.com/ssijbabu/kalypso-scheduler-java.git
cd kalypso-scheduler-java
mvn clean package
```

### Running Locally

```bash
mvn exec:java -Dexec.mainClass="io.kalypso.scheduler.KalypsoSchedulerOperator"
```

### Building Docker Image

```bash
docker build -t kalypso-scheduler:latest .
```

### Deploying to Kubernetes

```bash
# Create namespace
kubectl create namespace kalypso

# Apply operator
kubectl apply -f deploy/operator.yaml -n kalypso

# Check status
kubectl logs -n kalypso -l app=kalypso-scheduler -f
```

## Architecture

### Directory Structure

```
src/main/java/io/kalypso/scheduler/
├── api/v1alpha1/                    # CRD definitions
│   ├── Template.java
│   ├── ClusterType.java
│   ├── Environment.java
│   ├── Workload.java
│   ├── DeploymentTarget.java
│   ├── SchedulingPolicy.java
│   ├── Assignment.java
│   ├── AssignmentPackage.java
│   ├── GitOpsRepo.java
│   ├── ConfigSchema.java
│   ├── BaseRepo.java
│   └── spec/                        # Spec/Status classes
├── controllers/                     # Reconcilers
│   ├── BaseRepoReconciler.java
│   ├── EnvironmentReconciler.java
│   ├── WorkloadRegistrationReconciler.java
│   ├── WorkloadReconciler.java
│   ├── SchedulingPolicyReconciler.java
│   ├── AssignmentReconciler.java
│   ├── AssignmentPackageReconciler.java
│   ├── GitOpsRepoReconciler.java
│   └── shared/                      # Shared utilities
├── services/                        # Business logic
│   ├── FluxService.java
│   ├── TemplateProcessingService.java
│   ├── GitHubService.java
│   └── ConfigValidationService.java
├── model/                           # Data models
│   ├── TemplateContext.java
│   └── TemplateProcessingResult.java
├── operator/                        # Operator configuration
│   └── OperatorConfiguration.java
└── KalypsoSchedulerOperator.java   # Main entry point
```

### CRDs (Custom Resource Definitions)

The operator manages the following CRDs (all in `scheduler.kalypso.io/v1alpha1` group):

1. **Template** - Template definitions for reconcilers, namespaces, and config
2. **ClusterType** - Cluster type abstractions
3. **Environment** - Deployment environments (dev, stage, prod)
4. **WorkloadRegistration** - Git repository references for workloads
5. **Workload** - Application workload definitions
6. **DeploymentTarget** - Deployment targets within workloads
7. **SchedulingPolicy** - Policies for scheduling targets to cluster types
8. **Assignment** - Assignment of deployment targets to cluster types
9. **AssignmentPackage** - Generated manifests for assignments
10. **GitOpsRepo** - GitOps repository targets for PR creation
11. **ConfigSchema** - Configuration schema definitions
12. **BaseRepo** - Base repository references

### Reconcilers

| Reconciler | Purpose |
|---|---|
| BaseRepoReconciler | Creates Flux resources for base repositories |
| EnvironmentReconciler | Manages environment-specific resources |
| WorkloadRegistrationReconciler | Fetches workload definitions from git |
| WorkloadReconciler | Manages deployment targets within workloads |
| SchedulingPolicyReconciler | Schedules targets to cluster types |
| AssignmentReconciler | Generates manifests via template processing |
| AssignmentPackageReconciler | Validates and manages manifest packages |
| GitOpsRepoReconciler | Creates PRs to GitOps repositories |

## Configuration

Configuration is managed via `application.properties`:

```properties
# Kubernetes
kubernetes.namespace=kalypso
kubernetes.client.retries=3

# Flux Integration
flux.default-namespace=flux-system

# Template Processing
template.engine=freemarker
template.cache-enabled=true

# GitHub
github.api-endpoint=https://api.github.com

# Reconciliation
reconciliation.retry-delay-seconds=5
reconciliation.max-retries=3
```

## Development

### Running Tests

```bash
mvn test
```

### Building with Tests

```bash
mvn clean verify
```

### Code Style

- Follow Java naming conventions (PascalCase for classes, camelCase for methods)
- All public classes and methods must have JavaDoc comments
- Use SLF4J for logging
- Add unit tests for all new functionality

## Logging

Logs are configured via `logback.xml` and written to:
- Console (INFO and above)
- File: `logs/kalypso-scheduler.log`

Set log level in `application.properties`:

```properties
logging.level.io.kalypso=DEBUG
logging.level.io.javaoperatorsdk=INFO
```

## Migration from Go

This is a line-by-line migration from the Go Kubebuilder implementation. For details on the migration process, see [MIGRATION_PLAN.md](./MIGRATION_PLAN.md).

**Key Mappings**:
- Go `controller-runtime` → Java `java-operator-sdk`
- Go `text/template` → Java `Freemarker`
- Go `github.com/google/go-github` → Java `kohsuke/github`
- Go `controller-runtime` RBAC markers → Java `@RBAC` annotations

## Contributing

Contributions are welcome! Please note:

1. All changes must include comprehensive documentation (JavaDoc + comments)
2. All new features must have unit tests
3. Follow the coding standards in [.github/copilot-instructions.md](./.github/copilot-instructions.md)
4. Update this README if you change functionality

## Issues & Support

For issues or questions:
1. Check [MIGRATION_PLAN.md](./MIGRATION_PLAN.md) for status
2. Review [.github/copilot-instructions.md](./.github/copilot-instructions.md) for guidelines
3. Create an issue on GitHub with details

## License

This project is part of the Kalypso project and follows the same license as the original [Go implementation](https://github.com/microsoft/kalypso-scheduler).

## Related Projects

- [Original Go Implementation](https://github.com/microsoft/kalypso-scheduler)
- [Kalypso Control Plane](https://github.com/microsoft/kalypso-control-plane)
- [java-operator-sdk](https://javaoperatorsdk.io/)

---

**Current Status**: Day 0 - Bootstrap Complete ✅  
**Next Milestone**: Day 1 - Template & ClusterType CRDs

See [MIGRATION_PLAN.md](./MIGRATION_PLAN.md) for detailed progress and roadmap.
