# Upgrade Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `upgrade` global command to the Latte CLI that can upgrade the runtime, plugins, and dependencies.

**Architecture:** A single `UpgradeCommand` class that dispatches to sub-operations based on the first argument (`runtime`, `plugins`, `dependency`, `dependencies`, `all`, `help`). Runtime upgrade uses the GitHub Releases API. Plugin and dependency upgrades use the Latte repository search API (`api.lattejava.org`). Project file modification reuses patterns from `InstallCommand` and `GroovySourceTools`.

**Tech Stack:** Java 25, `java.net.http.HttpClient`, json-simple for JSON parsing, existing `TarTools` for tarball extraction, existing `GroovySourceTools` for project file editing.

---

## File Structure

- Create: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java` — Command implementation with sub-operation dispatch
- Modify: `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java:49-52` — Register the upgrade command
- Modify: `src/main/java/org/lattejava/cli/runtime/Main.java:101-125` — Add upgrade to help text

## Key Existing Code

- `Command` interface: `src/main/java/org/lattejava/cli/command/Command.java` — `void run(RuntimeConfiguration, Output, Project)`
- `InstallCommand`: `src/main/java/org/lattejava/cli/command/InstallCommand.java` — Has `replaceDependenciesBlock()` and `generateDependenciesBlock()` for rewriting the dependencies section of project.latte
- `GroovySourceTools`: `src/main/java/org/lattejava/cli/parser/groovy/GroovySourceTools.java` — `findBlock(source, name, nestingDepth)` returns a `Block(start, end)` for locating blocks in Groovy source
- `NetTools`: `src/main/java/org/lattejava/net/NetTools.java` — Has a shared `HttpClient` instance and `downloadToPath()` for file downloads
- `TarTools`: `src/main/java/org/lattejava/io/tar/TarTools.java` — `untar(file, to, useGroup, useOwner)` handles .tar.gz extraction
- `FileTools`: `src/main/java/org/lattejava/io/FileTools.java` — `prune(path)` recursively deletes a directory
- `Main.class.getPackage().getImplementationVersion()` — Returns the current CLI version from the JAR manifest
- `System.getProperty("latte.home")` — The install directory, set by the `bin/latte` shell script
- Repository search API: `GET https://api.lattejava.org/repository/search?id=<id>&latest=true` returns `{"id":"...","versions":["0.1.2"]}`
- GitHub Releases API: `GET https://api.github.com/repos/latte-java/cli/releases/latest` returns JSON with `tag_name` and `assets[0].browser_download_url`

---

### Task 1: Create UpgradeCommand with help and registration

**Files:**
- Create: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java:49-52`
- Modify: `src/main/java/org/lattejava/cli/runtime/Main.java:106-110`

- [ ] **Step 1: Create the UpgradeCommand skeleton**

Create `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`:

```java
/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.cli.command;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.output.Output;

/**
 * Upgrades the Latte runtime, plugins, or dependencies.
 *
 * @author Brian Pontarelli
 */
public class UpgradeCommand implements Command {
  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    if (configuration.args.isEmpty()) {
      printHelp(output);
      return;
    }

    String subcommand = configuration.args.getFirst();
    switch (subcommand) {
      case "help" -> printHelp(output);
      case "runtime" -> upgradeRuntime(output);
      case "plugins" -> upgradePlugins(output, project);
      case "dependency" -> upgradeDependency(configuration, output, project);
      case "dependencies" -> upgradeDependencies(output, project);
      case "all" -> {
        upgradeRuntime(output);
        if (project != null) {
          upgradePlugins(output, project);
          upgradeDependencies(output, project);
        }
      }
      default -> throw new RuntimeFailureException("Unknown upgrade parameter [" + subcommand + "]. Run 'latte upgrade help' for usage.");
    }
  }

  private void printHelp(Output output) {
    output.infoln("Usage: latte upgrade <parameter>");
    output.infoln("");
    output.infoln("Parameters:");
    output.infoln("");
    output.infoln("   all              Upgrades the runtime, dependencies, and all plugins");
    output.infoln("   runtime          Upgrades only the Latte runtime");
    output.infoln("   plugins          Upgrades all plugins in the project file");
    output.infoln("   dependency       Upgrades a single dependency");
    output.infoln("                    Usage: latte upgrade dependency <group:name:version>");
    output.infoln("   dependencies     Upgrades all project dependencies");
    output.infoln("   help             Displays this help message");
    output.infoln("");
  }

  private void upgradeRuntime(Output output) {
    // TODO: Task 2
    throw new RuntimeFailureException("Runtime upgrade not yet implemented.");
  }

  private void upgradePlugins(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'plugins' upgrade requires a project.latte file.");
    }
    // TODO: Task 3
    throw new RuntimeFailureException("Plugin upgrade not yet implemented.");
  }

  private void upgradeDependency(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependency' upgrade requires a project.latte file.");
    }
    // TODO: Task 4
    throw new RuntimeFailureException("Dependency upgrade not yet implemented.");
  }

  private void upgradeDependencies(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependencies' upgrade requires a project.latte file.");
    }
    // TODO: Task 4
    throw new RuntimeFailureException("Dependencies upgrade not yet implemented.");
  }
}
```

- [ ] **Step 2: Register the command in DefaultRunner**

In `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java`, change the COMMANDS map from:

```java
  public static final Map<String, Command> COMMANDS = Map.of(
      "init", new InitCommand(),
      "install", new InstallCommand()
  );
