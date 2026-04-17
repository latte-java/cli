# Separate Test Module Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the `java` and `java-testng` plugins so that when both `src/main/java/module-info.java` and `src/test/java/module-info.java` exist, tests are compiled and run as an independent JPMS module that `requires` the main module (enabling true public-API-only testing).

**Architecture:** Add a `testModuleBuild` setting, auto-detected from `src/test/java/module-info.java`. In `JavaPlugin.compileTest()`, add a third branch that skips `--patch-module` and compiles tests with the main build dir plus all test dependencies on `--module-path`. In `JavaTestNGPlugin.test()`, add a third branch that puts all deps + both publications on `--module-path`, uses `--add-modules <testModule>`, and launches TestNG via `--module org.testng/org.testng.TestNG`. Duplicate `JavaLayout` into the java-testng plugin (since java-testng does not depend on the java plugin) so both plugins own configurable layouts.

**Tech Stack:** Groovy, Latte build system, TestNG, ASM (for reading `module-info.class`), Java 25.

**Branch:** `features/test-module` (already created).

**Reference:** spec at `docs/superpowers/specs/2026-04-17-separate-test-module-design.md`.

---

## File Structure

### Created
- `plugins/java/test-module-separate/src/main/java/module-info.java` — fixture main module.
- `plugins/java/test-module-separate/src/main/java/org/lattejava/test/MyClass.java` — fixture exported class.
- `plugins/java/test-module-separate/src/main/resources/main.txt` — fixture main resource.
- `plugins/java/test-module-separate/src/test/java/module-info.java` — fixture test module.
- `plugins/java/test-module-separate/src/test/java/org/lattejava/test/MyClassTest.java` — fixture test class.
- `plugins/java/test-module-separate/src/test/resources/test.txt` — fixture test resource.
- `plugins/java-testng/test-module-separate/...` — identical fixture layout for java-testng plugin.
- `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaLayout.groovy` — duplicate layout class in java-testng package.

### Modified
- `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaSettings.groovy` — add `testModuleBuild` field.
- `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy` — auto-detect, new compileTest branch, `resolveTestModuleName()`.
- `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy` — new `moduleBuildSeparate()` test, new error-case test.
- `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGSettings.groovy` — add `testModuleBuild` field.
- `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy` — add `layout` field, use `layout.mainSourceDirectory` for existing detect, add test-module detect, new `test()` branch, `resolveTestModuleName()`, entry-point indirection.
- `plugins/java-testng/src/test/groovy/org/lattejava/plugin/java/testng/JavaTestNGPluginTest.groovy` — new `moduleBuildSeparate()` test.

---

## Task 1: Add `testModuleBuild` field to `JavaSettings`

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaSettings.groovy`

- [ ] **Step 1: Add the field**

Add this property to `JavaSettings.groovy` immediately after the existing `moduleBuild` field (around line 68):

```groovy
  /**
   * Enables separate test module mode. When true, src/test/java is compiled as an independent
   * JPMS module that {@code requires} the main module, rather than being patched into it.
   * <p>
   * Auto-detected based on the presence of {@code module-info.java} in {@link JavaLayout#testSourceDirectory}.
   * Requires {@link #moduleBuild} to also be true; if it is not, compilation fails with an error.
   */
  boolean testModuleBuild = false
```

- [ ] **Step 2: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaSettings.groovy
git commit -m "Add testModuleBuild setting to JavaSettings

Field controls whether src/test/java is compiled as an independent JPMS
module. Auto-detected in a later commit."
```

---

## Task 2: Auto-detect `testModuleBuild` in `JavaPlugin` constructor

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy:71-75`

- [ ] **Step 1: Add the detection block**

The existing constructor has this block (around lines 71-74):

```groovy
    // Auto-detect module build if module-info.java exists
    if (Files.isRegularFile(project.directory.resolve(layout.mainSourceDirectory).resolve("module-info.java"))) {
      settings.moduleBuild = true
    }
```

Add this immediately after it:

```groovy
    // Auto-detect separate test module if src/test/java/module-info.java exists
    if (Files.isRegularFile(project.directory.resolve(layout.testSourceDirectory).resolve("module-info.java"))) {
      settings.testModuleBuild = true
    }
```

- [ ] **Step 2: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy
git commit -m "Auto-detect testModuleBuild from src/test/java/module-info.java"
```

