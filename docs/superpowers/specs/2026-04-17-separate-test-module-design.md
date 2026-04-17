# Separate Test Module Support (Java + Java-TestNG Plugins)

## Summary

Allow `src/test/java` to be compiled and tested as an independent JPMS module that `requires` the main module, rather than being patched into the main module. This enables true public-API testing: tests can only see what the main module `exports`, proving the module boundary is correct.

Triggered by the presence of `src/test/java/module-info.java` (in addition to `src/main/java/module-info.java`).

Branch: `features/test-module`.

### JPMS Split-Package Constraint

A separate test module **cannot share a package with the main module**. JPMS forbids two modules from declaring the same package (no split packages), and a module can only `opens` packages it owns.

Practical consequence: test classes in separate-module mode must live in packages **distinct** from the main module's packages. Convention in this repo: if the main module exports `org.foo.bar`, the corresponding tests live in `org.foo.bar.tests` (or similar).

This is not a workaround — it is the point. By forcing tests into a separate package, the test module can only see `exports`ed API, which is exactly the "public-API-only" testing this feature delivers. Projects that need white-box testing of internals should stay in patch-module mode.

## Modes

Three mutually exclusive modes, determined by the presence of `module-info.java` files:

| `src/main/java/module-info.java` | `src/test/java/module-info.java` | Mode                                                                                                                                           |
|----------------------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| absent                           | absent                           | classpath (existing)                                                                                                                           |
| present                          | absent                           | patch-module (existing)                                                                                                                        |
| present                          | present                          | **separate-test-module (new)**                                                                                                                 |
| absent                           | present                          | **error** — `testModuleBuild is enabled but moduleBuild is not. A separate test module requires src/main/java/module-info.java to also exist.` |

## Settings Changes

### `JavaSettings`

Both `moduleBuild` and `testModuleBuild` are declared as nullable `Boolean` (not primitive `boolean`) with no initializer, so they default to `null`. The plugin treats `null` as "not yet configured" — auto-detection fills in the value on first method invocation.

```groovy
/**
 * Enables JPMS module build mode. If left null, auto-detected lazily on first plugin method call
 * from the presence of module-info.java in JavaLayout.mainSourceDirectory. Explicitly set to
 * true/false in project.latte to override.
 */
Boolean moduleBuild

/**
 * Enables separate test module mode. If left null, auto-detected lazily on first plugin method
 * call from the presence of module-info.java in JavaLayout.testSourceDirectory. Requires
 * moduleBuild to also be true; if it is not, compilation fails with an error.
 */
Boolean testModuleBuild
```

### `JavaTestNGSettings`

Add the same two fields with the same nullable semantics.

## New `JavaLayout` Class in `java-testng` Plugin

`JavaTestNGPlugin` currently hard-codes paths like `src/main/java/module-info.java`. With the new feature it depends on additional layout information (test source dir, test build dir, jar output dir). Rather than scattering more hard-coded strings, duplicate the existing `JavaLayout` class into the `java-testng` plugin so the two plugins each own an independently configurable layout.

### Why duplicate, not share

The `java-testng` plugin does not depend on the `java` plugin (see `plugins/java-testng/project.latte` — no `org.lattejava.plugin:java` dependency). Importing `JavaLayout` would force a new inter-plugin dependency. Duplicating keeps plugin coupling unchanged.

### New file

`plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaLayout.groovy` — byte-for-byte the same shape as `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaLayout.groovy`, but in the `org.lattejava.plugin.java.testng` package. MIT license header per CLAUDE.md.

### `JavaTestNGPlugin` changes

Add a public field (parallel to `JavaPlugin.layout`):

```groovy
JavaLayout layout = new JavaLayout()
```

This allows `project.latte` to configure both plugins' layouts independently:

```groovy
java.layout.mainSourceDirectory = Paths.get("src/java")
javaTestNG.layout.mainSourceDirectory = Paths.get("src/java")
```

### Replace hard-coded paths already in `JavaTestNGPlugin`

Only paths **touched by this feature** are refactored (to keep scope tight). Unrelated hard-coded paths in `test()`, `produceCodeCoverageReports()`, `processGitOutput()`, etc. are left as-is — those can be moved to `layout` in a follow-up change.

The single refactor in this spec: the existing main-module auto-detect, which currently reads `src/main/java/module-info.java`, becomes:

```groovy
if (Files.isRegularFile(project.directory.resolve(layout.mainSourceDirectory).resolve("module-info.java"))) {
  settings.moduleBuild = true
}
```

## Auto-detection (lazy `init()`)

Auto-detection is consolidated in a private `init()` method on each plugin, called as the first line of every public plugin method. It only writes to `settings.moduleBuild` / `settings.testModuleBuild` when they are still `null`, so explicit `project.latte` overrides are preserved.

```groovy
private void init() {
  if (settings.moduleBuild == null) {
    settings.moduleBuild = Files.isRegularFile(project.directory.resolve(layout.mainSourceDirectory).resolve("module-info.java"))
  }
  if (settings.testModuleBuild == null) {
    settings.testModuleBuild = Files.isRegularFile(project.directory.resolve(layout.testSourceDirectory).resolve("module-info.java"))
  }
}
```

Running `init()` lazily (rather than in the constructor) means layout overrides like `java.layout.mainSourceDirectory = Paths.get("src/java")` placed in `project.latte` after `loadPlugin(...)` are honored — auto-detect uses the current layout state, not the default one present at construction time.

Validation of the `testModuleBuild && !moduleBuild` combination is deferred to `compileTest()` / `test()` so overrides in `project.latte` are predictable.

## `JavaPlugin.compileTest()` — New Branch

Replace the current two-way if/else with a three-way branch:

```groovy
if (settings.testModuleBuild) {
  if (!settings.moduleBuild) {
    fail("testModuleBuild is enabled but moduleBuild is not. A separate test module requires " +
        "src/main/java/module-info.java to also exist.")
  }

  // Separate test module: all deps (main + test) go on --module-path.
  // User must declare `requires` in test module-info.java for everything tests use
  // (main module, testng, easymock, etc.).
  // Main build dir is on --module-path so test module can resolve `requires <mainModule>`.
  // No --patch-module, no --add-reads — pure JPMS.
  compileInternal(
      layout.testSourceDirectory,
      layout.testBuildDirectory,
      settings.testDependencies,
      "",
      layout.mainBuildDirectory)
} else if (settings.moduleBuild) {
  // Existing patch-module logic (unchanged).
} else {
  // Existing classpath logic (unchanged).
}
copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
```

`pathString()` already emits `--module-path` when `settings.moduleBuild` is true, so the separate-module branch works without further changes to that helper.

## `JavaPlugin.compileMain()`, `jar()`, `document()`

No changes. The main module compile is identical, `jarInternal(testBuildDirectory)` naturally picks up the new `module-info.class`, and javadoc runs only over the main module.

## `JavaTestNGPlugin.test()` — New Branch

Add a third branch. The method already extracts `classpathArgs`; extend it to also pick a TestNG entry-point invocation:

```groovy
String classpathArgs
String testngEntry

if (settings.testModuleBuild) {
  String testModuleName = resolveTestModuleName()

  // Everything on --module-path: main deps, test deps, main publication(s), test publication(s).
  Classpath modulePath = dependencyPlugin.classpath {
    settings.dependencies.each { deps -> dependencies(deps) }
    project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
    project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
  }

  // --add-modules ALL-MODULE-PATH resolves every JAR on the module path (including automatic
  // modules like slf4j that TestNG needs but doesn't formally require). The test module is
  // also named explicitly so its @Test classes are discoverable.
  // No --add-opens generation: the user's test module-info.java must declare
  //   opens <pkg> to org.testng;
  // for every package containing test classes.
  classpathArgs = "${modulePath.toString("--module-path ")} --add-modules ALL-MODULE-PATH,${testModuleName}"

  // TestNG 7+ ships Automatic-Module-Name: org.testng, so it resolves as an automatic module.
  // Entry point is launched via --module rather than a bare class name.
  testngEntry = "--module org.testng/org.testng.TestNG"
} else if (settings.moduleBuild) {
  // Existing patch-module logic (unchanged).
  testngEntry = "org.testng.TestNG"
} else {
  // Existing classpath logic (unchanged).
  testngEntry = "org.testng.TestNG"
}
```

The final command template is updated so `org.testng.TestNG` is replaced with `${testngEntry}`:

```
${javaPath} ${jvmArguments} ${classpathArgs} ${jacocoArgs} ${testngEntry} -d ${reportDirectory} ${testngArguments} ${xmlFile}
```

