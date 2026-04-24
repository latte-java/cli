# `latte init` template directories — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn `latte init`'s single-file template into a directory tree. Ship built-in `library` (expanded scaffold: `module-info.java` + a `Placeholder` class/test in the derived package) and `web` (starter with a `Main` runnable via source-file mode). Allow `latte init <path>` to use an arbitrary directory as the template. Drop the `--template` switch.

**Architecture:** `InitCommand` resolves a template directory from the first positional argument (default `library`), walks it, preflights file conflicts, and copies each file to the project directory substituting `${...}` variables in **both** path segments and file contents. A small set of derived variables (`${nameId}`, `${package}`, `${packagePath}`) lets templates place files in package-structured directories.

**Tech Stack:** Java 25 (CLI source), TestNG + EasyMock (tests), Savant `file.copy`/`FileSet` (bundling — already recurses).

**Spec:** `docs/superpowers/specs/2026-04-23-init-template-directories-design.md`

---

## File Structure

**Modify:**

- `src/main/java/org/lattejava/cli/command/InitCommand.java`
  - Delete `loadTemplate(RuntimeConfiguration)` and `createDirectoryLayout(Path)`.
  - Add private helpers:
    - `static String substitute(String input, Map<String, String> vars)`
    - `static Map<String, String> deriveVariables(String group, String name, String license)`
    - `Path resolveTemplateDir(RuntimeConfiguration configuration)`
    - `void copyTemplate(Path templateDir, Path projectDir, Map<String, String> vars)`
  - Change `run(RuntimeConfiguration, Output, Path)` ordering: resolve template → prompt → derive vars → copy.

- `src/test/java/org/lattejava/cli/command/InitCommandTest.java`
  - Rewrite fixture setup to build temp template **directories**.
  - Replace `config.switches.add("template", ...)` with `config.args.add(...)` (positional).
  - Add direct-unit tests for each helper before integration tests.

**Create (templates — shipped via `bundle` target):**

- `src/main/templates/library/project.latte` (moved from `src/main/templates/project.latte`)
- `src/main/templates/library/src/main/java/module-info.java`
- `src/main/templates/library/src/main/java/${packagePath}/Placeholder.java`
- `src/main/templates/library/src/main/resources/.gitkeep`
- `src/main/templates/library/src/test/java/module-info.java`
- `src/main/templates/library/src/test/java/${packagePath}/PlaceholderTest.java`
- `src/main/templates/library/src/test/resources/.gitkeep`
- `src/main/templates/web/project.latte`
- `src/main/templates/web/src/main/java/Main.java`
- `src/main/templates/web/src/test/java/MainTest.java`
- `src/main/templates/web/web/static/.gitkeep`

Note on module system: the spec specified a main `module-info.java` for the library template. Latte projects with a main module also carry a **test** `module-info.java` (see `http/src/test/java/module-info.java` in the sibling repo). Without the test module-info the TestNG reflective access fails. So the library template includes both. This is an elaboration on the spec — flagged here, not in the brainstorming doc. If you (the executor) disagree, stop and ask before implementing Task 7.

**Delete:**

- `src/main/templates/project.latte` (moved into `library/`).

**Bundle target:** No change. `project.latte:82-84` already does
`file.copy(to: "${bundleDir}/templates") { fileSet(dir: "src/main/templates", includePatterns: [/.*/]) }`. `FileSet.toFileInfos` uses `Files.walkFileTree`, which recurses. Same for the mirrored `build.savant` target. `.gitkeep` files (in empty dirs) are required because `walkFileTree.visitFile` fires only for files.

---

## Task 1: Relocate the current template into `library/`

**Files:**
- Move: `src/main/templates/project.latte` → `src/main/templates/library/project.latte`

