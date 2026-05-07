# Day 7 Summary — TemplateProcessingService, GitHubService & ConfigValidationService

## Completed Tasks

### ✅ Exception Classes (3 new)

- [x] `exception/TemplateProcessingException.java` — unchecked exception wrapping Freemarker errors
- [x] `exception/GitHubServiceException.java` — unchecked exception wrapping GitHub API IOExceptions
- [x] `exception/ConfigValidationException.java` — carries `List<String> validationErrors` for multi-error reporting

### ✅ Model (1 new)

- [x] `model/TemplateContext.java`
  - Immutable data bag passed to Freemarker, equivalent to Go's `dataType` struct in `scheduler/templater.go`
  - Fields: `deploymentTargetName`, `namespace`, `environment`, `workspace`, `workload`, `labels`, `manifests`, `clusterType`, `configData`
  - `toMap()` returns PascalCase-keyed map matching Go template variable names (`DeploymentTargetName`, `Namespace`, etc.)
  - Convenience keys `Repo`, `Branch`, `Path` extracted from `manifests` for direct use in templates
  - Fluent `Builder` with empty-string / empty-map defaults for partial construction in tests

### ✅ Services (3 new)

#### `services/TemplateProcessingService.java`

Java equivalent of `scheduler/templater.go`:
- `processTemplate(String templateSource, TemplateContext context)` — renders a Freemarker template against the context
- Recursive re-processing: if the rendered output still contains `${` or `<#`, the result is re-processed (up to `MAX_RECURSION_DEPTH = 5`). Mirrors Go's `replaceTemplateVariables` recursive check for `{{`.
- Static `buildTargetNamespace(DeploymentTarget, ClusterType)` — produces `{environment}-{clusterTypeName}-{deploymentTargetName}`, directly mapping Go's `buildTargetNamespace`
- Custom Freemarker functions registered as shared variables:

| Freemarker | Go funcMap | Purpose |
|---|---|---|
| `toYaml` | `toYaml` (yaml.Marshal) | Serialize object to YAML |
| `stringify` | `stringify` | Serialize map to YAML string |
| `hash` | `hash` (hashstructure) | Stable unsigned hash for naming |
| `unquote` | `unquote` | Strip surrounding quotes/whitespace |

#### `services/GitHubService.java`

Java equivalent of `scheduler/githubrepo.go`:
- `createPullRequest(repoFullName, prTitle, baseBranch, fileContents)` — atomic multi-file commit using GitHub Trees API; cleans old Kalypso PRs first
- `isPromoted(repoFullName, branch)` — checks `.github/tracking/Promoted_Commit_Id` existence
- `cleanPullRequests(repoFullName, baseBranch)` — closes all open PRs with head branch starting with `kalypso-`
- Static `buildFilePath(basePath, clusterType, deploymentTarget, fileName)` — constructs `{basePath}/{clusterType}/{deploymentTarget}/{fileName}`
- Go constant equivalents:

| Go | Java |
|---|---|
| `reconcilerName = "reconciler"` | `RECONCILER_NAME` |
| `namespaceName  = "namespace"` | `NAMESPACE_NAME` |
| `configName     = "platform-config"` | `CONFIG_NAME` |
| `"Kalypso Scheduler"` | `AUTHOR_NAME` |
| `"kalypso.scheduler@email.com"` | `AUTHOR_EMAIL` |
| `"Kalypso Scheduler commit"` | `COMMIT_MESSAGE` |
| `os.Getenv("GITHUB_AUTH_TOKEN")` | reads `GITHUB_AUTH_TOKEN` env var |

#### `services/ConfigValidationService.java`

