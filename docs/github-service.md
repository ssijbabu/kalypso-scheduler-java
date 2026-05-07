# GitHubService — Developer Guide

## Who this is for

This document explains the `GitHubService` class from scratch. No prior knowledge
of the GitHub API, GitOps, or the Go operator is assumed. If you can read Java and
you know what a Pull Request is, you have enough background.

---

## 1. The problem GitHubService solves

The Kalypso operator's end goal is to put rendered Kubernetes manifests into a
**GitOps repository** — a GitHub repo whose contents are automatically applied to
clusters by Flux. Rather than pushing directly to the main branch, the operator
opens a **Pull Request** so that humans can review and approve the changes.

`GitHubService` is the only place in the codebase that interacts with GitHub. It
creates branches, commits files, and opens PRs.

---

## 2. Authentication

The GitHub API requires a token to authenticate. The service reads this token from
the `GITHUB_AUTH_TOKEN` environment variable:

```bash
export GITHUB_AUTH_TOKEN=ghp_your_personal_access_token
```

This matches the Go operator which uses `os.Getenv("GITHUB_AUTH_TOKEN")`.

The token must have at least `repo` scope (read/write access to the target repository).

The service does **not** validate the token at construction time — it only throws
`GitHubServiceException` when an API method is actually called with a missing or
blank token. This allows the operator to start without the env var if no PRs are
needed yet.

---

## 3. The file structure inside the GitOps repo

When the operator creates a PR, the manifests are organized into a specific directory
tree inside the GitOps repository. This structure matches the Go operator's `getTree`
function in `githubrepo.go`:

```
{basePath}/
  {clusterType}/
    {deploymentTarget}/
      reconciler.yaml     (or reconciler.sh for shell-script reconcilers)
      namespace.yaml      (or namespace.sh)
      platform-config.yaml
```

The `basePath` comes from `GitOpsRepo.spec.path` (e.g. `./clusters`). The leading
`./` is stripped because GitHub API paths must not start with `./`.

**Example** for `basePath = "./clusters"`, `clusterType = "aks"`,
`deploymentTarget = "prod-east"`:

```
clusters/aks/prod-east/reconciler.yaml
clusters/aks/prod-east/namespace.yaml
clusters/aks/prod-east/platform-config.yaml
```

Use the static helper `buildFilePath` to construct these paths:

```java
String path = GitHubService.buildFilePath("./clusters", "aks", "prod-east", "reconciler.yaml");
// Returns: "clusters/aks/prod-east/reconciler.yaml"
```

### 3.1 File name constants

The three file names are declared as public constants to avoid magic strings
throughout the codebase. They match the Go operator constants exactly:

| Java constant | Value | Go constant |
|---|---|---|
| `RECONCILER_NAME` | `"reconciler"` | `reconcilerName = "reconciler"` |
| `NAMESPACE_NAME` | `"namespace"` | `namespaceName = "namespace"` |
| `CONFIG_NAME` | `"platform-config"` | `configName = "platform-config"` |

The file extension (`.yaml` or `.sh`) comes from the content type of each manifest.

---

## 4. Creating a pull request — step by step

```java
Map<String, String> fileContents = new HashMap<>();
fileContents.put("clusters/aks/prod-east/reconciler.yaml", reconcilerContent);
fileContents.put("clusters/aks/prod-east/namespace.yaml", namespaceContent);

GHPullRequest pr = service.createPullRequest(
    "org/gitops-repo",      // full GitHub repo name
    "Kalypso manifest update for prod-east",  // PR title
    "main",                 // base branch
    fileContents            // path → content map
);
```

**What happens internally:**

**Step 1 — Clean up old PRs** (`cleanPullRequests`)

Any existing open PR whose head branch starts with `kalypso-` and targets the same
base branch is closed. This ensures there is only one active Kalypso PR at a time.
The head branches of closed PRs are also deleted.

This mirrors Go's `cleanPullRequests` helper in `githubrepo.go`.

**Step 2 — Get the base commit SHA**

Fetches the current tip commit of the base branch (e.g. `main`). All new commits are
built on top of this SHA.

**Step 3 — Build a Git tree**