Rationale: Move first so the existing tests (which use their own temp template files via `--template` — they don't hit the default path) keep passing through the refactor. The default-path lookup in `InitCommand` is still broken after this step — that's fixed in Task 6.

- [ ] **Step 1: Move the file**

```bash
mkdir -p src/main/templates/library
git mv src/main/templates/project.latte src/main/templates/library/project.latte
```

- [ ] **Step 2: Run existing tests to confirm nothing depends on the old path**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS. If any fails, the failure is unrelated to this move — the move is a pure rename and existing tests pass `config.switches.add("template", <temp file>)` rather than hitting `$latte.home/templates/project.latte`.

- [ ] **Step 3: Commit**

```bash
git add src/main/templates/
git commit -m "chore: move project.latte template into library/ subdirectory"
```

---

## Task 2: Add `substitute` helper (TDD)

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Add failing tests**

Append these test methods to `InitCommandTest`:

```java
@Test
public void substituteReplacesSingleVariable() {
  String result = InitCommand.substitute("hello ${who}", Map.of("who", "world"));
  assertEquals(result, "hello world");
}

@Test
public void substituteReplacesMultipleOccurrences() {
  String result = InitCommand.substitute("${x}/${x}/${x}", Map.of("x", "a"));
  assertEquals(result, "a/a/a");
}

@Test
public void substituteLeavesUnknownVariablesIntact() {
  String result = InitCommand.substitute("hi ${who} from ${where}", Map.of("who", "me"));
  assertEquals(result, "hi me from ${where}");
}

@Test
public void substituteReturnsInputWhenNoVariablesPresent() {
  String result = InitCommand.substitute("plain text", Map.of("x", "y"));
  assertEquals(result, "plain text");
}
```

Add the import at the top of the test file (in the existing `java.*` group):

```java
import java.util.Map;
```

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
latte test --test=InitCommandTest
```

Expected: 4 new tests fail with "cannot find symbol: method substitute".

- [ ] **Step 3: Implement `substitute`**

Add this `static` method to `InitCommand` (alphabetized under private static methods; if no static methods exist yet, the "Order inside classes" rule puts static methods after constructors but before instance methods):

```java
static String substitute(String input, Map<String, String> vars) {
  String result = input;
  for (Map.Entry<String, String> entry : vars.entrySet()) {
    result = result.replace("${" + entry.getKey() + "}", entry.getValue());
  }
  return result;
}
```

Add the import to `InitCommand.java` (existing `java.*` group):

```java
import java.util.Map;
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: add substitute helper to InitCommand"
```

---

## Task 3: Add `deriveVariables` helper (TDD)

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Add failing tests**

```java
@Test
public void deriveVariablesBasic() {
  Map<String, String> vars = InitCommand.deriveVariables("org.example", "widget", "MIT");
  assertEquals(vars.get("group"), "org.example");
  assertEquals(vars.get("name"), "widget");
  assertEquals(vars.get("license"), "MIT");
  assertEquals(vars.get("nameId"), "widget");
  assertEquals(vars.get("package"), "org.example.widget");
  assertEquals(vars.get("packagePath"), "org/example/widget");
}

@Test
public void deriveVariablesHyphenatedNameBecomesUnderscoreIdentifier() {
  Map<String, String> vars = InitCommand.deriveVariables("org.example", "my-lib", "Apache-2.0");
  assertEquals(vars.get("name"), "my-lib");
  assertEquals(vars.get("nameId"), "my_lib");
  assertEquals(vars.get("package"), "org.example.my_lib");
  assertEquals(vars.get("packagePath"), "org/example/my_lib");
}

@Test
public void deriveVariablesMultiSegmentGroup() {
  Map<String, String> vars = InitCommand.deriveVariables("com.acme.foo", "bar", "MIT");
  assertEquals(vars.get("package"), "com.acme.foo.bar");
  assertEquals(vars.get("packagePath"), "com/acme/foo/bar");
}
```

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
latte test --test=InitCommandTest
```

Expected: 3 new tests fail with "cannot find symbol: method deriveVariables".

- [ ] **Step 3: Implement `deriveVariables`**

Add to `InitCommand` (alphabetized with `substitute`):

```java
static Map<String, String> deriveVariables(String group, String name, String license) {
  String nameId = name.replace('-', '_');
  String packageName = group + "." + nameId;
  String packagePath = packageName.replace('.', '/');
  return Map.of(
      "group", group,
      "name", name,
      "license", license,
      "nameId", nameId,
      "package", packageName,
      "packagePath", packagePath
  );
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: add deriveVariables helper to InitCommand"
```

---

## Task 4: Add `resolveTemplateDir` helper (TDD)

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Add failing tests**

Append to `InitCommandTest`:

```java
@Test
public void resolveTemplateDirDefaultsToLibrary() throws IOException {
  Path fakeLatteHome = Files.createTempDirectory("latte-home");
  Path libraryDir = fakeLatteHome.resolve("templates/library");
  Files.createDirectories(libraryDir);

  String original = System.getProperty("latte.home");
  System.setProperty("latte.home", fakeLatteHome.toString());
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    Path result = new InitCommand().resolveTemplateDir(config);
    assertEquals(result, libraryDir);
  } finally {
    if (original == null) {
      System.clearProperty("latte.home");
    } else {
      System.setProperty("latte.home", original);
    }
    deleteRecursively(fakeLatteHome);
  }
}

@Test
public void resolveTemplateDirNamedTemplate() throws IOException {
  Path fakeLatteHome = Files.createTempDirectory("latte-home");
  Path webDir = fakeLatteHome.resolve("templates/web");
  Files.createDirectories(webDir);

  String original = System.getProperty("latte.home");
  System.setProperty("latte.home", fakeLatteHome.toString());
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("web");
    Path result = new InitCommand().resolveTemplateDir(config);
    assertEquals(result, webDir);
  } finally {
    if (original == null) {
      System.clearProperty("latte.home");
    } else {
      System.setProperty("latte.home", original);
    }
    deleteRecursively(fakeLatteHome);
  }
}

@Test
public void resolveTemplateDirNamedTemplateMissing() throws IOException {
  Path fakeLatteHome = Files.createTempDirectory("latte-home");
  Files.createDirectories(fakeLatteHome.resolve("templates"));

  String original = System.getProperty("latte.home");
  System.setProperty("latte.home", fakeLatteHome.toString());
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("nonexistent");
    new InitCommand().resolveTemplateDir(config);
    fail("Expected RuntimeFailureException");
  } catch (RuntimeFailureException e) {
    assertTrue(e.getMessage().contains("[nonexistent]"));
    assertTrue(e.getMessage().contains("not found"));
  } finally {
    if (original == null) {
      System.clearProperty("latte.home");
    } else {
      System.setProperty("latte.home", original);
    }
    deleteRecursively(fakeLatteHome);
  }
}

@Test
public void resolveTemplateDirPathWithSlash() throws IOException {
  Path customDir = Files.createTempDirectory("custom-template");
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add(customDir.toString());
    Path result = new InitCommand().resolveTemplateDir(config);
    assertEquals(result, customDir);
  } finally {
    deleteRecursively(customDir);
  }
}

@Test
public void resolveTemplateDirPathMissing() {
  RuntimeConfiguration config = new RuntimeConfiguration();
  config.args.add("/definitely/does/not/exist/anywhere");
  try {
    new InitCommand().resolveTemplateDir(config);
    fail("Expected RuntimeFailureException");
  } catch (RuntimeFailureException e) {
    assertTrue(e.getMessage().contains("[/definitely/does/not/exist/anywhere]"));
    assertTrue(e.getMessage().contains("not found"));
  }
}

@Test
public void resolveTemplateDirTildeExpansion() throws IOException {
  String home = System.getProperty("user.home");
  Path tildeTarget = Files.createTempDirectory(Path.of(home), "tilde-template");
  try {
    String relative = tildeTarget.getFileName().toString();
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("~/" + relative);
    Path result = new InitCommand().resolveTemplateDir(config);
    assertEquals(result.toRealPath(), tildeTarget.toRealPath());
  } finally {
    deleteRecursively(tildeTarget);
  }
}

@Test
public void resolveTemplateDirMissingLatteHome() {
  String original = System.getProperty("latte.home");
  System.clearProperty("latte.home");
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    new InitCommand().resolveTemplateDir(config);
    fail("Expected RuntimeFailureException");
  } catch (RuntimeFailureException e) {
    assertTrue(e.getMessage().contains("latte.home"));
  } finally {
    if (original != null) {
      System.setProperty("latte.home", original);
    }
  }
}
```

Also add a private static helper on `InitCommandTest` (end of class):

```java
private static void deleteRecursively(Path root) throws IOException {
  if (!Files.exists(root)) {
    return;
  }
  Files.walk(root)
      .sorted(Comparator.reverseOrder())
      .forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
      });
}
```

The existing `afterMethod` duplicates this logic — later tasks will collapse it. Leave it alone for this step.

- [ ] **Step 2: Run to confirm all new tests fail**

```bash
latte test --test=InitCommandTest
```

Expected: 7 new tests fail with "cannot find symbol: method resolveTemplateDir".

- [ ] **Step 3: Implement `resolveTemplateDir`**

Add to `InitCommand`:

```java
Path resolveTemplateDir(RuntimeConfiguration configuration) {
  String arg = configuration.args.isEmpty() ? null : configuration.args.getFirst();

  if (arg != null && isPathLike(arg)) {
    Path customDir = expandTilde(arg);
    if (!Files.isDirectory(customDir)) {
      throw new RuntimeFailureException("Template directory not found: [" + customDir + "]");
    }
    return customDir;
  }

  String templateName = arg != null ? arg : "library";
  String latteHome = System.getProperty("latte.home");
  if (latteHome == null) {
    throw new RuntimeFailureException("The latte.home system property is not set. Is Latte installed correctly?");
  }

  Path namedDir = Path.of(latteHome, "templates", templateName);
  if (!Files.isDirectory(namedDir)) {
    throw new RuntimeFailureException("Template [" + templateName + "] not found at [" + namedDir + "]. Is Latte installed correctly?");
  }
  return namedDir;
}

private static boolean isPathLike(String arg) {
  return arg.contains("/") || arg.contains("\\") || arg.startsWith("~") || Path.of(arg).isAbsolute();
}

private static Path expandTilde(String arg) {
  if (arg.equals("~")) {
    return Path.of(System.getProperty("user.home"));
  }
  if (arg.startsWith("~/") || arg.startsWith("~\\")) {
    return Path.of(System.getProperty("user.home"), arg.substring(2));
  }
  return Path.of(arg);
}
```

Ordering inside `InitCommand`: `resolveTemplateDir` is a public (package-private) instance method; `isPathLike` and `expandTilde` are private static methods. Both go in their respective alphabetized groups per the class-order rule.

- [ ] **Step 4: Run tests to confirm they pass**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: add resolveTemplateDir to InitCommand"
```

---

## Task 5: Add `copyTemplate` with preflight and write (TDD)

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Add failing tests**

```java
@Test
public void copyTemplateWritesFilesWithContentSubstitution() throws IOException {
  Path tmpl = Files.createTempDirectory("tmpl");
  Path dest = Files.createTempDirectory("dest");
  try {
    Files.writeString(tmpl.resolve("project.latte"), "name: ${name}");

    new InitCommand().copyTemplate(tmpl, dest, Map.of("name", "widget"));

    assertEquals(Files.readString(dest.resolve("project.latte")), "name: widget");
  } finally {
    deleteRecursively(tmpl);
    deleteRecursively(dest);
  }
}

@Test
public void copyTemplateSubstitutesPathSegments() throws IOException {
  Path tmpl = Files.createTempDirectory("tmpl");
  Path dest = Files.createTempDirectory("dest");
  try {
    Path nested = tmpl.resolve("src/main/java/${packagePath}");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("Placeholder.java"), "package ${package};");

    Map<String, String> vars = Map.of("package", "org.example.my_lib", "packagePath", "org/example/my_lib");
    new InitCommand().copyTemplate(tmpl, dest, vars);

    Path expected = dest.resolve("src/main/java/org/example/my_lib/Placeholder.java");
    assertTrue(Files.isRegularFile(expected));
    assertEquals(Files.readString(expected), "package org.example.my_lib;");
  } finally {
    deleteRecursively(tmpl);
    deleteRecursively(dest);
  }
}

