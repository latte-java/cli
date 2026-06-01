# Init Version Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After `latte init` scaffolds a project, automatically upgrade the plugin and dependency versions in the generated `project.latte` to the latest available, by delegating to a `UpgradeCommand` that is reworked to edit the file purely with the Groovy lexer.

**Architecture:** Rework `UpgradeCommand` so `upgradeDependencies()` and `upgradeDependency()` edit `project.latte` via the lexer (`GroovySourceTools.findMethodCallStringArguments`) exactly like the existing `upgradePlugins()` — no parsed `Project`, no block regeneration, so comments/whitespace/exclusions are preserved and no plugins are loaded. Add a `--no-warnings` switch that silences "not found, skipping" lines. `InitCommand` then builds an unparsed `Project`, retrieves `UpgradeCommand` from `DefaultRunner.COMMANDS`, and runs `plugins` then `dependencies` with `--no-warnings`, unless `--no-upgrade` is passed.

**Tech Stack:** Java 25, TestNG, the project's `GroovySourceTools` ANTLR4 lexer helper, `RepositoryTools.queryLatestVersion` (Latte search API).

---

## Background facts (verified)

- `GroovySourceTools.findMethodCallStringArguments(source, name)` returns `List<StringLiteral>`, each `StringLiteral` is a record `(int start, int end, String value)` where `start`/`end` are absolute offsets covering the quoted literal (including the quotes) and `value` is the unquoted text. It matches an identifier `name` immediately followed by `(`, so `dependency(id: "...")` matches but `dependency.integrate()` and `dependency = loadPlugin(...)` do not. (`GroovySourceTools.java:101,114-149`)
- `RepositoryTools.queryLatestVersion(artifactId)` returns the latest version string, or `null` for not-found, non-200, or any exception (offline). A nonexistent artifact therefore always yields `null` regardless of network state. (`RepositoryTools.java:32-53`)
- `ProjectFileTools.readProjectFile` / `writeProjectFile` are simple read/write helpers and stay. `ProjectFileTools.writeDependencies` (block regeneration) is **also used by `InstallCommand.java:101`**, so it must NOT be removed; `UpgradeCommand` just stops calling it.
- `RuntimeConfiguration` has `public Switches switches` and `public List<String> args`. A bare `--foo` flag becomes a boolean switch reachable via `configuration.switches.has("foo")`. `Switches.add(String)` adds a boolean switch.
- `Project` has constructor `Project(Path directory, Output output)`; `project.directory` is the field used by the upgrade methods.
- `DefaultRunner.COMMANDS` is a `public static final Map<String, Command>` containing `"upgrade" -> new UpgradeCommand()`.
- Test base class `BaseUnitTest` exposes `public static Output output = new SystemOutOutput(false)` (writes to stdout, not captured). `Output` is a 13-method interface.

## File Structure

- **Modify** `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`
  - Add `boolean noWarnings` reading + threading in `run()`.
  - Add private static helper `upgradeArtifactCalls(content, methodName, label, noWarnings, output)`.
  - Rewrite `upgradePlugins`, `upgradeDependencies`, `upgradeDependency` to be lexer-based.
  - Leave `upgradeRuntime`, `printHelp` unchanged; update `printHelp` to document `--no-warnings`.
- **Modify** `src/main/java/org/lattejava/cli/command/InitCommand.java`
  - After `copyTemplate(...)`, unless `--no-upgrade`, delegate to `UpgradeCommand` for `plugins` then `dependencies` with `--no-warnings`.
- **Modify** `src/test/java/org/lattejava/cli/command/UpgradeCommandTest.java`
  - Add a capturing `Output` test double and two `--no-warnings` tests. Existing tests stay (they assert outcomes).
- **Modify** `src/test/java/org/lattejava/cli/command/InitCommandTest.java`
  - Add `--no-upgrade` to the two end-to-end template tests; add a `--no-upgrade` deterministic test and a live-network "upgrades by default" test.

---

