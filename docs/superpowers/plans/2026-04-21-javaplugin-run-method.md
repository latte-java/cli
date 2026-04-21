# JavaPlugin `run` method — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `run(Map)` to `JavaPlugin` so `project.latte` can execute a Java main class (or a `.java` source file) using the project's current module-path/classpath, the targeted JDK, and caller-supplied JVM args, program args, environment, and working directory.

**Architecture:** New public method on `JavaPlugin` + a private helper that inspects resolved classpath/module-path entries to auto-detect whether the main class lives in a module (explicit or automatic) and emits `--module <mod>/<main>` accordingly. Runs the child JVM via `ProcessBuilder.inheritIO()`. Reuses existing `pathString(...)`, `dependencyPlugin.classpath { ... }`, and the ASM-based module-name reader already used by `resolveModuleName()`.

**Tech Stack:** Groovy (plugin code), TestNG + EasyMock (tests), ASM (module-info parsing), `java.util.jar.JarFile` / `java.util.jar.Manifest`, `java.lang.ProcessBuilder`.

**Spec:** `docs/superpowers/specs/2026-04-21-javaplugin-run-method-design.md`

---

## File Structure

**Modify:**

- `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
  - Add `Path javaPath` field.
  - Extend `initialize()` to resolve + validate `javaPath`.
  - Add public `int run(Map<String, Object> attributes)` method.
  - Add private helper nested class `RunEntryMatch` (holds `Path entry` and `String moduleName` — null for non-modular).
  - Add private helpers:
    - `Classpath resolveRunClasspath(List<Map<String, Object>> dependencies, List<Path> libraryDirectories, List<Path> additionalPaths)`
    - `RunEntryMatch findMainClassEntry(List<Path> entries, String main)`
    - `String readModuleNameFromBytes(byte[] moduleInfoBytes)` — extracted from existing `resolveModuleName()` body so both callers share it.

- `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`
  - Add test methods for `run` scenarios (see tasks below).

- `plugins/java/test-project/src/main/java/org/lattejava/test/MyClass.java`
  - Add a `public static void main(String[] args)` that writes a marker file and honors env + working-dir for integration tests.

- `plugins/java/test-module/src/main/java/org/lattejava/test/MyClass.java`
  - Add the same `main(...)` so the module-build run scenario has an entry point.

**Create:**

- `plugins/java/test-project/src/main/tools/Hello.java` — a tiny single-file source for the source-file-mode test.

- Test-only fixture JARs produced in-test via `JarBuilder` / `java.util.jar.JarOutputStream` for unit tests of `findMainClassEntry`:
  - an explicit-module JAR (contains `module-info.class` + a class),
  - an automatic-module JAR (no `module-info.class`, `Automatic-Module-Name` in manifest),
  - a non-modular JAR.

These fixture JARs are created in temp directories inside the test methods — no new files checked into the repo.

---

## Task 1: Add `javaPath` field and validate in `initialize()`

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Write the failing test**

Add this test to `JavaPluginTest.groovy` (place alphabetically among the `@Test void ...` methods):

```groovy
@Test
void runInitializeResolvesJavaPath() throws Exception {
  Output output = new SystemOutOutput(true)

  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
  plugin.settings.javaVersion = "25"

  assertNull(plugin.javaPath)

  // initialize() is private; trigger it by calling a public method that uses it.
  // compileMain() calls initialize() via compileInternal(). We just need initialize to run;
  // a fresh compile will exit early if there are no files, which is fine here.
  plugin.clean()
  plugin.compileMain()

  assertNotNull(plugin.javaPath, "Expected initialize() to resolve javaPath")
  assertTrue(Files.isRegularFile(plugin.javaPath), "Expected javaPath [" + plugin.javaPath + "] to exist")
  assertTrue(Files.isExecutable(plugin.javaPath), "Expected javaPath [" + plugin.javaPath + "] to be executable")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: The new test fails with `AssertionError` on `assertNotNull(plugin.javaPath)` because the field does not yet exist (or is null after initialize).

- [ ] **Step 3: Add `javaPath` field and validation**

In `JavaPlugin.groovy`, add the field next to the existing `javacPath` / `javaDocPath` fields (keep alphabetical among fields of the same visibility):

```groovy
  Path javaDocPath

  Path javaPath

  Path javacPath
```

Then extend `initialize()` (after the existing `javaDocPath` block) to resolve and validate `javaPath`:

```groovy
    javaPath = Paths.get(javaHome, "bin/java")
    if (!Files.isRegularFile(javaPath)) {
      fail("The java executable [%s] does not exist.", javaPath.toAbsolutePath())
    }
    if (!Files.isExecutable(javaPath)) {
      fail("The java executable [%s] is not executable.", javaPath.toAbsolutePath())
    }
```

Also change the early-return guard at the top of `initialize()` from `if (javacPath)` to remain in place — `javacPath` is still resolved, so that guard is fine.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `runInitializeResolvesJavaPath` passes. All pre-existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy \
        plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: resolve javaPath in initialize()"
```

---

## Task 2: Extract `readModuleNameFromBytes` helper

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`

Context: The existing `resolveModuleName()` method reads a module name from `module-info.class` bytes via ASM. Task 3+ needs to do the same thing against JAR-entry bytes. Extract the ASM-reading into a shared helper and route `resolveModuleName()` through it.

- [ ] **Step 1: Add the helper and refactor `resolveModuleName()` to use it**

Add this private helper (place alphabetically among the existing private methods):

```groovy
  private static String readModuleNameFromBytes(byte[] moduleInfoBytes) {
    ClassReader reader = new ClassReader(moduleInfoBytes)
    String[] result = new String[1]
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      ModuleVisitor visitModule(String name, int access, String version) {
        result[0] = name
        return null
      }
    }, 0)
    return result[0]
  }
```

Then simplify `resolveModuleName()`:

```groovy
  private String resolveModuleName() {
    Path moduleInfoClass = project.directory.resolve(layout.mainBuildDirectory).resolve("module-info.class")
    if (!Files.isRegularFile(moduleInfoClass)) {
      fail("Module build is enabled but module-info.class was not found in [%s]. Ensure main sources are compiled first.", layout.mainBuildDirectory)
    }

    String name = readModuleNameFromBytes(Files.readAllBytes(moduleInfoClass))
    if (!name) {
      fail("Failed to extract module name from [%s]", moduleInfoClass)
    }

    return name
  }
```

- [ ] **Step 2: Run the existing module-build test to verify no regression**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `moduleBuild` and `moduleBuildSeparate` still pass (they exercise `resolveModuleName()` transitively via `compileTest()`).

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy
git commit -m "java plugin: extract readModuleNameFromBytes helper"
```

---

## Task 3: Add `main` methods to fixture classes

**Files:**
- Modify: `plugins/java/test-project/src/main/java/org/lattejava/test/MyClass.java`
- Modify: `plugins/java/test-module/src/main/java/org/lattejava/test/MyClass.java`
- Create: `plugins/java/test-project/src/main/tools/Hello.java`

Context: Later tasks need a runnable entry point in each fixture. The fixtures log their environment + working directory + arg values to a marker file under a path supplied by the test so `inheritIO()` doesn't block our assertions.

- [ ] **Step 1: Add `main` to test-project `MyClass.java`**

Edit `plugins/java/test-project/src/main/java/org/lattejava/test/MyClass.java` — add a `main(String[])` method AFTER the existing `doSomething()`. The class already uses slf4j; don't change imports.

```java
  public static void main(String[] args) throws Exception {
    String markerPath = System.getProperty("latte.run.marker");
    if (markerPath == null) {
      throw new IllegalStateException("latte.run.marker system property is required");
    }
    String exitCodeStr = System.getProperty("latte.run.exitCode", "0");
    int exitCode = Integer.parseInt(exitCodeStr);

    StringBuilder sb = new StringBuilder();
    sb.append("pwd=").append(new java.io.File("").getAbsolutePath()).append('\n');
    sb.append("args=").append(String.join(",", args)).append('\n');
    sb.append("env.LATTE_RUN_TEST=").append(String.valueOf(System.getenv("LATTE_RUN_TEST"))).append('\n');
    sb.append("env.PATH.present=").append(System.getenv("PATH") != null).append('\n');

    java.nio.file.Files.writeString(java.nio.file.Path.of(markerPath), sb.toString());
    System.exit(exitCode);
  }
```

- [ ] **Step 2: Add the same `main` to test-module `MyClass.java`**

Edit `plugins/java/test-module/src/main/java/org/lattejava/test/MyClass.java` — append the identical `main(String[])` shown above.

- [ ] **Step 3: Create `Hello.java` for source-file mode**

Create `plugins/java/test-project/src/main/tools/Hello.java`:

```java
/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
public class Hello {
  public static void main(String[] args) throws Exception {
    String markerPath = System.getProperty("latte.run.marker");
    if (markerPath == null) {
      throw new IllegalStateException("latte.run.marker system property is required");
    }
    java.nio.file.Files.writeString(java.nio.file.Path.of(markerPath), "hello from source file");
  }
}
```

Note: this file lives outside `src/main/java` so `compileMain()` does not pick it up — single-file source execution doesn't need it compiled. The directory name `src/main/tools` is arbitrary; pick any path outside the Java source roots.

- [ ] **Step 4: Verify the existing test suite still compiles & passes**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: All tests pass. `MyClass` now has an unused `main` method; existing tests that only call `doSomething()` indirectly are unaffected.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/test-project/src/main/java/org/lattejava/test/MyClass.java \
        plugins/java/test-module/src/main/java/org/lattejava/test/MyClass.java \
        plugins/java/test-project/src/main/tools/Hello.java
git commit -m "java plugin: add main entry points to run test fixtures"
```

---

## Task 4: Implement `run()` skeleton — attribute validation only

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Write the failing test**

Add to `JavaPluginTest.groovy`:

```groovy
@Test
void runFailsWhenMainMissing() throws Exception {
  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
  plugin.settings.javaVersion = "25"

  try {
    plugin.run([:])
    fail("Expected run() to fail when [main] attribute is missing")
  } catch (RuntimeException expected) {
    assertTrue(expected.getMessage().contains("[main]"),
        "Unexpected error message: " + expected.getMessage())
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: Compile failure — `run` method does not exist on `JavaPlugin`.

- [ ] **Step 3: Add the skeleton**

In `JavaPlugin.groovy`, add imports in the correct groups (existing file has one group for third-party/Latte imports, then a blank line, then a group of `java.*` imports — each alphabetized):

Add to the upper group (alphabetical; insert between `FilePlugin` and `ClassReader`):

```groovy
import org.lattejava.lang.Classpath
```

Add to the lower `java.*` group (alphabetical):

```groovy
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest
```

Then add the `run` method near the other public methods (alphabetical among them — place between `printJDKModuleDeps` and `compile`/`compileMain`/etc.; file currently isn't strictly alphabetized, so placing alongside the other runtime-style methods is acceptable):

```groovy
  /**
   * Runs a Java main class or a {@code .java} source file using the project's current
   * module-path/classpath. Returns the child process exit code; fails the build on a non-zero
   * exit when {@code failOnError} is {@code true} (the default).
   *
   * @param attributes Named attributes. See the design doc for the full list.
   * @return The child process exit code.
   */
  int run(Map<String, Object> attributes) {
    init()

    if (!GroovyTools.attributesValid(attributes,
        ["additionalClasspath", "arguments", "dependencies", "environment", "failOnError",
         "jvmArguments", "main", "workingDirectory"],
        ["main"],
        ["additionalClasspath": List.class,
         "arguments": String.class,
         "dependencies": List.class,
         "environment": Map.class,
         "failOnError": Boolean.class,
         "jvmArguments": String.class,
         "main": String.class,
         "workingDirectory": Object.class])) {
      fail("You must supply the [main] attribute to java.run(), e.g.:\n\n" +
          "  java.run(main: \"com.example.App\")")
    }

    // TODO tasks 5+: source-file mode, entry-point resolution, execution.
    return 0
  }
```

Yes, this has an explicit `TODO` — it is removed in later tasks. Keep it only until Task 7 finishes. The purpose here is to make the attribute-validation test pass with the smallest possible code.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `runFailsWhenMainMissing` passes. Other tests unaffected.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy \
        plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: add run() skeleton with attribute validation"
```

---

## Task 5: Source-file mode

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Write the failing tests**

Add to `JavaPluginTest.groovy`:

```groovy
@Test
void runSourceFileSucceeds() throws Exception {
  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
  plugin.settings.javaVersion = "25"

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  int rc = plugin.run(
      main: "src/main/tools/Hello.java",
      jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
  )

  assertEquals(rc, 0, "Expected java to exit 0")
  assertTrue(Files.isRegularFile(marker), "Expected marker file [" + marker + "] to be written")
  assertEquals(Files.readString(marker), "hello from source file")
}

@Test
void runSourceFileMissingFails() throws Exception {
  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
  plugin.settings.javaVersion = "25"

  try {
    plugin.run(main: "src/main/tools/DoesNotExist.java")
    fail("Expected run() to fail for a missing source file")
  } catch (RuntimeException expected) {
    assertTrue(expected.getMessage().contains("does not exist or is not readable"),
        "Unexpected error message: " + expected.getMessage())
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `runSourceFileSucceeds` returns `rc == 0` but the marker file is never written (because the skeleton doesn't actually launch java), so the `Files.isRegularFile(marker)` assertion fails. `runSourceFileMissingFails` fails because no exception is thrown.

- [ ] **Step 3: Implement source-file mode**

Replace the `run()` body (the part after the validation block) with:

```groovy
    GroovyTools.putDefaults(attributes, [
        "additionalClasspath": [] as List<Path>,
        "arguments": "",
        "dependencies": defaultRunDependencies(),
        "environment": [:] as Map<String, String>,
        "failOnError": true,
        "jvmArguments": "",
        "workingDirectory": project.directory
    ])

    initialize()

    String main = attributes["main"].toString()
    String jvmArguments = attributes["jvmArguments"].toString()
    String arguments = attributes["arguments"].toString()
    List<Map<String, Object>> dependencies = (List<Map<String, Object>>) attributes["dependencies"]
    List<Path> additionalClasspath = ((List<Object>) attributes["additionalClasspath"])
        .collect { FileTools.toPath(it) }
    Path workingDirectory = FileTools.toPath(attributes["workingDirectory"])
    Map<String, String> environment = (Map<String, String>) attributes["environment"]
    boolean failOnError = (Boolean) attributes["failOnError"]

    // Build the additional-paths list: caller extras + the project's main publications.
    List<Path> publicationPaths = project.publications.group("main")
        .collect { it.file.toAbsolutePath() }
    List<Path> allAdditionalPaths = new ArrayList<>(additionalClasspath)
    allAdditionalPaths.addAll(publicationPaths)

    String pathArgs
    String entryPoint

    if (main.endsWith(".java")) {
      Path resolvedSource = workingDirectory.resolve(main)
      if (!Files.isRegularFile(resolvedSource) || !Files.isReadable(resolvedSource)) {
        fail("Main source file [%s] does not exist or is not readable", resolvedSource.toAbsolutePath())
      }
      pathArgs = pathString(dependencies, settings.libraryDirectories,
          allAdditionalPaths.toArray(new Path[0]))
      entryPoint = resolvedSource.toAbsolutePath().toString()
    } else {
      // Handled in Task 6+.
      fail("Class-name mode not yet implemented")
    }

    return executeRun(jvmArguments, pathArgs, entryPoint, arguments,
        workingDirectory, environment, failOnError)
```

Add two private helpers at the bottom of the file (alphabetical among private methods):

```groovy
  private static List<Map<String, Object>> defaultRunDependencies() {
    return [
        [group: "compile", transitive: true, fetchSource: false,
            transitiveGroups: ["compile", "runtime", "provided"]],
        [group: "runtime", transitive: true, fetchSource: false,
            transitiveGroups: ["compile", "runtime", "provided"]],
        [group: "provided", transitive: true, fetchSource: false,
            transitiveGroups: ["compile", "runtime", "provided"]]
    ]
  }

  private int executeRun(String jvmArguments, String pathArgs, String entryPoint,
                         String arguments, Path workingDirectory,
                         Map<String, String> environment, boolean failOnError) {
    String command = "${javaPath} ${jvmArguments} ${pathArgs} ${entryPoint} ${arguments}"
    output.debugln("Executing java command [%s]", command)

    List<String> args = new StringTokenizer(command).toList() as List<String>

    ProcessBuilder pb = new ProcessBuilder(args)
        .inheritIO()
        .directory(workingDirectory.toFile())
    pb.environment().putAll(environment)

    Process process = pb.start()
    int exitCode = process.waitFor()

    if (exitCode != 0 && failOnError) {
      fail("java command failed with exit code [%d]", exitCode)
    }

    return exitCode
  }
```

Also import `org.lattejava.cli.parser.groovy.GroovyTools` if not already imported (check the top of the file — it already imports `GroovyTools`, so nothing to add).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `runSourceFileSucceeds` passes (the child JVM actually runs `Hello.java` and writes the marker file). `runSourceFileMissingFails` passes (the `does not exist or is not readable` check fires before we spawn the JVM). Other tests still pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy \
        plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: implement run() source-file mode"
```

---

## Task 6: Entry-point resolution scaffolding + `findMainClassEntry` helper

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

Context: This task introduces the reusable helper that walks resolved path entries and identifies which one contains the main class (plus its module name, if any). We unit-test the helper directly against hand-built fixture directories and JARs.

- [ ] **Step 1: Write the failing tests for the helper**

Add to `JavaPluginTest.groovy`. These tests build small fixture artifacts in a temp directory and call the helper directly:

```groovy
@Test
void findMainClassEntryDirectoryNonModular() throws Exception {
  Path tmp = Files.createTempDirectory("latte-findmain")
  Path classesDir = tmp.resolve("classes")
  writeClassFile(classesDir, "com/example/App.class", "com.example.App".bytes) // bytes don't matter

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([classesDir], "com.example.App")
  assertNotNull(match)
  assertEquals(match.entry, classesDir)
  assertNull(match.moduleName)
}

@Test
void findMainClassEntryDirectoryWithModuleInfo() throws Exception {
  Path tmp = Files.createTempDirectory("latte-findmain")
  Path classesDir = tmp.resolve("classes")
  writeClassFile(classesDir, "com/example/App.class", new byte[0])
  writeClassFile(classesDir, "module-info.class", buildModuleInfoClassBytes("com.example.app"))

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([classesDir], "com.example.App")
  assertNotNull(match)
  assertEquals(match.entry, classesDir)
  assertEquals(match.moduleName, "com.example.app")
}

@Test
void findMainClassEntryJarNonModular() throws Exception {
  Path jar = Files.createTempFile("latte-findmain", ".jar")
  buildJar(jar, null, null, ["com/example/App.class": new byte[0]])

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([jar], "com.example.App")
  assertNotNull(match)
  assertEquals(match.entry, jar)
  assertNull(match.moduleName)
}

@Test
void findMainClassEntryJarAutomaticModule() throws Exception {
  Path jar = Files.createTempFile("latte-findmain", ".jar")
  buildJar(jar, "auto.module.name", null, ["com/example/App.class": new byte[0]])

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([jar], "com.example.App")
  assertNotNull(match)
  assertEquals(match.moduleName, "auto.module.name")
}

@Test
void findMainClassEntryJarExplicitModule() throws Exception {
  Path jar = Files.createTempFile("latte-findmain", ".jar")
  buildJar(jar, null, buildModuleInfoClassBytes("explicit.module.name"),
      ["com/example/App.class": new byte[0]])

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([jar], "com.example.App")
  assertNotNull(match)
  assertEquals(match.moduleName, "explicit.module.name")
}

@Test
void findMainClassEntryReturnsNullWhenMissing() throws Exception {
  Path tmp = Files.createTempDirectory("latte-findmain")

  Output output = new SystemOutOutput(false)
  Project project = new Project(projectDir.resolve("test-project"), output)
  JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)

  def match = plugin.findMainClassEntry([tmp], "com.example.Missing")
  assertNull(match)
}
```

Add these helper methods at the bottom of `JavaPluginTest.groovy` (private static):

```groovy
  private static void writeClassFile(Path baseDir, String relativePath, byte[] bytes) throws Exception {
    Path target = baseDir.resolve(relativePath)
    Files.createDirectories(target.getParent())
    Files.write(target, bytes)
  }

  private static void buildJar(Path jarPath, String automaticModuleName, byte[] moduleInfoClass,
                               Map<String, byte[]> classFiles) throws Exception {
    java.util.jar.Manifest manifest = new java.util.jar.Manifest()
    manifest.mainAttributes.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0")
    if (automaticModuleName != null) {
      manifest.mainAttributes.putValue("Automatic-Module-Name", automaticModuleName)
    }
    Files.deleteIfExists(jarPath)
    try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
        Files.newOutputStream(jarPath), manifest)) {
      if (moduleInfoClass != null) {
        jos.putNextEntry(new java.util.jar.JarEntry("module-info.class"))
        jos.write(moduleInfoClass)
        jos.closeEntry()
      }
      classFiles.each { name, bytes ->
        jos.putNextEntry(new java.util.jar.JarEntry(name))
        jos.write(bytes)
        jos.closeEntry()
      }
    }
  }

  private static byte[] buildModuleInfoClassBytes(String moduleName) {
    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0)
    cw.visit(org.objectweb.asm.Opcodes.V11, org.objectweb.asm.Opcodes.ACC_MODULE,
        "module-info", null, null, null)
    org.objectweb.asm.ModuleVisitor mv = cw.visitModule(moduleName,
        org.objectweb.asm.Opcodes.ACC_OPEN, null)
    mv.visitRequire("java.base", org.objectweb.asm.Opcodes.ACC_MANDATED, null)
    mv.visitEnd()
    cw.visitEnd()
    return cw.toByteArray()
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: Compile failure — `findMainClassEntry` method does not exist on `JavaPlugin`.

- [ ] **Step 3: Implement the helper and its nested match type**

In `JavaPlugin.groovy`, add this nested static class (place at the very bottom of the class, above the closing `}`):

```groovy
  static class RunEntryMatch {
    Path entry
    String moduleName  // null if non-modular
  }