Java equivalent of `scheduler/config_validator.go`:
- `validateValues(Map<String, Object> values, String schema)` — validates against a JSON Schema string using everit-json-schema
- Type coercion before validation (matching Go's `gojsonschema` coercion): `"integer"` → `Long`, `"number"` → `Double`, `"boolean"` → `Boolean`
- All validation violations collected and surfaced via `ConfigValidationException.getValidationErrors()`

### ✅ Unit Tests (27 new total for Day 7 test classes)

| Class | Tests | What's covered |
|---|---|---|
| `TemplateProcessingServiceTest` | 9 | Simple interpolation, all context keys, toYaml, stringify, hash, unquote, recursion, recursion guard, invalid template |
| `GitHubServiceTest` | 8 | buildFilePath (4 variants), file name constants, author/commit constants, null-token error, blank-token error |
| `ConfigValidationServiceTest` | 10 | Valid values, missing required, string→int coercion, string→number coercion, string→boolean coercion, wrong type fails, coerceTypes int/number/boolean/unconvertible |

### ✅ MIGRATION_PLAN.md updated

- [x] Day 7 marked `[x]`

---

## Key Design Decisions

### `toJavaObject` instead of `DeepUnwrap.unwrap`

The Freemarker `TemplateMethodModelEx.exec(List args)` receives `TemplateModel` instances. When `DefaultObjectWrapper` wraps a `java.util.Map` as `SimpleHash` (the non-adapter path), `DeepUnwrap.unwrap()` only handles `AdapterTemplateModel` and `WrapperTemplateModel` — it throws for `SimpleHash`.

The `toJavaObject` helper handles all model types:
1. `AdapterTemplateModel` → `getAdaptedObject()` (handles `DefaultMapAdapter` when adapters are enabled)
2. `TemplateHashModelEx` → iterate keys/values manually (handles `SimpleHash`)
3. `TemplateSequenceModel` → iterate by index
4. `TemplateScalarModel` / `TemplateNumberModel` / `TemplateBooleanModel` → primitive extraction

This makes the function methods work correctly regardless of whether the `DefaultObjectWrapper` uses the adapter path.

### `YAMLGenerator.Feature.MINIMIZE_QUOTES`

Jackson YAML 2.16 (backed by SnakeYAML 2.x) quotes all string values by default (e.g. `region: "eastus"`). Go's `yaml.Marshal` does not quote simple strings. The `YAMLMapper` is configured with `MINIMIZE_QUOTES` to produce unquoted output matching Go's behaviour and making the YAML embeddable in manifests without extra processing.

### `WrapperTemplateModel` does not exist in Freemarker 2.3.32

The `freemarker.template.WrapperTemplateModel` interface was removed from Freemarker's public API in 2.3.32. Any code importing it fails to compile. The `toJavaObject` helper handles all cases without needing this interface.

### GitHub service: Trees API for atomic commits

Instead of calling `GHRepository.createContent()` once per file (which creates N separate commits), the service uses the GitHub Trees API:
1. Build a `GHTree` with all files via `repo.createTree().add(path, content, false).baseTree(sha).create()`
2. Create a single commit pointing at the new tree
3. Create a branch pointing at the new commit
4. Open a PR

This mirrors Go's `getTree` function in `githubrepo.go` which builds the full tree before committing.

### Token validation at call time, not construction time

`GitHubService` accepts a null or blank token in the constructor and only throws `GitHubServiceException` when an API method is actually called. This allows the service to be constructed during Spring/operator startup even if the `GITHUB_AUTH_TOKEN` env var is not set, failing fast only when a PR operation is actually requested.

---

## Issues Encountered and Resolved

| # | Issue | Root Cause | Fix |
|---|---|---|---|
| 1 | `toYaml` / `stringify` test failure — output missing expected key | `DeepUnwrap.unwrap()` cannot unwrap `SimpleHash` (Freemarker's default Map wrapper), returns or throws unexpectedly | Replaced with `toJavaObject()` helper that handles all TemplateModel subtypes including `TemplateHashModelEx` iteration |
| 2 | `WrapperTemplateModel cannot be resolved` compile error | `WrapperTemplateModel` was removed from `freemarker.template` in 2.3.32's public API | Removed the `WrapperTemplateModel` check; `AdapterTemplateModel` handles the adapter case |
| 3 | `toYaml` output `region: "eastus"` instead of `region: eastus` | Jackson YAML 2.16 (SnakeYAML 2.x) quotes all strings by default | Added `yamlMapper.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)` to match Go's `yaml.Marshal` output |
| 4 | IDE reported `GHRepository cannot be resolved` after removing unused `GHRef` import | Stale IDE diagnostics immediately after edit; import was still present | Verified by reading file; real import intact, compilation passes cleanly |
| 5 | `assertEquals(double, Object, double, String)` not applicable | `result.get("ratio")` returns `Object`; JUnit's overload requires `double` | Added explicit `(double)` cast: `assertEquals(2.5, (double) result.get("ratio"), 1e-9, ...)` |

---

## Project Structure After Day 7

```
src/main/java/io/kalypso/scheduler/
├── exception/
│   ├── TemplateProcessingException.java    # new
│   ├── GitHubServiceException.java         # new
│   └── ConfigValidationException.java      # new
├── model/
│   └── TemplateContext.java                # new
└── services/
    ├── FluxService.java                    # existing
    ├── TemplateProcessingService.java      # new
    ├── GitHubService.java                  # new
    └── ConfigValidationService.java        # new

src/test/java/io/kalypso/scheduler/
└── services/
    ├── FluxServiceTest.java                # existing
    ├── TemplateProcessingServiceTest.java  # new — 9 unit tests
    ├── GitHubServiceTest.java              # new — 8 unit tests
    └── ConfigValidationServiceTest.java    # new — 10 unit tests

MIGRATION_PLAN.md                          # updated — Day 7 marked [x]
DAY7_SUMMARY.md                            # this file
docs/
├── flux-service.md                        # existing
├── template-processing-service.md         # new
├── github-service.md                      # new
└── config-validation-service.md           # new
```

---

## Build Verification

```bash
mvn clean verify
```

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0   ← new Day 7 unit tests
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0   ← integration tests (all passing)
[INFO] BUILD SUCCESS
[INFO] Total time:  ~14 s
```

### Cumulative test count

| Day | New unit tests | Running total (unit / IT) |
|---|---|---|
| 0–5 | 65 | 65 / 19 |
| 6 | 5 | 70 / 20 |
| 7 | 27 | 97 / 20 |

---

## Ready for Day 8

**Day 8 Tasks** (Next):
- [ ] Implement `BaseRepoReconciler` — creates/deletes Flux resources via `FluxService`
- [ ] Implement `EnvironmentReconciler` — creates/deletes namespace + Flux resources
- [ ] Add finalizers for cleanup on deletion
- [ ] Status condition management helper
- [ ] Unit tests for both reconcilers

---

**Status**: ✅ Day 7 COMPLETE — TemplateProcessingService, GitHubService, ConfigValidationService implemented and tested

**Next Milestone**: Day 8 — BaseRepoReconciler & EnvironmentReconciler

---

*Created: 2026-05-07*
*Framework: java-operator-sdk 5.3.2*
*Build Tool: Maven*
*Java Version: 17 (runtime: 25)*