### Task 1: Thread `--no-warnings` and add the shared lexer helper; refactor `upgradePlugins`

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`

This is a behavior-preserving refactor of the plugin path plus the new `--no-warnings` gating. Existing plugin tests must stay green.

- [ ] **Step 1: Update `run()` to read the switch and pass it to the upgrade methods**

Replace the body of `run()` (currently `UpgradeCommand.java:47-71`) with:

```java
  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    if (configuration.args.isEmpty()) {
      printHelp(output);
      return;
    }

    boolean noWarnings = configuration.switches.has("no-warnings");
    String subcommand = configuration.args.getFirst();
    switch (subcommand) {
      case "help" -> printHelp(output);
      case "runtime" -> upgradeRuntime(output);
      case "plugins" -> upgradePlugins(output, project, noWarnings);
      case "dependency" -> upgradeDependency(configuration, output, project);
      case "dependencies" -> upgradeDependencies(output, project, noWarnings);
      case "all" -> {
        upgradeRuntime(output);
        if (project != null) {
          upgradePlugins(output, project, noWarnings);
          upgradeDependencies(output, project, noWarnings);
        }
      }
      default ->
          throw new RuntimeFailureException("Unknown upgrade parameter [" + subcommand + "]. Run 'latte upgrade help' for usage.");
    }
  }
```

- [ ] **Step 2: Add the shared helper method**

Add this private static method to the class (place it near the other private helpers):

```java
  /**
   * Upgrades the version segment of every {@code <methodName>(id: "group:project:version")} call in the source to the
   * latest version reported by the repository, editing only the version substring so surrounding text, comments, and
   * trailing closures are preserved.
   *
   * @param content    The project.latte source.
   * @param methodName The call to upgrade (e.g. "loadPlugin" or "dependency").
   * @param label      A human-readable noun for log messages (e.g. "Plugin" or "Dependency").
   * @param noWarnings When true, suppresses the "not found, skipping" lines.
   * @param output     The output for progress messages.
   * @return The (possibly) modified source.
   */
  private static String upgradeArtifactCalls(String content, String methodName, String label, boolean noWarnings, Output output) {
    List<GroovySourceTools.StringLiteral> literals = GroovySourceTools.findMethodCallStringArguments(content, methodName);

    // Process in reverse order so character offsets remain valid after each replacement.
    for (int i = literals.size() - 1; i >= 0; i--) {
      GroovySourceTools.StringLiteral literal = literals.get(i);
      String value = literal.value();
      int lastColon = value.lastIndexOf(':');
      if (lastColon == -1) {
        continue;
      }

      String artifactId = value.substring(0, lastColon);
      String currentVersion = value.substring(lastColon + 1);
      String latestVersion = RepositoryTools.queryLatestVersion(artifactId);

      if (latestVersion != null && !latestVersion.equals(currentVersion)) {
        output.infoln("Upgrading %s [%s] from %s to %s", label, artifactId, currentVersion, latestVersion);
        String newValue = "\"" + artifactId + ":" + latestVersion + "\"";
        content = content.substring(0, literal.start()) + newValue + content.substring(literal.end());
      } else if (latestVersion == null) {
        if (!noWarnings) {
          output.infoln("%s [%s:%s] not found in repository, skipping", label, artifactId, currentVersion);
        }
      } else {
        output.infoln("%s [%s] already at latest version %s", label, artifactId, currentVersion);
      }
    }

    return content;
  }
