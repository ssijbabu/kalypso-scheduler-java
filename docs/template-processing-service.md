# TemplateProcessingService — Developer Guide

## Who this is for

This document explains the `TemplateProcessingService` class from scratch. No prior
knowledge of template engines, Freemarker, or the Go operator is assumed. If you can
read Java and you know roughly what a Kubernetes manifest looks like (a YAML file with
`apiVersion`, `kind`, `metadata`, etc.), you have enough background.

---

## 1. The problem TemplateProcessingService solves

The Kalypso operator needs to produce different Kubernetes manifests for every
(cluster type, deployment target) combination. For example, a Flux `Kustomization`
resource for a production cluster in East US looks different from the one for a
staging cluster in West Europe — even though the structure is the same.

Instead of hard-coding those differences, the operator stores **templates** inside
`Template` CRD objects. A template is a text file with placeholder variables, like:

```yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: ${Workload}-${DeploymentTargetName}
  namespace: ${Namespace}
spec:
  path: ./${Environment}/${ClusterType}
```

`TemplateProcessingService` fills in those placeholders with real values from the
context of the current reconciliation loop.

---

## 2. What is Freemarker?

Freemarker is a Java template engine — the Java equivalent of Go's `text/template`
package. You give it a text string with `${VariableName}` markers and a data map,
and it produces the final text with all variables replaced.

Freemarker is more powerful than simple string substitution:
- It supports conditionals: `<#if Environment == "prod">...</#if>`
- It supports loops: `<#list Labels?keys as key>${key}: ${Labels[key]}</#list>`
- It supports custom functions: `${toYaml(Labels)}`

The Kalypso operator uses version **2.3.32** of Freemarker (declared in `pom.xml`).

---

## 3. The template data — TemplateContext

Before a template can be rendered, we need a **data bag** — a Java object holding all
the values that can be referenced in the template. This is the `TemplateContext` class.

It is the Java equivalent of the Go `dataType` struct in `scheduler/templater.go`:

```go
// Go original
type dataType struct {
    DeploymentTargetName string
    Namespace            string
    Environment          string
    Workspace            string
    Workload             string
    Labels               map[string]string
    Manifests            map[string]string
    ClusterType          string
    ConfigData           map[string]interface{}
}
```

The Java equivalent adds convenience fields `Repo`, `Branch`, `Path` extracted from
`Manifests` so templates can write `${Repo}` instead of `${Manifests.repo}`.

### 3.1 Building a TemplateContext

Use the fluent builder:

```java
TemplateContext ctx = new TemplateContext.Builder()
    .deploymentTargetName("prod-east")
    .namespace("prod-aks-prod-east")        // output of buildTargetNamespace()
    .environment("prod")
    .workspace("team-payments")
    .workload("payment-service")
    .clusterType("aks-large")
    .labels(Map.of("region", "eastus", "tier", "production"))
    .configData(Map.of("DB_URL", "postgres://...", "REPLICA_COUNT", "3"))
    .manifests(Map.of("repo", "https://github.com/org/infra",
                      "branch", "main",
                      "path", "./clusters/prod-east"))
    .build();
```

### 3.2 Template variable names

All template variables use **PascalCase** to match the Go `dataType` field names.
The full list:

| Template variable | Java field | Go field |
|---|---|---|
| `${DeploymentTargetName}` | `deploymentTargetName` | `DeploymentTargetName` |
| `${Namespace}` | `namespace` | `Namespace` |
| `${Environment}` | `environment` | `Environment` |
| `${Workspace}` | `workspace` | `Workspace` |
| `${Workload}` | `workload` | `Workload` |
| `${Labels}` | `labels` | `Labels` |
| `${Manifests}` | `manifests` | `Manifests` |
| `${ClusterType}` | `clusterType` | `ClusterType` |
| `${ConfigData}` | `configData` | `ConfigData` |
| `${Repo}` | extracted from `manifests["repo"]` | N/A (convenience) |
| `${Branch}` | extracted from `manifests["branch"]` | N/A (convenience) |
| `${Path}` | extracted from `manifests["path"]` | N/A (convenience) |

---

## 4. The custom template functions

The Go operator registers a `funcMap` with four custom functions. The Java service
registers equivalent functions as Freemarker `TemplateMethodModelEx` instances.

### 4.1 `toYaml`

Serializes any value (map, list, string) to YAML format. The result has no leading
`---` separator and no trailing newline.

**Go**: `yaml.Marshal(obj)` then trim trailing newline  
**Java**: `YAMLMapper.writeValueAsString(obj)` with `MINIMIZE_QUOTES` enabled, strip `---\n`

Template usage:
```
${toYaml(Labels)}
```

Example output for `Labels = {region: eastus, tier: production}`:
```
region: eastus
tier: production
```

### 4.2 `stringify`

Converts a map to a YAML-style multi-line string. Useful for embedding `ConfigData`
as a block literal. For non-map values, returns `String.valueOf(value)`.