```

Add the helper (alphabetical among private methods):

```groovy
  RunEntryMatch findMainClassEntry(List<Path> entries, String main) {
    String classPath = main.replace('.', '/') + ".class"

    for (Path entry : entries) {
      if (Files.isDirectory(entry)) {
        Path classFile = entry.resolve(classPath)
        if (!Files.isRegularFile(classFile)) {
          continue
        }

        RunEntryMatch m = new RunEntryMatch(entry: entry)
        Path moduleInfo = entry.resolve("module-info.class")
        if (Files.isRegularFile(moduleInfo)) {
          m.moduleName = readModuleNameFromBytes(Files.readAllBytes(moduleInfo))
        }
        return m
      }

      if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
        try (JarFile jarFile = new JarFile(entry.toFile())) {
          if (jarFile.getJarEntry(classPath) == null) {
            continue
          }

          RunEntryMatch m = new RunEntryMatch(entry: entry)

          JarEntry moduleInfo = jarFile.getJarEntry("module-info.class")
          if (moduleInfo != null) {
            byte[] bytes = jarFile.getInputStream(moduleInfo).readAllBytes()
            m.moduleName = readModuleNameFromBytes(bytes)
            return m
          }

          Manifest manifest = jarFile.getManifest()
          if (manifest != null) {
            String autoName = manifest.getMainAttributes().getValue("Automatic-Module-Name")
            if (autoName != null && !autoName.isEmpty()) {
              m.moduleName = autoName
            }
          }
          return m
        }
      }
    }

    return null
  }