---

## Task 3: Create the `test-module-separate` fixture for `JavaPlugin`

**Files:**
- Create: `plugins/java/test-module-separate/src/main/java/module-info.java`
- Create: `plugins/java/test-module-separate/src/main/java/org/lattejava/test/MyClass.java`
- Create: `plugins/java/test-module-separate/src/main/resources/main.txt`
- Create: `plugins/java/test-module-separate/src/test/java/module-info.java`
- Create: `plugins/java/test-module-separate/src/test/java/org/lattejava/test/MyClassTest.java`
- Create: `plugins/java/test-module-separate/src/test/resources/test.txt`

- [ ] **Step 1: Create main module-info**

File `plugins/java/test-module-separate/src/main/java/module-info.java`:

```java
module org.lattejava.test {
  exports org.lattejava.test;
}
```

- [ ] **Step 2: Create `MyClass`**

File `plugins/java/test-module-separate/src/main/java/org/lattejava/test/MyClass.java`:

```java
package org.lattejava.test;

public class MyClass {
  public String doSomething() {
    return "Hello World";
  }
}
```

- [ ] **Step 3: Create main resource**

File `plugins/java/test-module-separate/src/main/resources/main.txt`:

```
main resource
```

(No trailing newline — match existing `test-module/` fixture.)

- [ ] **Step 4: Create test module-info**

File `plugins/java/test-module-separate/src/test/java/module-info.java`:

```java
module org.lattejava.test.tests {
  requires org.lattejava.test;
  requires org.testng;
  opens org.lattejava.test to org.testng;
}
```

- [ ] **Step 5: Create `MyClassTest`**

File `plugins/java/test-module-separate/src/test/java/org/lattejava/test/MyClassTest.java`:

```java
package org.lattejava.test;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MyClassTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
```

- [ ] **Step 6: Create test resource**

File `plugins/java/test-module-separate/src/test/resources/test.txt`:

```
test resource
```

- [ ] **Step 7: Commit**

```bash
git add plugins/java/test-module-separate/
git commit -m "Add test-module-separate fixture for java plugin

New fixture project with both src/main/java/module-info.java and
src/test/java/module-info.java. Used to validate the new separate
test-module mode."
```

---

## Task 4: Write failing `moduleBuildSeparate()` test in `JavaPluginTest`

**Files:**
- Modify: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Add the test method**

Append this method to `JavaPluginTest` (after the existing `moduleBuild()` test, around line 176):

```groovy
  @Test
  void moduleBuildSeparate() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-module-separate"), output)
    project.group = "org.lattejava.test"
    project.name = "test-module-separate"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        output
    )

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    // Verify auto-detection of both module build and separate-test-module
    assertTrue(plugin.settings.moduleBuild)
    assertTrue(plugin.settings.testModuleBuild)

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-module-separate/build")))

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/module-info.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/org/lattejava/test/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/module-info.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/org/lattejava/test/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/jars/test-module-separate-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-module-separate/build/jars/test-module-separate-1.0.0.jar"), "module-info.class", "org/lattejava/test/MyClass.class", "main.txt")
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/jars/test-module-separate-test-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-module-separate/build/jars/test-module-separate-test-1.0.0.jar"), "module-info.class", "org/lattejava/test/MyClassTest.class", "test.txt")
  }
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
cd plugins/java && latte test --test=JavaPluginTest
```

Expected: `moduleBuildSeparate` fails during `compileTest()` because `compileInternal` is called with `--patch-module` arguments that don't make sense when the test source tree has its own `module-info.java` (the javac invocation will error with a duplicate module-info / "module already in unnamed module" style error, or with "module not found: org.testng" because testng goes on classpath in patch mode).

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "Add failing moduleBuildSeparate test for JavaPlugin

Asserts that a project with both main and test module-info.java
auto-detects testModuleBuild and produces compiled classes and JARs
with module-info.class in both output JARs. Fails until the new
compileTest branch is implemented."
```

---

## Task 5: Implement new branch in `JavaPlugin.compileTest()`

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy:129-145`

- [ ] **Step 1: Replace the method body**

The current `compileTest()` method is:

```groovy
  void compileTest() {
    String moduleArgs = ""
    if (settings.moduleBuild) {
      String moduleName = resolveModuleName()
      // Test-only deps (e.g. TestNG) go on -classpath so they land in the unnamed module,
      // accessible via --add-reads. Main deps + main build dir go on --module-path.
      String testClasspath = dependencyPlugin.classpath {
        settings.testDependencies.findAll { it.group != "compile" && it.group != "provided" }
            .each { deps -> dependencies(deps) }
      }.toString("-classpath ")
      moduleArgs = "${testClasspath} --patch-module ${moduleName}=${layout.testSourceDirectory} --add-reads ${moduleName}=ALL-UNNAMED"
    }
    compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
        settings.moduleBuild ? settings.mainDependencies : settings.testDependencies,
        moduleArgs, layout.mainBuildDirectory, layout.testBuildDirectory)
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }
```

Replace it with:

```groovy
  void compileTest() {
    if (settings.testModuleBuild) {
      if (!settings.moduleBuild) {
        fail("testModuleBuild is enabled but moduleBuild is not. A separate test module requires " +
            "src/main/java/module-info.java to also exist.")
      }

      // Separate test module: all deps (main + test) go on --module-path.
      // The user's test module-info.java must declare `requires` for everything tests use
      // (main module, testng, easymock, etc.). Main build dir is on --module-path so the
      // test module can resolve `requires <mainModule>`. No --patch-module, no --add-reads.
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.testDependencies, "", layout.mainBuildDirectory)
    } else if (settings.moduleBuild) {
      String moduleName = resolveModuleName()
      // Test-only deps (e.g. TestNG) go on -classpath so they land in the unnamed module,
      // accessible via --add-reads. Main deps + main build dir go on --module-path.
      String testClasspath = dependencyPlugin.classpath {
        settings.testDependencies.findAll { it.group != "compile" && it.group != "provided" }
            .each { deps -> dependencies(deps) }
      }.toString("-classpath ")
      String moduleArgs = "${testClasspath} --patch-module ${moduleName}=${layout.testSourceDirectory} --add-reads ${moduleName}=ALL-UNNAMED"
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.mainDependencies, moduleArgs, layout.mainBuildDirectory, layout.testBuildDirectory)
    } else {
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.testDependencies, "", layout.mainBuildDirectory, layout.testBuildDirectory)
    }
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }
```

Notes:
- The separate-module branch passes **only** `layout.mainBuildDirectory` as the additional path (not `layout.testBuildDirectory` as well) because the test module should NOT patch itself. Everything comes from `--module-path`.
- `pathString()` emits `--module-path` when `settings.moduleBuild` is true. Since that is guaranteed when `testModuleBuild` is true, the helper produces the right flag automatically.

- [ ] **Step 2: Run the failing test and confirm it passes**

```bash
cd plugins/java && latte test --test=JavaPluginTest
```

Expected: all `JavaPluginTest` tests pass, including `moduleBuildSeparate`, `moduleBuild`, and `all`.

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy
git commit -m "Implement separate test module compile branch in JavaPlugin

When testModuleBuild is true, compileTest() now skips --patch-module
and puts all test dependencies and the main build dir on --module-path,
allowing src/test/java/module-info.java to compile as an independent
module that requires the main module."
```

---

## Task 6: Add `resolveTestModuleName()` to `JavaPlugin`

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`

- [ ] **Step 1: Add the method**

Add this private method immediately after the existing `resolveModuleName()` (around line 416):

```groovy
  private String resolveTestModuleName() {
    Path moduleInfoClass = project.directory.resolve(layout.testBuildDirectory).resolve("module-info.class")
    if (!Files.isRegularFile(moduleInfoClass)) {
      fail("testModuleBuild is enabled but module-info.class was not found in [%s]. Ensure test sources are compiled first.", layout.testBuildDirectory)
    }

    byte[] bytes = Files.readAllBytes(moduleInfoClass)
    ClassReader reader = new ClassReader(bytes)
    String[] result = new String[1]
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      ModuleVisitor visitModule(String name, int access, String version) {
        result[0] = name
        return null
      }
    }, 0)

    if (!result[0]) {
      fail("Failed to extract module name from [%s]", moduleInfoClass)
    }

    return result[0]
  }
```

- [ ] **Step 2: Verify the existing tests still pass**

```bash
cd plugins/java && latte test --test=JavaPluginTest
```