@Test
public void copyTemplatePreflightAbortsWhenTargetExists() throws IOException {
  Path tmpl = Files.createTempDirectory("tmpl");
  Path dest = Files.createTempDirectory("dest");
  try {
    Files.writeString(tmpl.resolve("a.txt"), "template-a");
    Files.writeString(tmpl.resolve("b.txt"), "template-b");

    // Pre-create b.txt in destination
    Files.writeString(dest.resolve("b.txt"), "existing-b");

    try {
      new InitCommand().copyTemplate(tmpl, dest, Map.of());
      fail("Expected RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[b.txt]"));
      assertTrue(e.getMessage().contains("already exists"));
    }

    // Preflight aborts before any writes: a.txt must NOT have been written
    assertFalse(Files.exists(dest.resolve("a.txt")));
    // And b.txt's existing content must be untouched
    assertEquals(Files.readString(dest.resolve("b.txt")), "existing-b");
  } finally {
    deleteRecursively(tmpl);
    deleteRecursively(dest);
  }
}

@Test
public void copyTemplateCreatesParentDirectories() throws IOException {
  Path tmpl = Files.createTempDirectory("tmpl");
  Path dest = Files.createTempDirectory("dest");
  try {
    Path nested = tmpl.resolve("deeply/nested/dir");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("file.txt"), "hi");

    new InitCommand().copyTemplate(tmpl, dest, Map.of());

    assertTrue(Files.isRegularFile(dest.resolve("deeply/nested/dir/file.txt")));
  } finally {
    deleteRecursively(tmpl);
    deleteRecursively(dest);
  }
}