```

- [ ] **Step 3: Rewrite `upgradePlugins` to use the helper**

Replace `upgradePlugins` (currently `UpgradeCommand.java:184-223`) with:

```java
  private void upgradePlugins(Output output, Project project, boolean noWarnings) {
    if (project == null) {
      throw new RuntimeFailureException("The 'plugins' upgrade requires a project.latte file.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    String content = ProjectFileTools.readProjectFile(projectFile);
    String updated = upgradeArtifactCalls(content, "loadPlugin", "Plugin", noWarnings, output);
    if (!updated.equals(content)) {
      ProjectFileTools.writeProjectFile(projectFile, updated);
    }
  }
```

- [ ] **Step 4: Run the existing plugin tests — expect PASS (refactor preserves behavior)**

Run: `latte test --test=UpgradeCommandTest`
Expected: PASS. `upgradePluginsNoProject`, `upgradePluginsNoPlugins`, `upgradePluginsUpgradesAll` (live-network), and `upgradePluginsSkipsUnknown` all still pass. The dependency tests still pass too because `upgradeDependencies`/`upgradeDependency` are untouched in this task.

Note: if the build complains about a now-unused import after later tasks, remove it then; in this task nothing becomes unused yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java
git commit -m "refactor: extract lexer-based artifact-call upgrader; add --no-warnings switch"
```

---

### Task 2: Rework `upgradeDependencies` to the lexer + add `--no-warnings` tests

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`
- Test: `src/test/java/org/lattejava/cli/command/UpgradeCommandTest.java`

- [ ] **Step 1: Add a capturing Output test double + the two failing `--no-warnings` tests**

Add this nested class at the bottom of `UpgradeCommandTest` (before the closing brace), and the two tests in the dependencies test section:

```java
  /** Captures info-level messages so tests can assert on (or on the absence of) log lines. */
  private static class CapturingOutput implements org.lattejava.output.Output {
    final java.util.List<String> infos = new java.util.ArrayList<>();

    private String fmt(String message, Object... values) {
      return values.length == 0 ? message : String.format(message, values);
    }

    public org.lattejava.output.Output info(String message, Object... values) { infos.add(fmt(message, values)); return this; }
    public org.lattejava.output.Output infoln(String message, Object... values) { infos.add(fmt(message, values)); return this; }
    public org.lattejava.output.Output infoln(int color, String message, Object... values) { infos.add(fmt(message, values)); return this; }
    public org.lattejava.output.Output debug(String message, Object... values) { return this; }
    public org.lattejava.output.Output debugln(String message, Object... values) { return this; }
    public org.lattejava.output.Output debug(Throwable t) { return this; }
    public org.lattejava.output.Output disableDebug() { return this; }
    public org.lattejava.output.Output enableDebug() { return this; }
    public org.lattejava.output.Output error(String message, Object... values) { return this; }
    public org.lattejava.output.Output errorln(String message, Object... values) { return this; }
    public org.lattejava.output.Output warning(String message, Object... values) { return this; }
    public org.lattejava.output.Output warningln(String message, Object... values) { return this; }
  }
```

```java
  @Test
  public void upgradeDependenciesNoWarningsSuppressesNotFound() throws IOException {
    // A nonexistent artifact always resolves to null (not-found), regardless of network state.
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          dependencies {
            group(name: "compile") {
              dependency(id: "org.nonexistent:fake-lib:1.0.0")
            }
          }
        }
        """;
    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");
    config.switches.add("no-warnings");

    CapturingOutput captured = new CapturingOutput();
    new UpgradeCommand().run(config, captured, project);

    assertTrue(captured.infos.stream().noneMatch(m -> m.contains("not found")),
        "Expected no 'not found' line with --no-warnings, got: " + captured.infos);
    assertEquals(Files.readString(projectFile), projectContent);
  }

  @Test
  public void upgradeDependenciesWarnsForNotFoundByDefault() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          dependencies {
            group(name: "compile") {
              dependency(id: "org.nonexistent:fake-lib:1.0.0")
            }
          }
        }
        """;
    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");

    CapturingOutput captured = new CapturingOutput();
    new UpgradeCommand().run(config, captured, project);

    assertTrue(captured.infos.stream().anyMatch(m -> m.contains("not found")),
        "Expected a 'not found' line without --no-warnings, got: " + captured.infos);
  }
```

- [ ] **Step 2: Run the new tests — expect FAIL**

Run: `latte test --test=UpgradeCommandTest`
Expected: FAIL. `upgradeDependenciesNoWarnings*` fail because `upgradeDependencies` still uses the parsed-`Project` path (it ignores the switch and uses different messages / requires `project.dependencies`). This proves the tests exercise the new behavior.

- [ ] **Step 3: Rewrite `upgradeDependencies` to use the helper**

Replace `upgradeDependencies` (currently `UpgradeCommand.java:88-120`) with:

```java
  private void upgradeDependencies(Output output, Project project, boolean noWarnings) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependencies' upgrade requires a project.latte file.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    String content = ProjectFileTools.readProjectFile(projectFile);
    String updated = upgradeArtifactCalls(content, "dependency", "Dependency", noWarnings, output);
    if (!updated.equals(content)) {
      ProjectFileTools.writeProjectFile(projectFile, updated);
    }
  }
```

- [ ] **Step 4: Run the tests — expect PASS**

Run: `latte test --test=UpgradeCommandTest`
Expected: PASS. The new `--no-warnings` tests pass; `upgradeDependenciesNoProject` (null → "requires a project.latte"), `upgradeDependenciesNoDeps` (no `dependency(...)` literals → file unchanged), `upgradeDependenciesUpgradesAll` and `upgradeDependenciesSkipsUnknown` (live-network) still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java src/test/java/org/lattejava/cli/command/UpgradeCommandTest.java
git commit -m "refactor: upgradeDependencies edits project.latte via the lexer"
```