Uses the GitHub [Trees API](https://docs.github.com/en/rest/git/trees) to create a
new tree object that contains all the manifest files in a single atomic operation.
This is equivalent to staging multiple files for one commit.

```java
GHTreeBuilder treeBuilder = repo.createTree().baseTree(baseTreeSha);
for (Map.Entry<String, String> file : fileContents.entrySet()) {
    treeBuilder.add(file.getKey(), file.getValue(), false); // path, content, executable
}
GHTree newTree = treeBuilder.create();
```

Using the Trees API (instead of calling `createContent()` per file) creates a
**single commit** with all files. Calling `createContent()` N times would create N
separate commits in the PR history, which is messy.

**Step 4 — Create a commit**

Creates a Git commit object pointing at the new tree, with the parent set to the
base branch's tip commit:

```java
GHCommit newCommit = repo.createCommit()
    .message("Kalypso Scheduler commit")
    .tree(newTree.getSha())
    .parent(baseSha)
    .create();
```

The commit message `"Kalypso Scheduler commit"` matches the Go operator constant.

**Step 5 — Create a new branch**

Creates a branch named `kalypso-{timestamp}` pointing at the new commit. The
`kalypso-` prefix is used by `cleanPullRequests` to identify and clean up Kalypso
branches in future runs.

**Step 6 — Open the PR**

Opens a pull request from the new branch targeting the base branch.

---

## 5. Checking if a branch is promoted

```java
boolean promoted = service.isPromoted("org/gitops-repo", "main");
```

A branch is considered "promoted" when the file
`.github/tracking/Promoted_Commit_Id` exists on that branch. This file is created
by the GitOps pipeline after a successful promotion.

Mirrors Go's `isPromoted` helper in `githubrepo.go`.

Returns `false` (not `throws`) when the file does not exist — a missing file is the
normal case for a branch that has not been promoted yet.

---

## 6. Constants — Go correspondence

All constants match the Go operator values exactly:

| Java | Value | Go |
|---|---|---|
| `AUTHOR_NAME` | `"Kalypso Scheduler"` | commit author name |
| `AUTHOR_EMAIL` | `"kalypso.scheduler@email.com"` | commit author email |
| `COMMIT_MESSAGE` | `"Kalypso Scheduler commit"` | commit message |
| `BRANCH_PREFIX` | `"kalypso-"` | prefix for PR branch names |
| `TOKEN_ENV_VAR` | `"GITHUB_AUTH_TOKEN"` | `os.Getenv("GITHUB_AUTH_TOKEN")` |
| `PROMOTED_COMMIT_TRACKING_FILE` | `".github/tracking/Promoted_Commit_Id"` | tracked file path |

---

## 7. Error handling

All GitHub API `IOException`s are wrapped in `GitHubServiceException` (an unchecked
exception). The caller (a reconciler) is expected to catch this, update the resource's
`status.conditions` to `Ready=False`, and requeue.

---

## 8. Files involved

```
src/main/java/io/kalypso/scheduler/
├── exception/
│   └── GitHubServiceException.java
└── services/
    └── GitHubService.java

src/test/java/io/kalypso/scheduler/
└── services/
    └── GitHubServiceTest.java  8 unit tests (path building + token validation)
```

---

## 9. Correspondence with the Go operator

| Go (`githubrepo.go`) | Java (`GitHubService.java`) |
|---|---|
| `type GithubRepo interface { CreatePullRequest(...) }` | `createPullRequest(repoFullName, prTitle, baseBranch, fileContents)` |
| `isPromoted(...)` | `isPromoted(repoFullName, branch)` |
| `cleanPullRequests(...)` | `cleanPullRequests(repoFullName, baseBranch)` (also private overload) |
| `reconcilerName = "reconciler"` | `RECONCILER_NAME` |
| `namespaceName  = "namespace"` | `NAMESPACE_NAME` |
| `configName     = "platform-config"` | `CONFIG_NAME` |
| `os.Getenv("GITHUB_AUTH_TOKEN")` | `System.getenv(TOKEN_ENV_VAR)` |
| `github.com/google/go-github` | `org.kohsuke:github-api:1.321` |
| Trees API (Go `getTree` func) | `repo.createTree().add(...).baseTree(...).create()` |

---

## 10. Why kohsuke/github-api?

The Go operator uses `github.com/google/go-github`. There is no direct Java port.
`kohsuke/github-api` is the most widely used Java GitHub API client (used by Jenkins,
among others) and provides a clean object-oriented API over the GitHub REST API.

It supports all the operations needed: tree building, commit creation, branch creation,
PR lifecycle (list, create, close).