Expected: all tests still pass (the new method is unused in `JavaPlugin`; it exists for `JavaTestNGPlugin` to invoke indirectly via... actually `JavaTestNGPlugin` has its own copy of this logic reading from a JAR, so this method is currently unused in the java plugin — consumers may invoke it from `project.latte`).

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy
git commit -m "Add resolveTestModuleName() to JavaPlugin

Reads build/classes/test/module-info.class with ASM and returns the
test module's name. Exposed for consumers that need the test module
name (parallels the existing resolveModuleName for the main module)."
```

---

## Task 7: Add the "testModuleBuild without moduleBuild" error-case test

**Files:**
- Modify: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Add the test method**

Append to `JavaPluginTest`:

```groovy
  @Test
  void testModuleBuildWithoutMainModuleBuildFails() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        output
    )

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"
    plugin.settings.libraryDirectories.add(projectDir.resolve("lib"))

    // Force the invalid combination: test module on, main module off
    plugin.settings.moduleBuild = false
    plugin.settings.testModuleBuild = true

    plugin.clean()
    plugin.compileMain()

    try {
      plugin.compileTest()
      fail("Expected compileTest() to fail when testModuleBuild is true but moduleBuild is false")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("testModuleBuild is enabled but moduleBuild is not"),
          "Unexpected error message: " + expected.getMessage())
    }
  }
```

- [ ] **Step 2: Run the test**

```bash
cd plugins/java && latte test --test=JavaPluginTest
```

Expected: the new test passes. All other `JavaPluginTest` tests continue to pass.

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "Test compileTest failure when testModuleBuild lacks moduleBuild"
```

---

## Task 8: Run the full `java` plugin test suite and integrate-publish

**Files:**
- None (verification step)

- [ ] **Step 1: Run all java plugin tests**

```bash
cd plugins/java && latte test
```

Expected: all tests pass, including `all`, `moduleBuild`, `moduleBuildSeparate`, `testModuleBuildWithoutMainModuleBuildFails`, and `jarjar`.

- [ ] **Step 2: Integrate-publish the java plugin locally**

```bash
cd plugins/java && latte int
```

Expected: builds successfully and publishes the plugin to `~/.cache/latte` under its integration version. The `java-testng` plugin will resolve this locally in later tasks (though `java-testng` does not currently depend on the `java` plugin — this step is defensive for any future linkage and mirrors the normal plugin workflow).

- [ ] **Step 3: No commit**

(Verification only.)

---

## Task 9: Create `JavaLayout` in the `java-testng` plugin

**Files:**
- Create: `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaLayout.groovy`

- [ ] **Step 1: Create the file**

Contents:

```groovy
/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.plugin.java.testng

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Layout class that defines the directories used by the Java TestNG plugin.
 * <p>
 * Duplicated from the {@code java} plugin's {@code JavaLayout} rather than imported, because
 * the {@code java-testng} plugin does not depend on the {@code java} plugin.
 */
class JavaLayout {
  /**
   * The build directory. Defaults to {@code build}.
   */
  Path buildDirectory = Paths.get("build")

  /**
   * The documentation directory. Defaults to {@code build/doc}.
   */
  Path docDirectory = buildDirectory.resolve("doc")

  /**
   * The jar build directory. Defaults to {@code build/jars}.
   */
  Path jarOutputDirectory = buildDirectory.resolve("jars")

  /**
   * The main source directory. Defaults to {@code src/main/java}.
   */
  Path mainSourceDirectory = Paths.get("src/main/java")

  /**
   * The main resource directory. Defaults to {@code src/main/resources}.
   */
  Path mainResourceDirectory = Paths.get("src/main/resources")

  /**
   * The main build directory. Defaults to {@code build/classes/main}.
   */
  Path mainBuildDirectory = buildDirectory.resolve("classes/main")

  /**
   * The test source directory. Defaults to {@code src/test/java}.
   */
  Path testSourceDirectory = Paths.get("src/test/java")

  /**
   * The test resource directory. Defaults to {@code src/test/resources}.
   */
  Path testResourceDirectory = Paths.get("src/test/resources")

  /**
   * The test build directory. Defaults to {@code build/classes/test}.
   */
  Path testBuildDirectory = buildDirectory.resolve("classes/test")
}
```

- [ ] **Step 2: Commit**