---

### Task 3: Rework `upgradeDependency` (single) to the lexer

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`

The existing single-dependency tests (`upgradeDependencyWithVersion`, `upgradeDependencyInTestGroup`, `upgradeDependencyNotFound`, `upgradeDependencyNoProject`, `upgradeDependencyMissingArgs`, `upgradeDependencyInvalidId`, `upgradeDependencyNoDependenciesInProject`, `upgradeDependencyPreservesOtherDependencies`, `upgradeDependencyPreservesSkipCompatibilityCheck`, `upgradeDependencyPreservesExclusions`, `upgradeDependencyPreservesNonSemanticVersion`) must stay green. They assert file content + reparse; the lexer rewrite satisfies them and the preservation tests trivially (only the matched version substring changes).

- [ ] **Step 1: Rewrite `upgradeDependency`**

Replace `upgradeDependency` (currently `UpgradeCommand.java:122-182`) with:

```java
  private void upgradeDependency(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependency' upgrade requires a project.latte file.");
    }
    if (configuration.args.size() < 2) {
      throw new RuntimeFailureException("Usage: latte upgrade dependency <artifact-id> [version]");
    }

    ArtifactID id;
    try {
      id = new ArtifactID(configuration.args.get(1));
    } catch (Exception e) {
      throw new RuntimeFailureException("Invalid dependency [" + configuration.args.get(1) + "]. Expected format: group:name");
    }
    String artifactId = id.group + ":" + id.project;

    Path projectFile = project.directory.resolve("project.latte");
    String content = ProjectFileTools.readProjectFile(projectFile);

    List<GroovySourceTools.StringLiteral> literals = GroovySourceTools.findMethodCallStringArguments(content, "dependency");
    if (literals.isEmpty()) {
      throw new RuntimeFailureException("No dependencies found in project.latte.");
    }

    GroovySourceTools.StringLiteral match = null;
    String currentVersion = null;
    for (GroovySourceTools.StringLiteral literal : literals) {
      String value = literal.value();
      int lastColon = value.lastIndexOf(':');
      if (lastColon == -1) {
        continue;
      }
      if (value.substring(0, lastColon).equals(artifactId)) {
        match = literal;
        currentVersion = value.substring(lastColon + 1);
        break;
      }
    }

    if (match == null) {
      throw new RuntimeFailureException("Dependency [" + artifactId + "] not found in any dependency group.");
    }

    String version;
    if (configuration.args.size() > 2) {
      version = configuration.args.get(2);
    } else {
      output.infoln("Resolving latest version for [%s]...", artifactId);
      version = RepositoryTools.queryLatestVersion(artifactId);
      if (version == null) {
        throw new RuntimeFailureException("Could not find artifact [" + artifactId + "] in the repository.");
      }
      output.infoln("Resolved to version [%s]", version);
    }

    String newValue = "\"" + artifactId + ":" + version + "\"";
    String updated = content.substring(0, match.start()) + newValue + content.substring(match.end());
    output.infoln("Upgrading [%s] from %s to %s", artifactId, currentVersion, version);
    ProjectFileTools.writeProjectFile(projectFile, updated);
  }
```

- [ ] **Step 2: Remove now-unused imports**

`upgradeDependencies`/`upgradeDependency` no longer use the parsed dependency model. Remove these imports if present and unused (`UpgradeCommand.java:28,30`):

```java
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.DependencyGroup;
```

Keep `import org.lattejava.dep.domain.ArtifactID;` (still used) and `import org.lattejava.cli.parser.groovy.ProjectFileTools;` (read/write still used).

- [ ] **Step 3: Run the tests — expect PASS**

Run: `latte test --test=UpgradeCommandTest`
Expected: PASS for all single-dependency tests, including the three preservation tests.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java
git commit -m "refactor: upgradeDependency edits the matching dependency via the lexer"
```

---

### Task 4: Document `--no-warnings` in `printHelp`

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`

- [ ] **Step 1: Add a switches line to `printHelp`**

In `printHelp` (currently `UpgradeCommand.java:73-86`), add a switches note after the `help` parameter line, before the final blank `infoln("")`:

```java
    output.infoln("   help             Displays this help message");
    output.infoln("");
    output.infoln("Switches:");
    output.infoln("");
    output.infoln("   --no-warnings    Suppresses 'not found, skipping' messages for artifacts not in the Latte repository");
    output.infoln("");