```

Note: the helper is package-private (no `private` modifier) so the test can call it directly. That's consistent with Groovy test access patterns used elsewhere in the file.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: All six new `findMainClassEntry*` tests pass. Other tests still pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy \
        plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: add findMainClassEntry helper for run()"
```

---

## Task 7: Wire class-name mode into `run()`

**Files:**
- Modify: `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

- [ ] **Step 1: Write the failing tests — class name against project publication (classpath build)**

Add to `JavaPluginTest.groovy`:

```groovy
@Test
void runClassNameClasspathBuild() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  output.enableDebug()

  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  project.dependencies = new Dependencies(
      new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
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

  plugin.clean()
  plugin.compileMain()
  plugin.jar()

  // Register the built main JAR as a publication so run() can find it.
  registerMainPublication(project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  int rc = plugin.run(
      main: "org.lattejava.test.MyClass",
      jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
  )

  assertEquals(rc, 0)
  assertTrue(Files.isRegularFile(marker))
  String contents = Files.readString(marker)
  assertTrue(contents.contains("pwd="))
}

@Test
void runClassNameModuleBuild() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  output.enableDebug()

  Project project = new Project(projectDir.resolve("test-module"), output)
  project.group = "org.lattejava.test"
  project.name = "test-module"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))

  project.dependencies = new Dependencies(
      new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
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

  plugin.clean()
  plugin.compileMain()
  plugin.jar()

  registerMainPublication(project, projectDir.resolve("test-module/build/jars/test-module-1.0.0.jar"))

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  int rc = plugin.run(
      main: "org.lattejava.test.MyClass",
      jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
  )

  assertEquals(rc, 0)
  assertTrue(Files.isRegularFile(marker))
}