@Test
public void copyTemplatePreservesEmptyGitkeepFiles() throws IOException {
  Path tmpl = Files.createTempDirectory("tmpl");
  Path dest = Files.createTempDirectory("dest");
  try {
    Path emptyDir = tmpl.resolve("resources");
    Files.createDirectories(emptyDir);
    Files.writeString(emptyDir.resolve(".gitkeep"), "");

    new InitCommand().copyTemplate(tmpl, dest, Map.of());

    Path result = dest.resolve("resources/.gitkeep");
    assertTrue(Files.isRegularFile(result));
    assertEquals(Files.size(result), 0L);
  } finally {
    deleteRecursively(tmpl);
    deleteRecursively(dest);
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
latte test --test=InitCommandTest
```

Expected: 5 new tests fail with "cannot find symbol: method copyTemplate".

- [ ] **Step 3: Implement `copyTemplate`**

Add imports at the top of `InitCommand.java`:

```java
import java.util.ArrayList;
import java.util.List;
```

(Alphabetize within the existing `java.*` import group.)

Add a private nested record `PendingFile` and the `copyTemplate` method:

```java
void copyTemplate(Path templateDir, Path projectDir, Map<String, String> vars) {
  List<PendingFile> pending = collectFiles(templateDir, projectDir, vars);

  for (PendingFile file : pending) {
    if (Files.isRegularFile(file.target)) {
      throw new RuntimeFailureException("[" + file.relativeResolved + "] already exists");
    }
  }

  for (PendingFile file : pending) {
    try {
      String content = Files.readString(file.source, StandardCharsets.UTF_8);
      String substituted = substitute(content, vars);
      Path parent = file.target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file.target, substituted, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to write [" + file.target + "]: " + e.getMessage());
    }
  }
}

private static List<PendingFile> collectFiles(Path templateDir, Path projectDir, Map<String, String> vars) {
  List<PendingFile> results = new ArrayList<>();
  try (var stream = Files.walk(templateDir)) {
    stream.filter(Files::isRegularFile).forEach(source -> {
      String relative = templateDir.relativize(source).toString();
      String resolved = substitute(relative, vars);
      Path target = projectDir.resolve(resolved);
      results.add(new PendingFile(source, target, resolved));
    });
  } catch (IOException e) {
    throw new RuntimeFailureException("Failed to walk template directory [" + templateDir + "]: " + e.getMessage());
  }
  return results;
}

private record PendingFile(Path source, Path target, String relativeResolved) {
}
```

The nested record goes at the bottom of the class (per "Order inside classes": nested classes last).

- [ ] **Step 4: Run tests to confirm they pass**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: add copyTemplate with preflight and path substitution"
```

---

## Task 6: Rewire `run()` and rewrite existing tests

This task swaps `InitCommand`'s public behavior to use the new helpers, removes `loadTemplate` and `createDirectoryLayout`, and migrates every existing test from `--template` switch + single template file to a positional argument + template directory.

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Rewrite the public `run` method and remove dead helpers**

Replace `InitCommand.run(RuntimeConfiguration, Output, Path)` with:

```java
public void run(RuntimeConfiguration configuration, Output output, Path projectDir) {
  Path templateDir = resolveTemplateDir(configuration);

  String group = promptGroup(output);
  String name = promptName(output, projectDir);
  String license = promptLicense(output);

  Map<String, String> vars = deriveVariables(group, name, license);

  copyTemplate(templateDir, projectDir, vars);

  output.infoln("Created project [%s:%s] from template [%s]", group, name, templateDir.getFileName());
}
```

Delete `loadTemplate(RuntimeConfiguration)` entirely. Delete `createDirectoryLayout(Path)` entirely.

Keep `run(RuntimeConfiguration, Output, Project)` unchanged (it delegates to the `Path`-overload).

Keep the three `prompt*` methods and `guessProjectName` unchanged.

- [ ] **Step 2: Rewrite the existing `beforeMethod`/`afterMethod` in `InitCommandTest`**

Replace the top of `InitCommandTest` (field declarations, `beforeMethod`, `afterMethod`) with:

```java
private static final String TEST_PROJECT_LATTE = """
    project(group: "${group}", name: "${name}", version: "0.1.0", licenses: ["${license}"]) {
      workflow {
        standard()
      }
    }

    target(name: "clean", description: "Cleans the project") {
    }

    target(name: "build", description: "Compiles and JARs the project") {
    }

    target(name: "test", description: "Runs the project's tests", dependsOn: ["build"]) {
    }
    """;

private Path templateDir;

private Path testDir;

@BeforeMethod
public void beforeMethod() throws IOException {
  testDir = Files.createTempDirectory("latte-init-test");
  templateDir = Files.createTempDirectory("latte-template-dir");
  Files.writeString(templateDir.resolve("project.latte"), TEST_PROJECT_LATTE);
}

@AfterMethod
public void afterMethod() throws IOException {
  deleteRecursively(templateDir);
  deleteRecursively(testDir);
}
```

`deleteRecursively` is the static helper introduced in Task 4. Remove the old file-removal block from `afterMethod`.

- [ ] **Step 3: Rewrite the `configWithTemplate` helper**

Replace `configWithTemplate()` at the bottom of the test class:

```java
private RuntimeConfiguration configWithTemplate() {
  RuntimeConfiguration config = new RuntimeConfiguration();
  config.args.add(templateDir.toString());
  return config;
}
```

- [ ] **Step 4: Rewrite each existing migrated test**

Update the existing test methods one at a time. Full replacements below.

```java
@Test
public void init() throws IOException {
  Scanner scanner = new Scanner("org.example\nmy-library\nApache-2.0\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, testDir);

  Path projectFile = testDir.resolve("project.latte");
  assertTrue(Files.isRegularFile(projectFile));

  String content = Files.readString(projectFile);
  assertTrue(content.contains("group: \"org.example\""));
  assertTrue(content.contains("name: \"my-library\""));
  assertTrue(content.contains("licenses: [\"Apache-2.0\"]"));
  assertTrue(content.contains("version: \"0.1.0\""));
  assertTrue(content.contains("target(name: \"build\""));
  assertTrue(content.contains("target(name: \"test\""));
  assertTrue(content.contains("target(name: \"clean\""));
  assertTrue(content.contains("dependsOn: [\"build\"]"));
  assertFalse(content.contains("${"));
}

@Test
public void initWithMIT() throws IOException {
  Scanner scanner = new Scanner("com.acme\nwidget\nMIT\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, testDir);

  String content = Files.readString(testDir.resolve("project.latte"));
  assertTrue(content.contains("group: \"com.acme\""));
  assertTrue(content.contains("name: \"widget\""));
  assertTrue(content.contains("licenses: [\"MIT\"]"));
}

@Test
public void initWithExistingDirectories() throws IOException {
  Files.createDirectories(testDir.resolve("src/main/java"));
  Files.createDirectories(testDir.resolve("src/test/java"));

  Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, testDir);

  assertTrue(Files.isRegularFile(testDir.resolve("project.latte")));
}

@Test
public void initWithInvalidInputThenValid() throws IOException {
  Scanner scanner = new Scanner("123bad\norg.example\n123bad\nmy-lib\nNOTALICENSE\nMIT\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, testDir);

  String content = Files.readString(testDir.resolve("project.latte"));
  assertTrue(content.contains("group: \"org.example\""));
  assertTrue(content.contains("name: \"my-lib\""));
  assertTrue(content.contains("licenses: [\"MIT\"]"));
}

@Test
public void initAlreadyExists() throws IOException {
  Path projectFile = testDir.resolve("project.latte");
  Files.writeString(projectFile, "existing content");

  Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
  InitCommand command = new InitCommand(scanner);

  try {
    command.run(configWithTemplate(), output, testDir);
    fail("Should have thrown RuntimeFailureException");
  } catch (RuntimeFailureException e) {
    assertTrue(e.getMessage().contains("[project.latte]"));
    assertTrue(e.getMessage().contains("already exists"));
  }

  assertEquals(Files.readString(projectFile), "existing content");
}

@Test
public void initWithDefaults() throws IOException {
  Path namedDir = testDir.resolve("my-cool-project");
  Files.createDirectories(namedDir);

  Scanner scanner = new Scanner("org.example\n\n\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, namedDir);

  String content = Files.readString(namedDir.resolve("project.latte"));
  assertTrue(content.contains("group: \"org.example\""));
  assertTrue(content.contains("name: \"my-cool-project\""));
  assertTrue(content.contains("licenses: [\"MIT\"]"));
}

@Test
public void initOverrideDefaults() throws IOException {
  Path namedDir = testDir.resolve("my-cool-project");
  Files.createDirectories(namedDir);

  Scanner scanner = new Scanner("org.example\ncustom-name\nApache-2.0\n");
  InitCommand command = new InitCommand(scanner);
  command.run(configWithTemplate(), output, namedDir);

  String content = Files.readString(namedDir.resolve("project.latte"));
  assertTrue(content.contains("name: \"custom-name\""));
  assertTrue(content.contains("licenses: [\"Apache-2.0\"]"));
}

@Test
public void initWithCustomTemplate() throws IOException {
  Path customTemplate = Files.createTempDirectory("latte-custom");
  try {
    Files.writeString(customTemplate.resolve("project.latte"), "custom: ${group} ${name} ${license}");
    Path nested = customTemplate.resolve("src/main/java/${packagePath}");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("Placeholder.java"), "package ${package};");

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add(customTemplate.toString());

    Scanner scanner = new Scanner("org.test\nmy-project\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(config, output, testDir);

    assertEquals(Files.readString(testDir.resolve("project.latte")), "custom: org.test my-project MIT");
    Path placeholder = testDir.resolve("src/main/java/org/test/my_project/Placeholder.java");
    assertTrue(Files.isRegularFile(placeholder));
    assertEquals(Files.readString(placeholder), "package org.test.my_project;");
  } finally {
    deleteRecursively(customTemplate);
  }
}

@Test
public void initWithMissingTemplate() {
  RuntimeConfiguration config = new RuntimeConfiguration();
  config.args.add("/nonexistent/template-dir");

  Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
  InitCommand command = new InitCommand(scanner);

  try {
    command.run(config, output, testDir);
    fail("Should have thrown RuntimeFailureException");
  } catch (RuntimeFailureException e) {
    assertTrue(e.getMessage().contains("[/nonexistent/template-dir]"));
    assertTrue(e.getMessage().contains("not found"));
  }
}
```

Note the ordering change: `initAlreadyExists` now reads the error via the preflight path (`"[project.latte]"` + `"already exists"`), matching the new spec. `initWithExistingDirectories` no longer asserts that `src/*/resources` were created because the test template does not include those directories — that behavior lives in the real library template now and is verified by the end-to-end test in Task 7.

- [ ] **Step 5: Run the full test class to confirm green**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS (helpers from Tasks 2–5 plus the migrated integration tests above).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "refactor: swap InitCommand to directory templates, drop --template switch"
```

---

## Task 7: Create the `library` template

**Files:**
- Create: `src/main/templates/library/src/main/java/module-info.java`
- Create: `src/main/templates/library/src/main/java/${packagePath}/Placeholder.java`
- Create: `src/main/templates/library/src/main/resources/.gitkeep`
- Create: `src/main/templates/library/src/test/java/module-info.java`
- Create: `src/main/templates/library/src/test/java/${packagePath}/PlaceholderTest.java`
- Create: `src/main/templates/library/src/test/resources/.gitkeep`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java` (add end-to-end test)

The `project.latte` under `library/` was placed in Task 1 — leave it alone.

- [ ] **Step 1: Create `src/main/templates/library/src/main/java/module-info.java`**

Contents:

```java
module ${package} {
}
```

- [ ] **Step 2: Create `src/main/templates/library/src/main/java/${packagePath}/Placeholder.java`**

The directory is literally named `${packagePath}` on disk. Create the nested path with `mkdir -p` (quote the segment so the shell leaves `${packagePath}` intact):

```bash
mkdir -p 'src/main/templates/library/src/main/java/${packagePath}'
```

File contents:

```java
package ${package};

public class Placeholder {
}
```

- [ ] **Step 3: Create `src/main/templates/library/src/main/resources/.gitkeep`**

Empty file.

```bash
mkdir -p src/main/templates/library/src/main/resources
: > src/main/templates/library/src/main/resources/.gitkeep
```

- [ ] **Step 4: Create `src/main/templates/library/src/test/java/module-info.java`**

Contents:

```java
module ${package}.tests {
  requires ${package};
  requires org.testng;
  opens ${package} to org.testng;
}
```

The `requires` clauses are alphabetized in the template's source text per `.claude/rules/code-conventions.md`. `$` (0x24) sorts before `o` (0x6F), so `requires ${package};` comes first. After substitution this also happens to match the common case where typical group prefixes (`com.`, `org.`) sort before `org.testng` — but the template's stable order is what matters here.

- [ ] **Step 5: Create `src/main/templates/library/src/test/java/${packagePath}/PlaceholderTest.java`**

```bash
mkdir -p 'src/main/templates/library/src/test/java/${packagePath}'
```

File contents:

```java
package ${package};

import org.testng.annotations.Test;

public class PlaceholderTest {
  @Test
  public void placeholder() {
  }
}
```

- [ ] **Step 6: Create `src/main/templates/library/src/test/resources/.gitkeep`**

```bash
mkdir -p src/main/templates/library/src/test/resources
: > src/main/templates/library/src/test/resources/.gitkeep
```

- [ ] **Step 7: Add an end-to-end test exercising the real library template**

Append this test to `InitCommandTest`. It points `latte.home` at the repo's build-time `src/main` so the test runs against the real template directory.

```java
@Test
public void initLibraryTemplateEndToEnd() throws IOException {
  String originalHome = System.getProperty("latte.home");
  System.setProperty("latte.home", "src/main");
  try {
    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(new RuntimeConfiguration(), output, testDir);

    // project.latte
    assertTrue(Files.isRegularFile(testDir.resolve("project.latte")));

    // Main module-info
    Path mainModuleInfo = testDir.resolve("src/main/java/module-info.java");
    assertTrue(Files.isRegularFile(mainModuleInfo));
    assertEquals(Files.readString(mainModuleInfo).trim(), "module org.example.my_lib {\n}".trim());

    // Placeholder in the derived package
    Path placeholder = testDir.resolve("src/main/java/org/example/my_lib/Placeholder.java");
    assertTrue(Files.isRegularFile(placeholder));
    assertTrue(Files.readString(placeholder).contains("package org.example.my_lib;"));

    // Test module-info
    Path testModuleInfo = testDir.resolve("src/test/java/module-info.java");
    assertTrue(Files.isRegularFile(testModuleInfo));
    String testModuleContent = Files.readString(testModuleInfo);
    assertTrue(testModuleContent.contains("module org.example.my_lib.tests"));
    assertTrue(testModuleContent.contains("requires org.example.my_lib;"));
    assertTrue(testModuleContent.contains("opens org.example.my_lib to org.testng;"));

    // PlaceholderTest
    Path placeholderTest = testDir.resolve("src/test/java/org/example/my_lib/PlaceholderTest.java");
    assertTrue(Files.isRegularFile(placeholderTest));
    assertTrue(Files.readString(placeholderTest).contains("package org.example.my_lib;"));

    // Resource gitkeeps
    assertTrue(Files.isRegularFile(testDir.resolve("src/main/resources/.gitkeep")));
    assertTrue(Files.isRegularFile(testDir.resolve("src/test/resources/.gitkeep")));
  } finally {
    if (originalHome == null) {
      System.clearProperty("latte.home");
    } else {
      System.setProperty("latte.home", originalHome);
    }
  }
}
```

- [ ] **Step 8: Run the full test class**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/templates/library/ src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: library template — module-info + Placeholder class/test in derived package"
```

---

## Task 8: Create the `web` template

**Files:**
- Create: `src/main/templates/web/project.latte`
- Create: `src/main/templates/web/src/main/java/Main.java`
- Create: `src/main/templates/web/src/test/java/MainTest.java`
- Create: `src/main/templates/web/web/static/.gitkeep`
- Modify: `src/test/java/org/lattejava/cli/command/InitCommandTest.java` (add end-to-end test)

- [ ] **Step 1: Create `src/main/templates/web/project.latte`**

```groovy
project(group: "${group}", name: "${name}", version: "0.1.0", licenses: ["${license}"]) {
  workflow {
    standard()
  }

  dependencies {
    group(name: "compile") {
      dependency(id: "org.lattejava:web:0.1.0")
    }
    group(name: "test-compile", export: false) {
      dependency(id: "org.testng:testng:7.10.2")
    }
  }

  publications {
    standard()
  }
}

// Plugins
dependency = loadPlugin(id: "org.lattejava.plugin:dependency:0.1.0")
java = loadPlugin(id: "org.lattejava.plugin:java:0.1.0")
javaTestNG = loadPlugin(id: "org.lattejava.plugin:java-testng:0.1.0")
release = loadPlugin(id: "org.lattejava.plugin:release-git:0.1.0")

// Plugin settings
java.settings.javaVersion = "25"
javaTestNG.settings.javaVersion = "25"

target(name: "clean", description: "Cleans the project") {
  java.clean()
}

target(name: "build", description: "Compiles and JARs the project") {
  java.compile()
  java.jar()
}

target(name: "test", description: "Runs the project's tests", dependsOn: ["build"]) {
  javaTestNG.test()
}

target(name: "run", description: "Runs the web server") {
  java.run(main: "src/main/java/Main.java")
}

target(name: "int", description: "Releases a local integration build of the project", dependsOn: ["test"]) {
  dependency.integrate()
}

target(name: "release", description: "Releases a full version of the project", dependsOn: ["clean", "test"]) {
  release.release()
}
```

- [ ] **Step 2: Create `src/main/templates/web/src/main/java/Main.java`**

```java
import module org.lattejava.web;

void main() {
  new Web()
      .get("/", (req, res) -> res.setBody("Hello, world!"))
      .start(8080);
}
```

- [ ] **Step 3: Create `src/main/templates/web/src/test/java/MainTest.java`**

```java
import module org.testng;

public class MainTest {
  @Test
  public void placeholder() {
  }
}
```

- [ ] **Step 4: Create `src/main/templates/web/web/static/.gitkeep`**

```bash
mkdir -p src/main/templates/web/web/static
: > src/main/templates/web/web/static/.gitkeep
```

- [ ] **Step 5: Add an end-to-end test for the web template**

Append to `InitCommandTest`:

```java
@Test
public void initWebTemplateEndToEnd() throws IOException {
  String originalHome = System.getProperty("latte.home");
  System.setProperty("latte.home", "src/main");
  try {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("web");

    Scanner scanner = new Scanner("org.example\nmy-web\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(config, output, testDir);

    String projectLatte = Files.readString(testDir.resolve("project.latte"));
    assertTrue(projectLatte.contains("group: \"org.example\""));
    assertTrue(projectLatte.contains("name: \"my-web\""));
    assertTrue(projectLatte.contains("org.lattejava:web:0.1.0"));
    assertTrue(projectLatte.contains("java.run(main: \"src/main/java/Main.java\")"));

    Path main = testDir.resolve("src/main/java/Main.java");
    assertTrue(Files.isRegularFile(main));
    assertTrue(Files.readString(main).contains("import module org.lattejava.web;"));

    Path mainTest = testDir.resolve("src/test/java/MainTest.java");
    assertTrue(Files.isRegularFile(mainTest));

    assertTrue(Files.isRegularFile(testDir.resolve("web/static/.gitkeep")));
  } finally {
    if (originalHome == null) {
      System.clearProperty("latte.home");
    } else {
      System.setProperty("latte.home", originalHome);
    }
  }
}
```

- [ ] **Step 6: Run the full test class**

```bash
latte test --test=InitCommandTest
```

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/templates/web/ src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat: web template — project.latte + classless Main.java + run target"
```

---

## Task 9: Manual verification of the bundle + end-to-end flows

This is a manual step. Must be done by a human on a machine with Latte installed and `org.lattejava:web:0.1.0` resolvable.

**Files:** None modified; this confirms the previous tasks ship correctly.

- [ ] **Step 1: Full build and integration publish**

```bash
latte clean int
```

Expected: build succeeds, tests pass. If the build machinery rebuilds the bundle as part of `int`, inspect the output; otherwise continue.

- [ ] **Step 2: Build the bundle and inspect it**

```bash
latte clean bundle
ls build/bundle/templates/
ls build/bundle/templates/library/
ls build/bundle/templates/web/
find build/bundle/templates -type f | sort
```

Expected: bundle contains both `library/` and `web/` subtrees with every file (including `.gitkeep` sentinels and the literal `${packagePath}` directories).

- [ ] **Step 3: Scaffold a fresh library project using the built bundle**

```bash
mkdir -p /tmp/latte-library-smoke && cd /tmp/latte-library-smoke
# Use the freshly built bundle as latte.home. Adjust the -Dlatte.home path as needed.
java -Dlatte.home=<repo>/cli/build/bundle -jar <repo>/cli/build/jars/*.jar init
# Answer the prompts: group=org.example, name=my-lib, license=MIT
latte test
```

Expected: `latte test` passes with the placeholder test running. Verify the on-disk layout:

```bash
find . -type f
```

Expected files: `project.latte`, `src/main/java/module-info.java`, `src/main/java/org/example/my_lib/Placeholder.java`, `src/main/resources/.gitkeep`, `src/test/java/module-info.java`, `src/test/java/org/example/my_lib/PlaceholderTest.java`, `src/test/resources/.gitkeep`.

- [ ] **Step 4: Scaffold a fresh web project**

```bash
mkdir -p /tmp/latte-web-smoke && cd /tmp/latte-web-smoke
java -Dlatte.home=<repo>/cli/build/bundle -jar <repo>/cli/build/jars/*.jar init web
# Answer the prompts: group=org.example, name=my-web, license=MIT
latte run &
SERVER_PID=$!
sleep 2
curl -s http://localhost:8080/
kill $SERVER_PID
```

Expected: `curl` returns `Hello, world!`.

If `import module org.lattejava.web` fails under source-file execution (error from the Java launcher complaining about the module), stop. This is the risk flagged in the spec. Options in order of preference:

1. Pass an explicit `--module-path` via `java.run` `jvmArguments` — if this is a classpath/module-path resolution issue only, it may just need an extra flag.
2. Convert the web template to a compiled main class (add a `module-info.java`, use a templated package, and change `java.run(main: "…Main.java")` to `java.run(main: "<package>.Main")`).

Document the outcome in the PR description.

- [ ] **Step 5: Commit any manual-verification fixes**

If Step 4 uncovered an issue that required a template change, commit the fix and rerun steps 2–4 until green.

---

## Notes for the executor

- **Keep tests in front of changes.** Tasks 2–5 are pure TDD. If you catch yourself implementing before the failing test runs, stop and back up.
- **Don't skip the preflight test in Task 5.** The no-partial-writes guarantee is the whole point of the ordering change.
- **Respect `.claude/rules/code-conventions.md` and `.claude/rules/error-messages.md`.** Alphabetize imports and requires clauses. Wrap runtime values in exception messages in `[brackets]` (examples throughout the plan do this already).
- **Do not add license headers to the template files** (they are not Java source files in this repo, and the scaffolded projects are the user's code — we do not dictate their license).
- **Don't try to be clever with Task 9.** If the `import module` issue surfaces, document it, involve the spec author, and pick the simplest fix.