```bash
git add plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaLayout.groovy
git commit -m "Add JavaLayout to java-testng plugin

Duplicates the java plugin's JavaLayout into the java-testng package.
The java-testng plugin does not depend on the java plugin, so
duplicating avoids introducing a new inter-plugin dependency."
```

---

## Task 10: Add `testModuleBuild` field to `JavaTestNGSettings`

**Files:**
- Modify: `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGSettings.groovy`

- [ ] **Step 1: Add the field**

Add this property immediately after the existing `moduleBuild` field (around line 34):

```groovy
  /**
   * Enables separate test module mode. When true, tests are run with src/test/java compiled as an
   * independent JPMS module that {@code requires} the main module, rather than patched into it.
   * <p>
   * Auto-detected based on the presence of {@code module-info.java} in {@link JavaLayout#testSourceDirectory}.
   * Requires {@link #moduleBuild} to also be true.
   */
  boolean testModuleBuild = false
```

- [ ] **Step 2: Commit**

```bash
git add plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGSettings.groovy
git commit -m "Add testModuleBuild setting to JavaTestNGSettings"
```

---

## Task 11: Add `layout` field to `JavaTestNGPlugin` and use it for existing main-module detection

**Files:**
- Modify: `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy`

- [ ] **Step 1: Add the `layout` field**

Add this instance field near the top of the class (for example, immediately before or after `JavaTestNGSettings settings = new JavaTestNGSettings()` at line 75):

```groovy
  JavaLayout layout = new JavaLayout()
```

- [ ] **Step 2: Update the existing main-module auto-detect**

The current constructor has (around lines 83-86):

```groovy
    // Auto-detect module build if module-info.java exists
    if (Files.isRegularFile(project.directory.resolve("src/main/java/module-info.java"))) {
      settings.moduleBuild = true
    }
```

Replace with:

```groovy
    // Auto-detect module build if module-info.java exists
    if (Files.isRegularFile(project.directory.resolve(layout.mainSourceDirectory).resolve("module-info.java"))) {
      settings.moduleBuild = true
    }
```

- [ ] **Step 3: Run the existing tests to verify no regressions**

```bash
cd plugins/java-testng && latte test --test=JavaTestNGPluginTest
```

Expected: all existing tests pass (`all`, `moduleBuild`, `coverage`, `skipTests`, `singleTestSwitch`, `withExclude`, `withGroup`). The hard-coded path was equivalent to the new layout-based path, so behavior is identical.

- [ ] **Step 4: Commit**

```bash
git add plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy
git commit -m "Add layout field to JavaTestNGPlugin, use it for main-module detect

Moves the existing src/main/java/module-info.java check to use
layout.mainSourceDirectory so it respects any project.latte overrides.
No behavior change."
```

---

## Task 12: Auto-detect `testModuleBuild` in `JavaTestNGPlugin`

**Files:**
- Modify: `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy`

- [ ] **Step 1: Add the detection block**

Immediately after the main-module detect block from Task 11:

```groovy
    // Auto-detect separate test module if src/test/java/module-info.java exists
    if (Files.isRegularFile(project.directory.resolve(layout.testSourceDirectory).resolve("module-info.java"))) {
      settings.testModuleBuild = true
    }
```

- [ ] **Step 2: Commit**

```bash
git add plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy
git commit -m "Auto-detect testModuleBuild in JavaTestNGPlugin"
```

---

## Task 13: Create the `test-module-separate` fixture for `java-testng`

**Files:**
- Create: `plugins/java-testng/test-module-separate/src/main/java/module-info.java`
- Create: `plugins/java-testng/test-module-separate/src/main/java/org/lattejava/test/MyClass.java`
- Create: `plugins/java-testng/test-module-separate/src/main/resources/main.txt`
- Create: `plugins/java-testng/test-module-separate/src/test/java/module-info.java`
- Create: `plugins/java-testng/test-module-separate/src/test/java/org/lattejava/test/MyClassTest.java`
- Create: `plugins/java-testng/test-module-separate/src/test/resources/test.txt`

- [ ] **Step 1: Copy the fixture from the java plugin**

Run:

```bash
mkdir -p plugins/java-testng/test-module-separate
cp -R plugins/java/test-module-separate/src plugins/java-testng/test-module-separate/
```

- [ ] **Step 2: Verify the contents**