@Test
void runClassNameNotFoundFails() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))
  project.dependencies = new Dependencies()
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

  try {
    plugin.run(main: "com.example.DoesNotExist")
    fail("Expected run() to fail when main class is not found")
  } catch (RuntimeException expected) {
    assertTrue(expected.getMessage().contains("Main class [com.example.DoesNotExist] was not found"),
        "Unexpected error message: " + expected.getMessage())
  }
}
```

Also add a helper at the bottom of `JavaPluginTest.groovy`:

```groovy
  private static void registerMainPublication(Project project, Path jarPath) throws Exception {
    org.lattejava.dep.domain.Artifact artifact = new org.lattejava.dep.domain.Artifact(
        project.group + ":" + project.name + ":" + project.version.toString() + ":jar")
    org.lattejava.dep.domain.ArtifactMetaData metaData =
        new org.lattejava.dep.domain.ArtifactMetaData(null, project.licenses)
    org.lattejava.dep.domain.Publication publication =
        new org.lattejava.dep.domain.Publication(artifact, metaData, jarPath, null)
    project.publications.add("main", publication)
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: The three new tests fail. `runClassNameClasspathBuild` and `runClassNameModuleBuild` fail at `fail("Class-name mode not yet implemented")` from Task 5. `runClassNameNotFoundFails` fails because that same `fail(...)` message is thrown instead of the expected "Main class [...] was not found" message.

- [ ] **Step 3: Implement class-name mode**

Replace the `else { fail("Class-name mode not yet implemented") }` block in `run()` with:

```groovy
    } else {
      Classpath resolved = resolveRunClasspath(dependencies, settings.libraryDirectories, allAdditionalPaths)
      RunEntryMatch match = findMainClassEntry(resolved.paths, main)
      if (match == null) {
        fail("Main class [%s] was not found on the resolved classpath/module-path", main)
      }

      if (match.moduleName != null) {
        entryPoint = "--module ${match.moduleName}/${main}"
        pathArgs = pathString(dependencies, settings.libraryDirectories,
            allAdditionalPaths.toArray(new Path[0]))
      } else if (settings.moduleBuild) {
        // Non-modular entry inside a module build: split paths so the match lands on -classpath.
        Classpath modulePath = new Classpath()
        Classpath classpath = new Classpath()
        resolved.paths.each { p ->
          if (p == match.entry) {
            classpath.path(p)
          } else {
            modulePath.path(p)
          }
        }
        pathArgs = "${modulePath.toString("--module-path ")} ${classpath.toString("-classpath ")}"
        entryPoint = main
      } else {
        entryPoint = main
        pathArgs = pathString(dependencies, settings.libraryDirectories,
            allAdditionalPaths.toArray(new Path[0]))
      }
    }
```

Add the `resolveRunClasspath` helper at the bottom of the class (alphabetical among private methods):

```groovy
  private Classpath resolveRunClasspath(List<Map<String, Object>> dependencies,
                                        List<Path> libraryDirectories,
                                        List<Path> additionalPaths) {
    List<Path> additionalJARs = new ArrayList<>()
    if (libraryDirectories != null) {
      libraryDirectories.each { path ->
        Path dir = project.directory.resolve(FileTools.toPath(path))
        if (!Files.isDirectory(dir)) {
          return
        }
        Files.list(dir).filter(FileTools.extensionFilter(".jar"))
            .forEach { file -> additionalJARs.add(file.toAbsolutePath()) }
      }
    }

    return dependencyPlugin.classpath {
      dependencies.each { deps -> dependencies(deps) }
      additionalPaths.each { p -> path(location: p) }
      additionalJARs.each { jar -> path(location: jar) }
    }
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: `runClassNameClasspathBuild`, `runClassNameModuleBuild`, and `runClassNameNotFoundFails` all pass. Other tests still pass.

- [ ] **Step 5: Commit**

```bash
git add plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy \
        plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: implement run() class-name mode"
```

---

## Task 8: `failOnError`, `environment`, and `workingDirectory` behavior

**Files:**
- Test: `plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`

The implementation in Task 5 already supports all three. This task adds regression tests that exercise them end-to-end.

- [ ] **Step 1: Write the failing tests**

Add to `JavaPluginTest.groovy`:

```groovy
@Test
void runFailOnErrorFalseReturnsExitCode() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))
  project.dependencies = new Dependencies()
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

  plugin.clean()
  plugin.compileMain()
  plugin.jar()
  registerMainPublication(project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  int rc = plugin.run(
      main: "org.lattejava.test.MyClass",
      jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath() + " -Dlatte.run.exitCode=42",
      failOnError: false
  )

  assertEquals(rc, 42, "Expected the child's exit code to be returned verbatim")
  assertTrue(Files.isRegularFile(marker), "Marker should still be written before System.exit(42)")
}