Template usage:
```
${stringify(ConfigData)}
```

### 4.3 `hash`

Returns a stable unsigned numeric string derived from the JSON representation of the
argument. Used for deterministic naming in templates.

**Go**: `hashstructure.Hash(obj, hashstructure.FormatV2, nil)` → `uint64`  
**Java**: polynomial hash of JSON bytes → `Long.toUnsignedString(hash)`

The exact numeric value differs from the Go result, but is equally stable within the
Java operator.

Template usage:
```
name: workload-${hash(Labels)}
```

### 4.4 `unquote`

Strips leading and trailing double-quotes, single-quotes, and whitespace.

Template usage:
```
${unquote('"eastus"')}  → eastus
```

---

## 5. The `processTemplate` method — step by step

```java
String rendered = service.processTemplate(templateSource, context);
```

**What happens internally:**

**Step 1** — Create a Freemarker `Template` object from the raw template string.

**Step 2** — Call `template.process(context.toMap(), writer)`. Freemarker replaces
all `${Variable}` markers with values from `context.toMap()`. Custom functions like
`${toYaml(...)}` are evaluated using the registered shared variables.

**Step 3 — Recursive check**: After rendering, if the output string still contains
`${` or `<#`, it is treated as another template and re-processed with the same
context. This repeats up to `MAX_RECURSION_DEPTH = 5` times.

This mirrors Go's `replaceTemplateVariables` function which checks for `{{` in the
rendered output and re-processes:

```go
// Go original
func replaceTemplateVariables(tmpl string, data interface{}) (string, error) {
    // ...render...
    if strings.Contains(rendered, "{{") {
        return replaceTemplateVariables(rendered, data)
    }
    return rendered, nil
}
```

**When is recursion needed?** When a template context value itself contains a template
expression. For example, if `Namespace = "prefix-${DeploymentTargetName}"`:
- First pass: `${Namespace}` → `prefix-${DeploymentTargetName}`
- Second pass: `prefix-${DeploymentTargetName}` → `prefix-prod-east`

---

## 6. `buildTargetNamespace`

```java
String ns = TemplateProcessingService.buildTargetNamespace(deploymentTarget, clusterType);
// Example: "prod-aks-large-prod-east"
```

This static helper derives the Kubernetes namespace name for a deployment target.

**Formula**: `{environment}-{clusterTypeName}-{deploymentTargetName}`

Where:
- `environment` = `deploymentTarget.getSpec().getEnvironment()` (from the CRD spec)
- `clusterTypeName` = `clusterType.getMetadata().getName()` (Kubernetes resource name)
- `deploymentTargetName` = `deploymentTarget.getMetadata().getName()` (Kubernetes resource name)

This is a direct translation of Go's `buildTargetNamespace`:
```go
func buildTargetNamespace(dt DeploymentTarget, ct ClusterType) string {
    return fmt.Sprintf("%s-%s-%s", dt.Spec.Environment, ct.Name, dt.Name)
}
```

---

## 7. Files involved

```
src/main/java/io/kalypso/scheduler/
├── model/
│   └── TemplateContext.java          data bag for Freemarker; equivalent of Go dataType
└── services/
    └── TemplateProcessingService.java processTemplate / buildTargetNamespace

src/test/java/io/kalypso/scheduler/
└── services/
    └── TemplateProcessingServiceTest.java 9 unit tests
```

---

## 8. Correspondence with the Go operator

| Go (`templater.go`) | Java (`TemplateProcessingService.java`) |
|---|---|
| `type Templater interface { ProcessTemplate(...) }` | `processTemplate(templateSource, context)` |
| `type dataType struct { ... }` | `TemplateContext` (with `Builder`) |
| `funcMap["toYaml"]` | `ToYamlMethod` shared variable |
| `funcMap["stringify"]` | `StringifyMethod` shared variable |
| `funcMap["hash"]` | `HashMethod` shared variable |
| `funcMap["unquote"]` | `UnquoteMethod` shared variable |
| `replaceTemplateVariables` (recursive) | `processTemplateInternal(depth++)` |
| `buildTargetNamespace(dt, ct)` | `static buildTargetNamespace(dt, ct)` |
| `text/template + Sprig` | `Freemarker 2.3.32` |

---

## 9. Why Freemarker instead of Go's `text/template`?

Go's template engine uses `{{.Variable}}` syntax and Sprig for helper functions.
There is no direct Java port of the Go template engine.

Freemarker was chosen because:
1. It is mature and widely used in Java projects (Spring, Apache projects)
2. It supports the same features needed: variable interpolation, conditionals, loops
3. Custom functions can be registered easily as `TemplateMethodModelEx` implementations
4. It supports the same recursive template evaluation pattern needed

The template syntax changes from `{{.Variable}}` (Go) to `${Variable}` (Freemarker),
which means templates in `Template` CRD objects must be written in Freemarker syntax.