Expected tree:

```
plugins/java-testng/test-module-separate/src/
  main/java/module-info.java
  main/java/org/lattejava/test/MyClass.java
  main/resources/main.txt
  test/java/module-info.java
  test/java/org/lattejava/test/MyClassTest.java
  test/resources/test.txt
```

- [ ] **Step 3: Commit**

```bash
git add plugins/java-testng/test-module-separate/
git commit -m "Add test-module-separate fixture for java-testng plugin"
```

---

## Task 14: Copy pre-built fixture artifacts from the java plugin

**Files:**
- None new (copies `build/` from the java plugin's fixture into the java-testng plugin's fixture).

Rationale: `JavaTestNGPluginTest` drives a `plugin.test()` call that needs the fixture's JARs to already exist on disk — same pattern as the existing `plugins/java-testng/test-module/build/` artifacts, which are checked in for this reason (see the note in `JavaTestNGPluginTest` around line 45). Since Task 5 makes `JavaPluginTest.moduleBuildSeparate` produce exactly these JARs for the **java** plugin's fixture, we reuse those outputs here.

- [ ] **Step 1: Re-run `JavaPluginTest.moduleBuildSeparate` to regenerate the build output fresh**

```bash
cd plugins/java && latte test --test=JavaPluginTest
```

Expected: PASS. Afterwards `plugins/java/test-module-separate/build/classes/` and `plugins/java/test-module-separate/build/jars/` contain the compiled classes and both JARs.

- [ ] **Step 2: Copy the build artifacts into the java-testng fixture**

```bash
cd /Users/bpontarelli/dev/latte-java/cli
rm -rf plugins/java-testng/test-module-separate/build
cp -R plugins/java/test-module-separate/build plugins/java-testng/test-module-separate/
```

- [ ] **Step 3: Verify**

```bash
jar --list --file plugins/java-testng/test-module-separate/build/jars/test-module-separate-1.0.0.jar
jar --list --file plugins/java-testng/test-module-separate/build/jars/test-module-separate-test-1.0.0.jar
```

Expected: main jar contains `module-info.class`, `org/lattejava/test/MyClass.class`, `main.txt`. Test jar contains `module-info.class`, `org/lattejava/test/MyClassTest.class`, `test.txt`.

- [ ] **Step 4: Commit the pre-built artifacts**

```bash
git add -f plugins/java-testng/test-module-separate/build/
git commit -m "Check in pre-built test-module-separate artifacts for java-testng

Follows the same pattern as the existing test-module/build/ fixture:
JavaTestNGPluginTest expects JARs to exist on disk. Copied from the
java plugin's fixture output after running JavaPluginTest."
```

---

## Task 15: Write `moduleBuildSeparate()` test in `JavaTestNGPluginTest` and implement the new `test()` branch

**Files:**
- Modify: `plugins/java-testng/src/test/groovy/org/lattejava/plugin/java/testng/JavaTestNGPluginTest.groovy`
- Modify: `plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy`

- [ ] **Step 1: Add `resolveTestModuleName()` to `JavaTestNGPlugin`**

Add this private method immediately after the existing `resolveModuleName()` (around line 403):

```groovy
  private String resolveTestModuleName() {
    // Extract module-info.class from the first test publication JAR
    for (def pub : project.publications.group("test")) {
      try (JarFile jarFile = new JarFile(pub.file.toFile())) {
        JarEntry moduleInfo = jarFile.getJarEntry("module-info.class")
        if (moduleInfo == null) {
          continue
        }

        byte[] bytes = jarFile.getInputStream(moduleInfo).readAllBytes()
        ClassReader reader = new ClassReader(bytes)
        String[] result = new String[1]
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
          @Override
          ModuleVisitor visitModule(String name, int access, String version) {
            result[0] = name
            return null
          }
        }, 0)

        if (result[0]) {
          return result[0]
        }
      }
    }

    fail("testModuleBuild is enabled but no module-info.class was found in any test publication JAR.")
    return null
  }
```

- [ ] **Step 2: Add the new branch to `test()`**

The current `test()` method has (around lines 118-166):