```

to:

```java
  public static final Map<String, Command> COMMANDS = Map.of(
      "init", new InitCommand(),
      "install", new InstallCommand(),
      "upgrade", new UpgradeCommand()
  );
```

Add the import: `import org.lattejava.cli.command.UpgradeCommand;`

- [ ] **Step 3: Add upgrade to help text in Main**

In `src/main/java/org/lattejava/cli/runtime/Main.java`, in the `printHelp` method, after the `install` lines and before the blank line, add:

```java
    output.infoln("   upgrade         Upgrades runtime, plugins, or dependencies");
    output.infoln("                   Usage: latte upgrade <parameter>");
    output.infoln("                   Run 'latte upgrade help' for details");
```

- [ ] **Step 4: Build and verify**

Run: `latte build`
Expected: Compiles successfully.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java src/main/java/org/lattejava/cli/runtime/DefaultRunner.java src/main/java/org/lattejava/cli/runtime/Main.java
git commit -m "Add upgrade command skeleton with help and registration"
```

---

### Task 2: Implement runtime upgrade

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`

This task replaces the `upgradeRuntime` stub with a real implementation that:
1. Gets current version from `Main.class.getPackage().getImplementationVersion()`
2. Queries GitHub Releases API for latest version
3. Downloads the tarball
4. Extracts to temp dir, then replaces `latte.home` contents

- [ ] **Step 1: Add imports to UpgradeCommand.java**

Add these imports at the top of `UpgradeCommand.java`:

```java
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.lattejava.cli.runtime.Main;
import org.lattejava.io.FileTools;
import org.lattejava.io.tar.TarTools;
```

- [ ] **Step 2: Add the HttpClient field**

Add as a static field in the class:

```java
  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(10_000))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