```

- [ ] **Step 2: Run the dispatch/help tests — expect PASS**

Run: `latte test --test=UpgradeCommandTest`
Expected: PASS (`noArgsShowsHelp`, `helpSubcommand` assert the project file is unchanged, which still holds).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java
git commit -m "docs: document --no-warnings in upgrade help"
```

---

### Task 5: Wire `InitCommand` to upgrade after scaffolding

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/InitCommand.java`
- Test: `src/test/java/org/lattejava/cli/command/InitCommandTest.java`

- [ ] **Step 1: Make the two end-to-end scaffolding tests hermetic, and add the new init tests**

In `InitCommandTest`, add `config.switches.add("no-upgrade")` to the two real-template tests so they don't hit the network:

In `initLibraryTemplateEndToEnd` (currently builds `command.run(new RuntimeConfiguration(), output, ...)` at ~line 212), change it to:

```java
      RuntimeConfiguration config = new RuntimeConfiguration();
      config.switches.add("no-upgrade");
      Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
      InitCommand command = new InitCommand(scanner);
      command.run(config, output, new Project(testDir, output));
```

In `initWebTemplateEndToEnd` (currently `config.args.add("web")` at ~line 272), add right after that line:

```java
      config.switches.add("no-upgrade");
```

Then add these two tests:

```java
  @Test
  public void initNoUpgradeLeavesTemplateVersions() throws IOException {
    // Template with a deliberately old plugin version; --no-upgrade must leave it untouched.
    Files.writeString(templateDir.resolve("project.latte"), """
        project(group: "${group}", name: "${name}", version: "0.1.0", licenses: ["${license}"]) {
        }
        java = loadPlugin(id: "org.lattejava.plugin:java:0.0.1")
        """);

    RuntimeConfiguration config = configWithTemplate();
    config.switches.add("no-upgrade");
    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    new InitCommand(scanner).run(config, output, new Project(testDir, output));

    String content = Files.readString(testDir.resolve("project.latte"));
    assertTrue(content.contains("org.lattejava.plugin:java:0.0.1"),
        "--no-upgrade must leave the template plugin version untouched, got: " + content);
  }

  @Test
  public void initUpgradesPluginsByDefault() throws IOException {
    // Live-network integration test (consistent with UpgradeCommandTest.upgradePluginsUpgradesAll):
    // a real plugin pinned to an old version should be bumped past it after init.
    Files.writeString(templateDir.resolve("project.latte"), """
        project(group: "${group}", name: "${name}", version: "0.1.0", licenses: ["${license}"]) {
        }
        java = loadPlugin(id: "org.lattejava.plugin:java:0.1.0")
        """);

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    new InitCommand(scanner).run(configWithTemplate(), output, new Project(testDir, output));

    String content = Files.readString(testDir.resolve("project.latte"));
    assertFalse(content.contains("org.lattejava.plugin:java:0.1.0"),
        "init should have upgraded the java plugin past 0.1.0, got: " + content);
  }
```

- [ ] **Step 2: Run init tests — expect FAIL on the two new tests**

Run: `latte test --test=InitCommandTest`
Expected: `initUpgradesPluginsByDefault` FAILS — init performs no upgrade yet, so `:0.1.0` is still present. (`initNoUpgradeLeavesTemplateVersions` happens to pass already, since with no upgrade wiring the version is trivially left untouched; it becomes meaningful as a guard once Step 3 adds the upgrade, ensuring `--no-upgrade` still suppresses it.) `initUpgradesPluginsByDefault` is the driving failing test.

- [ ] **Step 3: Add the upgrade delegation to `InitCommand.run`**

In `InitCommand.run` (currently `InitCommand.java:90-105`), insert the upgrade call between `copyTemplate(...)` and the final `output.infoln("Created project ...")`:

```java
    copyTemplate(templateDir, projectDir, vars);

    if (!configuration.switches.has("no-upgrade")) {
      upgradeVersions(output, projectDir);
    }

    output.infoln("Created project [%s:%s] from template [%s]", group, name, templateDir.getFileName());