## `JavaTestNGPlugin.resolveTestModuleName()` — New Method

Parallel to the existing `resolveModuleName()`. Reads `module-info.class` from the **test publication JAR** using `JarFile`, extracts the module name with ASM. Same error-handling pattern as `resolveModuleName()`. Does not use `layout` — operates on publication JARs directly.

## What Doesn't Change

- `JavaLayout` (either copy) — no new fields. `src/test/java/module-info.java` lives under the existing `testSourceDirectory`.
- `jar()` / `jarInternal()` — test JAR includes `module-info.class` automatically because it's in `testBuildDirectory`.
- `pathString()` — already handles `--module-path` correctly when `moduleBuild` is true.
- `clean()`, `document()`, `getMainClasspath()`, `printJDKModuleDeps()` — unaffected.
- `Classpath` class — unaffected.
- Dependencies — no new artifacts; ASM is already in both plugins.
- Unrelated hard-coded paths in `JavaTestNGPlugin` (`build/test-reports`, `build/jacoco.exec`, `build/coverage-reports`, regex paths in `processGitOutput`) — left as-is for a follow-up.

## Test Fixtures

Add a new fixture `test-module-separate/` in **both** `plugins/java/` and `plugins/java-testng/`:

```
test-module-separate/
  src/main/java/
    module-info.java
    org/lattejava/test/MyClass.java
  src/main/resources/main.txt
  src/test/java/
    module-info.java
    org/lattejava/test/MyClassTest.java
  src/test/resources/test.txt
```

### `src/main/java/module-info.java`

```java
module org.lattejava.test {
  exports org.lattejava.test;
}
```

### `src/main/java/org/lattejava/test/MyClass.java`

Same as the existing `test-module/` fixture.

### `src/test/java/module-info.java`

```java
module org.lattejava.test.tests {
  requires org.lattejava.test;
  requires org.testng;
  opens org.lattejava.test.tests to org.testng;
}
```

### `src/test/java/org/lattejava/test/tests/MyClassTest.java`

Lives in its own package (`org.lattejava.test.tests`), not the main module's `org.lattejava.test` — required by JPMS split-package rules. Uses only `MyClass.doSomething()` from the exported API:

```java
package org.lattejava.test.tests;

import org.lattejava.test.MyClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MyClassTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
```

## New Unit Tests

### `JavaPluginTest.moduleBuildSeparate()`

- Constructs a `Project` pointing at `test-module-separate/`.
- Asserts `plugin.settings.moduleBuild` and `plugin.settings.testModuleBuild` are both auto-detected as `true`.
- Runs `clean()`, `compileMain()`, `compileTest()`, `jar()`.
- Asserts `build/classes/main/module-info.class` exists.
- Asserts `build/classes/test/module-info.class` exists.
- Asserts `build/jars/test-module-separate-1.0.0.jar` contains `module-info.class`.
- Asserts `build/jars/test-module-separate-test-1.0.0.jar` contains `module-info.class` **and** `org/lattejava/test/MyClassTest.class`.

### `JavaPluginTest.testModuleWithoutMainModule()` (optional error case)

- Creates a temp fixture with only `src/test/java/module-info.java` (no main module-info).
- Manually sets `plugin.settings.testModuleBuild = true`, `plugin.settings.moduleBuild = false`.
- Runs `compileTest()` and asserts the expected error message.

### `JavaTestNGPluginTest.moduleBuildSeparate()`

- Configures a `Project` pointing at `test-module-separate/` with main and test publications registered.
- Asserts `plugin.settings.moduleBuild` and `plugin.settings.testModuleBuild` are auto-detected.
- Runs `plugin.test()` and asserts `MyClassTest` ran successfully via the report XML.

## Follow-ups (explicitly out of scope)

- IntelliJ IDEA integration (idea plugin) for separate test modules. Not required for this feature to work; addressed separately if needed.
- `document()` for test modules — no use case identified.
- Open-to-unqualified shortcut (i.e., auto-generate `opens` declarations). Intentionally rejected during design in favor of explicit, user-authored `module-info.java`.
- Threading `layout` through the rest of `JavaTestNGPlugin`'s hard-coded paths (reports dir, jacoco exec, git-changes regex). Mechanical refactor, out of scope for this feature.
