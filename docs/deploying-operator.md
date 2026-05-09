# Deploying the Operator Manually

## Prerequisites

- Docker Desktop with Kubernetes enabled (or any cluster with `kubectl` configured)
- Java 17+, Maven 3.x, Docker CLI
- CRDs already installed (or follow step 2 below)

---

## Step 1: Build the JAR and Docker image

```bash
# Full build + CRD generation (generates META-INF/fabric8/*.yml)
mvn clean package -DskipTests

# Build the Docker image (tagged locally — no registry push needed for Docker Desktop)
docker build -t kalypso-scheduler:latest .
```

> The shaded JAR (`target/kalypso-scheduler-*.jar`) is embedded in the image at `/app/app.jar`.

---

## Step 2: Apply CRDs

```bash
kubectl apply -f target/classes/META-INF/fabric8/
```

This installs all 12 Kalypso CRDs into the cluster. Safe to re-run; existing CRDs are updated, not replaced.

---

## Step 3: Deploy the operator

```bash
kubectl apply -f k8s/
```

This creates:
- `namespace/kalypso-java`
- `serviceaccount/kalypso-scheduler` in `kalypso-java`
- `clusterrole/kalypso-scheduler-java` + binding (full access to all Kalypso CRDs and status subresources)
- `deployment/kalypso-scheduler` in `kalypso-java`

Wait for the pod to be ready:

```bash
kubectl rollout status deployment/kalypso-scheduler -n kalypso-java --timeout=2m
```

---

## Step 4: Verify startup

```bash
kubectl logs -n kalypso-java deployment/kalypso-scheduler --tail=40
```

A healthy startup produces one `controller started` line per reconciler:

```
'workloadreconciler' controller started
'schedulingpolicyreconciler' controller started
'assignmentpackagereconciler' controller started
...
Kalypso Scheduler Operator started successfully
```

If you see `NoSuchMethodError: HasMetadata.initNameAndNamespaceFrom`, fabric8 and JOSDK versions are mismatched — ensure `kubernetes.client.version=7.6.1` in `pom.xml`.

---

## Step 5: Tear down

```bash
kubectl delete -f k8s/
```

CRDs are intentionally left intact so the existing Go operator (if co-deployed) is unaffected.

---

## Useful kubectl commands during development

```bash
# Tail operator logs
kubectl logs -n kalypso-java deployment/kalypso-scheduler -f

# List all Kalypso resources in the namespace
kubectl get workloads,deploymenttargets,schedulingpolicies,assignments,assignmentpackages -n kalypso-java

# Describe a specific reconciled resource and its conditions
kubectl describe assignmentpackage <name> -n kalypso-java

# Force-restart the operator (picks up new image if imagePullPolicy=Never + rebuild)
kubectl rollout restart deployment/kalypso-scheduler -n kalypso-java

# Re-apply just the CRDs (safe after mvn clean package regenerates schemas)
kubectl apply -f target/classes/META-INF/fabric8/
```

---

## Iterating on code changes

```bash
# 1. Rebuild JAR and image
mvn clean package -DskipTests && docker build -t kalypso-scheduler:latest .

# 2. Restart the deployment (Docker Desktop shares the daemon — no push needed)
kubectl rollout restart deployment/kalypso-scheduler -n kalypso-java
kubectl rollout status deployment/kalypso-scheduler -n kalypso-java --timeout=2m
```

> `imagePullPolicy: Never` is set in `k8s/02-deployment.yaml` so Kubernetes uses the locally-built image without a registry.

---

## Environment variables

The operator reads configuration from `application.properties` (bundled in the JAR). Secrets are passed via environment variables at pod level:

| Variable | Purpose |
|---|---|
| `GITHUB_TOKEN` | Personal access token for `GitHubService` to open PRs |
| `OPERATOR_NAMESPACE` | Injected via `fieldRef: metadata.namespace` (do not set manually) |

To pass `GITHUB_TOKEN` during local testing, add it to `k8s/02-deployment.yaml` under `env:` before deploying.