```groovy
    String classpathArgs
    if (settings.moduleBuild) {
      // ... patch-module logic ...
    } else {
      Classpath classpath = dependencyPlugin.classpath {
        settings.dependencies.each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
        project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }
      classpathArgs = classpath.toString("-classpath ")
    }

    // ...
    String command = "${javaPath} ${settings.jvmArguments} ${classpathArgs} ${jacocoArgs} org.testng.TestNG -d ${settings.reportDirectory} ${settings.testngArguments} ${xmlFile}"
```

Change the if/else to include a new leading branch and introduce a `testngEntry` variable:

```groovy
    String classpathArgs
    String testngEntry
    if (settings.testModuleBuild) {
      if (!settings.moduleBuild) {
        fail("testModuleBuild is enabled but moduleBuild is not. A separate test module requires " +
            "src/main/java/module-info.java to also exist.")
      }

      String testModuleName = resolveTestModuleName()

      // Everything on --module-path: main deps, test deps, main publication(s), test publication(s).
      // The test module's module-info.java declares its own requires for main module, testng, etc.
      Classpath modulePath = dependencyPlugin.classpath {
        settings.dependencies.each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
        project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }

      // --add-modules forces resolution of the test module so its @Test classes are discoverable.
      // No --add-opens: the user's test module-info.java must declare `opens <pkg> to org.testng;`
      // for every package containing test classes.
      classpathArgs = "${modulePath.toString("--module-path ")} --add-modules ${testModuleName}"

      // TestNG 7+ ships Automatic-Module-Name: org.testng, so it resolves as an automatic module.
      testngEntry = "--module org.testng/org.testng.TestNG"
    } else if (settings.moduleBuild) {
      String moduleName = resolveModuleName()

      // Main deps + main publications go on --module-path
      Classpath modulePath = dependencyPlugin.classpath {
        settings.dependencies.findAll { it.group != "test-compile" && it.group != "test-runtime" }
            .each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }

      // Test-only deps go on -classpath (unnamed module)
      Classpath testClasspath = dependencyPlugin.classpath {
        settings.dependencies.findAll { it.group == "test-compile" || it.group == "test-runtime" }
            .each { deps -> dependencies(deps) }
      }

      // Patch test publications into the module
      String testPubPaths = project.publications.group("test")
          .collect { it.file.toAbsolutePath().toString() }
          .join(File.pathSeparator)

      // Extract packages from test JARs for --add-opens so TestNG can reflectively access test classes
      Set<String> packages = new TreeSet<>()
      project.publications.group("test").each { publication ->
        try (JarFile jarFile = new JarFile(publication.file.toFile())) {
          jarFile.entries().each { entry ->
            if (!entry.directory && entry.name.endsWith(".class")) {
              int lastSlash = entry.name.lastIndexOf("/")
              if (lastSlash > 0) {
                packages.add(entry.name.substring(0, lastSlash).replace("/", "."))
              }
            }
          }
        }
      }
      String addOpens = packages.collect { "--add-opens ${moduleName}/${it}=ALL-UNNAMED" }.join(" ")

      classpathArgs = "${modulePath.toString("--module-path ")} ${testClasspath.toString("-classpath ")} --add-modules ${moduleName} --patch-module ${moduleName}=${testPubPaths} --add-reads ${moduleName}=ALL-UNNAMED ${addOpens}"
      testngEntry = "org.testng.TestNG"
    } else {
      Classpath classpath = dependencyPlugin.classpath {
        settings.dependencies.each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
        project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }
      classpathArgs = classpath.toString("-classpath ")
      testngEntry = "org.testng.TestNG"
    }
```

And update the `command` assembly so `org.testng.TestNG` becomes `${testngEntry}`:

```groovy
    String command = "${javaPath} ${settings.jvmArguments} ${classpathArgs} ${jacocoArgs} ${testngEntry} -d ${settings.reportDirectory} ${settings.testngArguments} ${xmlFile}"
```

- [ ] **Step 3: Write the test in `JavaTestNGPluginTest`**

Add this test method (alongside the existing `moduleBuild()` test, around line 138):