```

- [ ] **Step 3: Replace the upgradeRuntime method**

```java
  private void upgradeRuntime(Output output) {
    String latteHome = System.getProperty("latte.home");
    if (latteHome == null || latteHome.isBlank()) {
      throw new RuntimeFailureException("The latte.home system property is not set. Is Latte installed correctly?");
    }

    String currentVersion = Main.class.getPackage().getImplementationVersion();
    if (currentVersion == null) {
      throw new RuntimeFailureException("Could not determine the current Latte version.");
    }

    output.infoln("Current version: %s", currentVersion);
    output.infoln("Checking for updates...");

    // Query GitHub Releases API
    JSONObject release;
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.github.com/repos/latte-java/cli/releases/latest"))
          .header("Accept", "application/vnd.github+json")
          .GET()
          .timeout(Duration.ofMillis(10_000))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeFailureException("GitHub API returned status " + response.statusCode());
      }
      release = (JSONObject) new JSONParser().parse(response.body());
    } catch (RuntimeFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeFailureException("Failed to check for updates: " + e.getMessage());
    }

    String latestVersion = (String) release.get("tag_name");
    if (latestVersion == null) {
      throw new RuntimeFailureException("Could not determine the latest version from GitHub.");
    }

    if (currentVersion.equals(latestVersion)) {
      output.infoln("Latte is already up to date [%s]", currentVersion);
      return;
    }

    // Find the tarball asset
    JSONArray assets = (JSONArray) release.get("assets");
    if (assets == null || assets.isEmpty()) {
      throw new RuntimeFailureException("No release assets found for version " + latestVersion);
    }

    String downloadUrl = null;
    for (Object obj : assets) {
      JSONObject asset = (JSONObject) obj;
      String name = (String) asset.get("name");
      if (name != null && name.endsWith(".tar.gz")) {
        downloadUrl = (String) asset.get("browser_download_url");
        break;
      }
    }

    if (downloadUrl == null) {
      throw new RuntimeFailureException("No tarball asset found in release " + latestVersion);
    }

    output.infoln("Downloading Latte %s...", latestVersion);

    // Download the tarball
    Path tarball;
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(downloadUrl))
          .GET()
          .timeout(Duration.ofMillis(60_000))
          .build();
      HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new RuntimeFailureException("Failed to download tarball: HTTP " + response.statusCode());
      }

      tarball = Files.createTempFile("latte-upgrade-", ".tar.gz");
      tarball.toFile().deleteOnExit();
      try (InputStream is = response.body()) {
        Files.copy(is, tarball, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (RuntimeFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeFailureException("Failed to download update: " + e.getMessage());
    }

    // Extract to temp directory
    Path tempDir;
    try {
      tempDir = Files.createTempDirectory("latte-upgrade-");
      TarTools.untar(tarball, tempDir, false, false);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to extract update: " + e.getMessage());
    }

    // Replace latte.home contents
    Path home = Path.of(latteHome);
    try {
      for (String dir : new String[]{"bin", "lib", "templates"}) {
        Path existing = home.resolve(dir);
        Path replacement = tempDir.resolve(dir);
        if (Files.isDirectory(existing)) {
          FileTools.prune(existing);
        }
        if (Files.isDirectory(replacement)) {
          Files.move(replacement, existing);
        }
      }
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to install update: " + e.getMessage());
    }

    output.infoln("Updated Latte from %s to %s", currentVersion, latestVersion);
  }
```

- [ ] **Step 4: Build and verify**

Run: `latte build`
Expected: Compiles successfully.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java
git commit -m "Implement runtime upgrade via GitHub Releases API"
```

---

### Task 3: Implement plugin upgrade

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`

This task replaces the `upgradePlugins` stub. It reads the project file, finds all `loadPlugin` lines, queries the repository search API for each, and rewrites the version in the file.

- [ ] **Step 1: Add a helper to query the repository search API**

Add this method to `UpgradeCommand.java`:

```java
  /**
   * Queries the Latte repository search API for the latest version of an artifact.
   *
   * @return The latest version string, or null if not found.
   */
  private String queryLatestVersion(String artifactId) {
    try {
      String encodedId = java.net.URLEncoder.encode(artifactId, java.nio.charset.StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.lattejava.org/repository/search?id=" + encodedId + "&latest=true"))
          .GET()
          .timeout(Duration.ofMillis(10_000))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404) {
        return null;
      }
      if (response.statusCode() != 200) {
        return null;
      }
      JSONObject json = (JSONObject) new JSONParser().parse(response.body());
      JSONArray versions = (JSONArray) json.get("versions");
      if (versions == null || versions.isEmpty()) {
        return null;
      }
      return (String) versions.getFirst();
    } catch (Exception e) {
      return null;
    }
  }
```

- [ ] **Step 2: Replace the upgradePlugins method**

```java
  private void upgradePlugins(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'plugins' upgrade requires a project.latte file.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    String content;
    try {
      content = Files.readString(projectFile, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to read project.latte: " + e.getMessage());
    }

    // Find all loadPlugin lines and update versions
    // Pattern: loadPlugin(id: "group:name:version")
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "(loadPlugin\\(id:\\s*\"([^:]+:[^:]+):)([^\"]+)(\"\\))");
    java.util.regex.Matcher matcher = pattern.matcher(content);
    StringBuilder result = new StringBuilder();
    boolean updated = false;

    while (matcher.find()) {
      String artifactId = matcher.group(2);
      String currentVersion = matcher.group(3);
      String latestVersion = queryLatestVersion(artifactId);

      if (latestVersion != null && !latestVersion.equals(currentVersion)) {
        output.infoln("Upgrading plugin [%s] from %s to %s", artifactId, currentVersion, latestVersion);
        matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
            matcher.group(1) + latestVersion + matcher.group(4)));
        updated = true;
      } else {
        matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
        if (latestVersion == null) {
          output.infoln("Plugin [%s:%s] not found in repository, skipping", artifactId, currentVersion);
        } else {
          output.infoln("Plugin [%s] already at latest version %s", artifactId, currentVersion);
        }
      }
    }
    matcher.appendTail(result);

    if (updated) {
      try {
        Files.writeString(projectFile, result.toString(), java.nio.charset.StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeFailureException("Failed to write project.latte: " + e.getMessage());
      }
    }
  }
```

- [ ] **Step 3: Build and verify**

Run: `latte build`
Expected: Compiles successfully.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java
git commit -m "Implement plugin upgrade via repository search API"
```

---

### Task 4: Implement dependency and dependencies upgrade

**Files:**
- Modify: `src/main/java/org/lattejava/cli/command/UpgradeCommand.java`
- Modify: `src/main/java/org/lattejava/cli/command/InstallCommand.java` — Extract `replaceDependenciesBlock` and `generateDependenciesBlock` to be reusable (make them package-private static or move to a shared utility)

This task implements both `dependency` (single) and `dependencies` (all) upgrades.

- [ ] **Step 1: Make InstallCommand helper methods reusable**