```

- [ ] **Step 4: Add the `upgradeVersions` helper to `InitCommand`**

Add these methods to `InitCommand` (private instance method + a small static config builder):

```java
  private void upgradeVersions(Output output, Path projectDir) {
    Path projectFile = projectDir.resolve("project.latte");
    if (!Files.isRegularFile(projectFile)) {
      return;
    }

    Project project = new Project(projectDir, output);
    Command upgrade = DefaultRunner.COMMANDS.get("upgrade");
    upgrade.run(upgradeConfiguration("plugins"), output, project);
    upgrade.run(upgradeConfiguration("dependencies"), output, project);
  }

  private static RuntimeConfiguration upgradeConfiguration(String subcommand) {
    RuntimeConfiguration configuration = new RuntimeConfiguration();
    configuration.args.add(subcommand);
    configuration.switches.add("no-warnings");
    return configuration;
  }
```

`Command`, `Project`, `DefaultRunner`, and `RuntimeConfiguration` are all reachable from `InitCommand`'s existing imports (`org.lattejava.cli.command` is its own package; `import org.lattejava.cli.domain.*` and `import org.lattejava.cli.runtime.*` cover the rest). If the compiler reports `DefaultRunner` unresolved, add `import org.lattejava.cli.runtime.DefaultRunner;` (it is covered by the existing `org.lattejava.cli.runtime.*` wildcard, so normally no new import is needed).

- [ ] **Step 5: Run init tests — expect PASS**

Run: `latte test --test=InitCommandTest`
Expected: PASS. `initNoUpgradeLeavesTemplateVersions` passes (switch honored), `initUpgradesPluginsByDefault` passes (live-network bump), and all existing tests pass — those using `TEST_PROJECT_LATTE` or tiny custom templates have no `loadPlugin`/`dependency` literals, so the upgrade is a no-op with no network call.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/InitCommand.java src/test/java/org/lattejava/cli/command/InitCommandTest.java
git commit -m "feat(init): upgrade plugin and dependency versions after scaffolding"
```

---

### Task 6: Full suite + manual verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `latte test`
Expected: PASS (no regressions). The live-network upgrade tests require connectivity to `api.lattejava.org`.

- [ ] **Step 2: Manual smoke test (optional, requires network)**

```bash
mkdir -p /tmp/latte-init-smoke && cd /tmp/latte-init-smoke
latte init
# answer prompts; then:
cat project.latte   # plugin + dependency versions should be at latest; comments/whitespace intact
```

Run again with `latte init --no-upgrade` in a fresh dir and confirm versions match the template.

- [ ] **Step 3: Commit (if any verification-driven fixes were needed)**

```bash
git add -A
git commit -m "test: verify init version upgrade end to end"
```

---

## Self-Review

**Spec coverage:**
- "On by default; `--no-upgrade` opt-out" → Task 5 (guard + tests). ✓
- "Upgrade both plugins and dependencies" → Task 5 runs `plugins` then `dependencies`. ✓
- "Init delegates to `UpgradeCommand` retrieved from `DefaultRunner.COMMANDS`" → Task 5 `upgradeVersions`. ✓
- "`UpgradeCommand` reworked to lexer for `upgradeDependencies` and `upgradeDependency`" → Tasks 2, 3. ✓
- "`upgradePlugins` unchanged behavior / shared mechanism" → Task 1 helper. ✓
- "`upgradeRuntime` unchanged" → not modified. ✓
- "`--no-warnings` switch suppresses not-found lines" → Task 1 helper gating + Task 2 tests + Task 4 help text. ✓
- "Comments/whitespace/exclusions/skipCompatibilityCheck/non-semantic versions preserved" → lexer edits only the version substring; existing preservation tests in Task 3. ✓
- "`ProjectFileTools.writeDependencies` retained for `InstallCommand`" → not removed; only `read/writeProjectFile` used. ✓
- "Init uses an unparsed `Project`; no plugin loading" → Task 5 `new Project(projectDir, output)`. ✓
- "End-to-end scaffolding tests stay hermetic" → Task 5 adds `--no-upgrade` to both. ✓
- "Offline → null handled, init succeeds" → `queryLatestVersion` returns null, helper skips, no throw. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete code; commands have expected output. ✓

**Type consistency:** `upgradeArtifactCalls(content, methodName, label, noWarnings, output)` signature is consistent across Tasks 1–2. `upgradePlugins(output, project, noWarnings)` / `upgradeDependencies(output, project, noWarnings)` match their `run()` call sites in Task 1. `upgradeConfiguration(String)` returns `RuntimeConfiguration` used by `upgradeVersions`. `GroovySourceTools.StringLiteral` accessors `start()`/`end()`/`value()` match the record. ✓