```groovy
  @Test
  void moduleBuildSeparate() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)
    FileTools.prune(projectDir.resolve("test-module-separate/build/test-reports"))

    Output moduleOutput = new SystemOutOutput(true)
    moduleOutput.enableDebug()

    Project moduleProject = new Project(projectDir.resolve("test-module-separate"), moduleOutput)
    moduleProject.group = "org.lattejava.test"
    moduleProject.name = "test-module-separate"
    moduleProject.version = new Version("1.0.0")
    moduleProject.licenses.add(License.parse("ApacheV2_0", null))

    moduleProject.publications.add("main", new Publication(new ReifiedArtifact("org.lattejava.test:test-module-separate:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-separate-1.0.0.jar"), null))
    moduleProject.publications.add("test", new Publication(new ReifiedArtifact("org.lattejava.test:test-module-separate:test-module-separate-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-separate-test-1.0.0.jar"), null))

    moduleProject.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    moduleProject.workflow = new Workflow(
        new FetchWorkflow(moduleOutput,
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(moduleOutput, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        moduleOutput
    )

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(moduleProject, new RuntimeConfiguration(), moduleOutput)
    plugin.settings.javaVersion = "25"

    // Verify auto-detection
    assertTrue(plugin.settings.moduleBuild)
    assertTrue(plugin.settings.testModuleBuild)

    plugin.test()
    assertSeparateModuleTestsRan("org.lattejava.test.MyClassTest")
  }

  static void assertSeparateModuleTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-module-separate/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-module-separate/build/test-reports/latte-tests/all.xml")))

    HashSet<String> tested = findNonIgnoredTestClasses("test-module-separate/build/test-reports/latte-tests/all.xml")
    assertEquals(tested, new HashSet<>(asList(classNames)))
  }
```

- [ ] **Step 4: Run the test**

```bash
cd plugins/java-testng && latte test --test=JavaTestNGPluginTest
```

Expected: `moduleBuildSeparate` passes. All other `JavaTestNGPluginTest` tests continue to pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/java-testng/src/main/groovy/org/lattejava/plugin/java/testng/JavaTestNGPlugin.groovy \
        plugins/java-testng/src/test/groovy/org/lattejava/plugin/java/testng/JavaTestNGPluginTest.groovy
git commit -m "Implement separate test module run branch in JavaTestNGPlugin

When testModuleBuild is true, test() now puts all deps and both
publications on --module-path, uses --add-modules <testModule>, and
launches TestNG via --module org.testng/org.testng.TestNG. The test
module's own module-info.java is responsible for requires and opens."
```

---

## Task 16: Run the full java-testng plugin test suite and integrate-publish

**Files:**
- None (verification step)

- [ ] **Step 1: Run all tests**

```bash
cd plugins/java-testng && latte test
```

Expected: all tests pass (`all`, `coverage`, `moduleBuild`, `moduleBuildSeparate`, `singleTestSwitch`, `skipTests`, `withExclude`, `withGroup`).

- [ ] **Step 2: Integrate-publish**

```bash
cd plugins/java-testng && latte int
```

Expected: builds and publishes locally.

- [ ] **Step 3: No commit**

---

## Task 17: Final verification — run the CLI project's own test suite

**Files:**
- None (verification step)

- [ ] **Step 1: Run the top-level CLI project tests**

```bash
cd /Users/bpontarelli/dev/latte-java/cli && latte test
```

Expected: no regressions in the top-level CLI project itself.

- [ ] **Step 2: Push the branch**

(Only after the user reviews the full change set.)

```bash
git push -u origin features/test-module
```

---

## Self-Review Notes

- **Spec coverage:** Every section of the spec maps to a task — `JavaSettings` (T1), `JavaPlugin` auto-detect (T2), `JavaPlugin.compileTest` new branch (T5), `JavaPlugin.resolveTestModuleName` (T6), error-case validation (T5+T7), fixtures (T3, T13, T14), new `JavaLayout` in java-testng (T9), `JavaTestNGSettings` (T10), `JavaTestNGPlugin` layout + main-module refactor (T11), test-module auto-detect (T12), `JavaTestNGPlugin.test` new branch + `resolveTestModuleName` (T15), tests (T4, T7, T15).
- **Placeholders:** none.
- **Type consistency:** `testModuleBuild` is spelled identically in both settings classes and throughout. `resolveTestModuleName` is spelled identically in both plugins. `JavaLayout` name is shared; the class lives in two packages.
- **Execution order:** Tasks 1-8 are self-contained to the java plugin; Tasks 9-16 are self-contained to the java-testng plugin; task 17 is final verification. The error-case test (Task 7) comes after the happy-path test (Task 5) so the common error path is already exercised.