In `src/main/java/org/lattejava/cli/command/InstallCommand.java`, change the visibility of `replaceDependenciesBlock` and `generateDependenciesBlock` from `private` to package-private `static`:

Change:
```java
  private void replaceDependenciesBlock(Path projectFile, Dependencies dependencies) {
```
to:
```java
  static void replaceDependenciesBlock(Path projectFile, Dependencies dependencies) {
```

Change:
```java
  private String generateDependenciesBlock(Dependencies dependencies, String indent) {
```
to:
```java
  static String generateDependenciesBlock(Dependencies dependencies, String indent) {
```

- [ ] **Step 2: Replace the upgradeDependency method**

```java
  private void upgradeDependency(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependency' upgrade requires a project.latte file.");
    }

    if (configuration.args.size() < 2) {
      throw new RuntimeFailureException("Usage: latte upgrade dependency <group:name:version>");
    }

    String dependencySpec = configuration.args.get(1);
    Artifact artifact;
    try {
      artifact = new Artifact(dependencySpec);
    } catch (Exception e) {
      throw new RuntimeFailureException("Invalid dependency [" + dependencySpec + "]. Expected format: group:name:version");
    }

    if (project.dependencies == null) {
      throw new RuntimeFailureException("No dependencies found in project.latte.");
    }

    // Find and replace the dependency in the matching group
    boolean found = false;
    for (DependencyGroup group : project.dependencies.groups.values()) {
      for (int i = 0; i < group.dependencies.size(); i++) {
        Artifact existing = group.dependencies.get(i);
        if (existing.id.equals(artifact.id)) {
          group.dependencies.set(i, artifact);
          found = true;
          output.infoln("Upgrading [%s] from %s to %s in [%s] group", artifact.id, existing.version, artifact.version, group.name);
          break;
        }
      }
    }

    if (!found) {
      throw new RuntimeFailureException("Dependency [" + artifact.id + "] not found in any dependency group.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    InstallCommand.replaceDependenciesBlock(projectFile, project.dependencies);
  }
```

- [ ] **Step 3: Replace the upgradeDependencies method**

```java
  private void upgradeDependencies(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependencies' upgrade requires a project.latte file.");
    }

    if (project.dependencies == null) {
      output.infoln("No dependencies found in project.latte.");
      return;
    }

    boolean updated = false;
    for (DependencyGroup group : project.dependencies.groups.values()) {
      for (int i = 0; i < group.dependencies.size(); i++) {
        Artifact existing = group.dependencies.get(i);
        String artifactId = existing.id.group + ":" + existing.id.project;
        String latestVersion = queryLatestVersion(artifactId);

        if (latestVersion != null && !latestVersion.equals(existing.version.toString())) {
          output.infoln("Upgrading [%s] from %s to %s in [%s] group", artifactId, existing.version, latestVersion, group.name);
          group.dependencies.set(i, new Artifact(artifactId + ":" + latestVersion));
          updated = true;
        } else if (latestVersion == null) {
          output.infoln("Dependency [%s:%s] not found in repository, skipping", artifactId, existing.version);
        } else {
          output.infoln("Dependency [%s] already at latest version %s", artifactId, existing.version);
        }
      }
    }

    if (updated) {
      Path projectFile = project.directory.resolve("project.latte");
      InstallCommand.replaceDependenciesBlock(projectFile, project.dependencies);
    }
  }
```

- [ ] **Step 4: Add required imports**

Add to the imports of `UpgradeCommand.java`:

```java
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.DependencyGroup;
```

- [ ] **Step 5: Build and verify**

Run: `latte build`
Expected: Compiles successfully.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/UpgradeCommand.java src/main/java/org/lattejava/cli/command/InstallCommand.java
git commit -m "Implement dependency and dependencies upgrade"
```

---

### Task 5: Manual integration test

- [ ] **Step 1: Test help**

Run: `latte upgrade help`
Expected: Prints the usage information.

- [ ] **Step 2: Test runtime check**

Run: `latte upgrade runtime`
Expected: Prints current version, checks GitHub, and either says "already up to date" or performs the upgrade.

- [ ] **Step 3: Test plugin upgrade (dry run)**

In a test project directory with a `project.latte` that has plugins, run:
```bash
latte upgrade plugins
```
Expected: Prints which plugins are being upgraded or are already at latest.

- [ ] **Step 4: Test dependency upgrade**

```bash
latte upgrade dependency org.example:lib:2.0.0
```
Expected: Updates the dependency version in project.latte.

- [ ] **Step 5: Test dependencies upgrade**

```bash
latte upgrade dependencies
```
Expected: Checks each dependency against the repository and upgrades if newer versions exist.

- [ ] **Step 6: Commit any fixes**

If any issues are found during testing, fix and commit.