@Test
void runFailOnErrorTrueFailsTheBuild() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))
  project.dependencies = new Dependencies()
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

  plugin.clean()
  plugin.compileMain()
  plugin.jar()
  registerMainPublication(project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  try {
    plugin.run(
        main: "org.lattejava.test.MyClass",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath() + " -Dlatte.run.exitCode=7"
    )
    fail("Expected run() to fail the build on non-zero exit")
  } catch (RuntimeException expected) {
    assertTrue(expected.getMessage().contains("exit code [7]"),
        "Unexpected error message: " + expected.getMessage())
  }
}

@Test
void runEnvironmentMergesAndWorkingDirectoryHonored() throws Exception {
  def cacheDir = projectDir.resolve("build/cache")
  FileTools.prune(cacheDir)

  Output output = new SystemOutOutput(true)
  Project project = new Project(projectDir.resolve("test-project"), output)
  project.group = "org.lattejava.test"
  project.name = "test-project"
  project.version = new Version("1.0.0")
  project.licenses.add(License.parse("ApacheV2_0", null))
  project.dependencies = new Dependencies()
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

  plugin.clean()
  plugin.compileMain()
  plugin.jar()
  registerMainPublication(project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

  Path marker = Files.createTempFile("latte-run", ".txt")
  Files.deleteIfExists(marker)

  Path workDir = Files.createTempDirectory("latte-run-workdir")

  int rc = plugin.run(
      main: "org.lattejava.test.MyClass",
      jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath(),
      workingDirectory: workDir,
      environment: ["LATTE_RUN_TEST": "hello-from-env"]
  )

  assertEquals(rc, 0)
  String contents = Files.readString(marker)
  assertTrue(contents.contains("env.LATTE_RUN_TEST=hello-from-env"),
      "Expected merged env var; got:\n" + contents)
  assertTrue(contents.contains("env.PATH.present=true"),
      "Expected inherited env to still be present; got:\n" + contents)
  assertTrue(contents.contains("pwd=" + workDir.toRealPath().toString()),
      "Expected pwd to match workingDirectory; got:\n" + contents)
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `cd plugins/java && latte test --test=JavaPluginTest`
Expected: All three new tests pass on the first run — the implementation from Task 5 already supports these knobs. If any fail, fix the underlying code (do not skip or weaken the assertions).

- [ ] **Step 3: Commit**

```bash
git add plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy
git commit -m "java plugin: test run() failOnError, environment, workingDirectory"
```

---

## Task 9: `latte int` and full test sweep

**Files:** none modified.

- [ ] **Step 1: Run the full plugin test suite**

Run: `cd plugins/java && latte test`
Expected: All tests pass, including pre-existing ones (`all`, `moduleBuild`, `moduleBuildSeparate`, `autoDetectRespectsLayout`, `jarjar`, `testModuleBuildWithoutMainModuleBuildFails`).

- [ ] **Step 2: Integration-publish the plugin locally**

Run: `cd plugins/java && latte int`
Expected: Build succeeds; the plugin is published to the local integration cache.

- [ ] **Step 3: Bump the plugin version (follow-up, not in this plan)**

Not part of this plan. The release plugin handles version bumps (`release-plugins` skill) during the normal plugin release flow.

---

## Self-review

- **Spec coverage:**
  - *`main` attribute (class name or `.java` path)* — Task 5 (source-file mode), Task 7 (class-name mode).
  - *`jvmArguments` / `arguments` strings* — Task 5 and Task 7 (used in command assembly).
  - *`dependencies` default = runtime view with transitive compile/runtime/provided* — Task 5 `defaultRunDependencies()`.
  - *`additionalClasspath` default `[]`* — Task 5 default.
  - *`workingDirectory` default `project.directory`* — Task 5 default; Task 8 test.
  - *`environment` merged into inherited env* — Task 5 (`pb.environment().putAll`); Task 8 test.
  - *`failOnError` default true; return exit code* — Task 5 (`executeRun`); Task 8 tests.
  - *Publications always on path* — Task 5 (`publicationPaths` appended to `allAdditionalPaths`).
  - *Auto-detection: explicit module / automatic module / non-modular / split in module build* — Task 6 (helper) + Task 7 (wiring). Note: no end-to-end test for "modular dependency JAR" or "automatic module in module build" — those are covered by Task 6's helper tests. An end-to-end test for those cases would need a real modular Maven dependency as a fixture; skipped in favor of direct helper testing as permitted by the spec's testing section.
  - *Source-file validation* — Task 5 `Files.isRegularFile(...) || !Files.isReadable(...)` check.
  - *"Main class not found" failure* — Task 7 test `runClassNameNotFoundFails`.
  - *`javaPath` existence + executable checks* — Task 1.
- **Placeholder scan:** Task 4's temporary `TODO` is explicitly removed in Task 5 and Task 7. No other placeholders. All code blocks are complete.
- **Type consistency:** `RunEntryMatch` has `Path entry` and `String moduleName` across Tasks 6, 7. `defaultRunDependencies()` referenced in Task 5 and defined in Task 5. `executeRun(...)` signature matches its only caller in Task 5.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-21-javaplugin-run-method.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
