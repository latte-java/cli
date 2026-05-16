# Java Plugin: Annotation Processor Module Path — Design

Date: 2026-05-16
Status: Approved (pending spec review)
Component: `cli/plugins/java`

## Goal

Let a developer point the Java plugin at a dependency group whose artifacts are
JPMS modules containing annotation processors. The plugin resolves that group
and passes it to `javac` via `--processor-module-path`, so `javac`
auto-discovers the processors through the `ServiceLoader` mechanism.

## Background

`javac --processor-module-path <path>` builds a module graph from the path and
discovers every `provides javax.annotation.processing.Processor` declaration via
`ServiceLoader` (the module-system equivalent of a classpath jar's
`META-INF/services/javax.annotation.processing.Processor` file). It is mutually
exclusive with `-processorpath` but coexists with the normal `-classpath` /
`--module-path`. If a processor module `requires` another module that is not
also on the processor module path, module resolution fails before any processor
runs — so the group must be resolved **transitively**.

## Setting

Add to `org.lattejava.plugin.java.JavaSettings`:

```groovy
/**
 * Dependency groups whose artifacts are JPMS modules containing annotation processors.
 * Each entry has the same shape as mainDependencies/testDependencies. The resolved
 * artifacts are passed to javac via --processor-module-path; javac auto-discovers
 * processors from these modules via ServiceLoader (each module must
 * `provides javax.annotation.processing.Processor`). Resolved transitively so each
 * processor's own library dependencies join the module graph. Applied to both main
 * and test compilation.
 *
 * Defaults to a single group named "compile-processors", resolved transitively
 * through the "compile" and "runtime" groups. A project opts in simply by declaring
 * a "compile-processors" dependency group in project.latte; projects that do not
 * declare it are unaffected (no --processor-module-path is emitted).
 */
List<Map<String, Object>> processorDependencies = [
    [group: "compile-processors", transitive: true, fetchSource: false,
     transitiveGroups: ["compile", "runtime"]]
]
```

Shape and semantics match the existing `mainDependencies` / `testDependencies`
lists, so the dependency map keys (`group`, `transitive`, `fetchSource`,
`transitiveGroups`) behave identically.

## Behavior

- **Single shared setting** applied to both `compileMain()` and `compileTest()`.
- **Auto-discovery only.** The plugin never emits `-processor`, so `javac`
  discovers processors via `ServiceLoader`. It never emits `-processorpath`, so
  there is no mutual-exclusion conflict with `-classpath` / `--module-path`.
- **Orthogonal to `moduleBuild`.** `--processor-module-path` is emitted
  independently of whether the project itself is a module; it does not change
  the main `pathArgs` (`-classpath` vs `--module-path`) selection.
- **Absent/empty group is a no-op.** If the resolved processor classpath has no
  paths, no `--processor-module-path` flag is emitted and the `javac` command is
  byte-for-byte identical to current behavior.

## Implementation

### 1. `JavaSettings.groovy`

Add the `processorDependencies` field above with the default shown.

### 2. `JavaPlugin.compileInternal(...)`

`compileInternal` is the single chokepoint both `compileMain()` and
`compileTest()` route through. In it:

1. Resolve the processor module path:
   `resolveRunClasspath(settings.processorDependencies, [], [])`
   — dependency groups only; no `libraryDirectories`, no build dirs.
2. `String processorArgs = processorClasspath.toString("--processor-module-path ")`
   (returns `""` when there are no paths).
3. Splice `processorArgs` into the existing `command` string alongside
   `pathArgs` / `extraArgs`.

No change to `compileMain()` / `compileTest()` is required — routing the logic
through `compileInternal` makes the "shared across main and test" behavior fall
out automatically.

### Why the default is safe for existing projects (verified)

- `ClasspathDelegate.toClasspath()` resolves only when traversal rules exist
  **and** `project.dependencies != null`; otherwise it returns an empty
  `Classpath`.
- `DefaultDependencyService.resolve()` traverses the project's actual
  dependency-graph edges. A project that does not declare a `compile-processors`
  group has no edges of that group, so the traversal callback is never invoked
  for it and the resolved graph is empty — **no exception is thrown**.
- `Classpath.toString(prefix)` returns `""` for an empty path list, so an
  absent/empty `compile-processors` group emits no `--processor-module-path`
  flag.

## Testing

### Integration test (`JavaPluginTest`)

- Add a test-project fixture (or extend an existing one) that declares a
  `compile-processors` dependency group pointing at a real annotation processor
  available on Maven Central that:
  1. has at least one transitive library dependency (exercises the
     transitive-module-graph requirement), and
  2. generates an observable artifact (a generated `.class` and/or source file).
  The specific Maven coordinate is selected during implementation; it must be a
  processor that registers via `ServiceLoader` (declares the processor as a
  service so module-path auto-discovery works).
- Assertions:
  - After `compileMain()` (and `compileTest()` where applicable), the
    processor-generated artifact exists in the build output.
  - The constructed `javac` command contains `--processor-module-path` when the
    `compile-processors` group is declared.
  - The constructed `javac` command does **not** contain
    `--processor-module-path` for a project with no `compile-processors` group
    (regression guard for existing projects).

### Existing tests

All existing `JavaPluginTest` cases must continue to pass unchanged, since none
of the existing test projects declare a `compile-processors` group.

## Out of Scope (YAGNI)

- Explicit `-processor <classes>` selection / disabling auto-discovery.
- Separate main vs. test processor settings.
- A dedicated public plugin method for annotation processing.
- Processor options (`-A<key>=<value>`) configuration.

## License

New/modified source files use the MIT header with copyright "The Latte Project"
per `cli/CLAUDE.md`.
