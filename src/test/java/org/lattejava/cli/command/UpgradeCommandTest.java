/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.parser.DefaultTargetGraphBuilder;
import org.lattejava.cli.parser.groovy.GroovyProjectFileParser;
import org.lattejava.cli.parser.groovy.GroovySourceTools;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactSpec;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.domain.Version;
import org.lattejava.io.FileTools;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the UpgradeCommand.
 *
 * @author Brian Pontarelli
 */
public class UpgradeCommandTest extends BaseUnitTest {
  private static final String BASE_PROJECT = """
      project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
        workflow {
          standard()
        }

        dependencies {
          group(name: "compile") {
            dependency(id: "com.fasterxml.jackson.core:jackson-core:2.13.4")
          }
          group(name: "test-compile", export: false) {
            dependency(id: "org.testng:testng:6.8.7")
          }
        }

        publications {
          standard()
        }
      }

      // Plugins
      dependency = loadPlugin(id: "org.lattejava.plugin:dependency:0.1.0")
      java = loadPlugin(id: "org.lattejava.plugin:java:0.1.0")
      release = loadPlugin(id: "org.lattejava.plugin:release-git:0.1.0")
      """;

  private Path testDir;

  @BeforeMethod
  public void beforeMethod() throws IOException {
    testDir = Files.createTempDirectory("latte-upgrade-test");
  }

  @AfterMethod
  public void afterMethod() throws IOException {
    if (testDir != null) {
      FileTools.prune(testDir);
    }
  }

  // ---- Dispatch and help tests ----

  @Test
  public void noArgsShowsHelp() throws IOException {
    Project project = createProject(BASE_PROJECT);
    RuntimeConfiguration config = new RuntimeConfiguration();

    new UpgradeCommand().run(config, output, project);

    // Project file should be unchanged
    assertEquals(Files.readString(testDir.resolve("project.latte")), BASE_PROJECT);
  }

  @Test
  public void helpSubcommand() throws IOException {
    Project project = createProject(BASE_PROJECT);
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("help");

    new UpgradeCommand().run(config, output, project);

    // Project file should be unchanged
    assertEquals(Files.readString(testDir.resolve("project.latte")), BASE_PROJECT);
  }

  @Test
  public void unknownSubcommand() throws IOException {
    Project project = createProject(BASE_PROJECT);
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("bogus");

    try {
      new UpgradeCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("Unknown upgrade parameter [bogus]"));
    }
  }

  // ---- upgradeDependency tests ----

  @Test
  public void upgradeDependencyWithVersion() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "com.fasterxml.jackson.core:jackson-core", "2.17.0");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(testDir.resolve("project.latte"));
    assertTrue(result.contains("com.fasterxml.jackson.core:jackson-core:2.17.0"));
    assertFalse(result.contains("jackson-core:2.13.4"));

    Project reparsed = reparseProject();
    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertNotNull(compile);
    assertEquals(compile.dependencies.size(), 1);
    assertEquals(compile.dependencies.getFirst().id.group, "com.fasterxml.jackson.core");
    assertEquals(compile.dependencies.getFirst().id.project, "jackson-core");
    assertEquals(compile.dependencies.getFirst().version, new Version("2.17.0"));
  }

  @Test
  public void upgradeDependencyInTestGroup() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.testng:testng", "7.12.0");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(testDir.resolve("project.latte"));
    assertTrue(result.contains("org.testng:testng:7.12.0"));
    assertFalse(result.contains("testng:6.8.7"));

    Project reparsed = reparseProject();
    DependencyGroup testCompile = reparsed.dependencies.groups.get("test-compile");
    assertNotNull(testCompile);
    assertEquals(testCompile.dependencies.size(), 1);
    assertEquals(testCompile.dependencies.getFirst().id.group, "org.testng");
    assertEquals(testCompile.dependencies.getFirst().id.project, "testng");
    assertEquals(testCompile.dependencies.getFirst().version, new Version("7.12.0"));
    assertFalse(testCompile.export);
  }

  @Test
  public void upgradeDependencyNotFound() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.nonexistent:fake", "1.0.0");

    try {
      new UpgradeCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("not found in any dependency group"));
    }

    // Project file should be unchanged
    assertEquals(Files.readString(testDir.resolve("project.latte")), BASE_PROJECT);
  }

  @Test
  public void upgradeDependencyNoProject() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.foo:bar", "1.0");

    try {
      new UpgradeCommand().run(config, output, null);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("requires a project.latte"));
    }
  }

  @Test
  public void upgradeDependencyMissingArgs() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency");

    try {
      new UpgradeCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("Usage"));
    }
  }

  @Test
  public void upgradeDependencyInvalidId() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "not-valid", "1.0");

    try {
      new UpgradeCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("Invalid dependency"));
    }
  }

  @Test
  public void upgradeDependencyNoDependenciesInProject() throws IOException {
    String noDepsProject = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, noDepsProject);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.foo:bar", "1.0");

    try {
      new UpgradeCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("No dependencies found"));
    }
  }

  @Test
  public void upgradeDependencyPreservesOtherDependencies() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "com.fasterxml.jackson.core:jackson-core", "2.17.0");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(testDir.resolve("project.latte"));
    assertTrue(result.contains("jackson-core:2.17.0"));
    assertTrue(result.contains("org.testng:testng:6.8.7"));
    // Plugin lines should be untouched
    assertTrue(result.contains("loadPlugin(id: \"org.lattejava.plugin:dependency:0.1.0\")"));

    Project reparsed = reparseProject();
    assertEquals(reparsed.dependencies.groups.size(), 2);

    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertEquals(compile.dependencies.size(), 1);
    assertEquals(compile.dependencies.getFirst().version, new Version("2.17.0"));

    DependencyGroup testCompile = reparsed.dependencies.groups.get("test-compile");
    assertEquals(testCompile.dependencies.size(), 1);
    assertEquals(testCompile.dependencies.getFirst().version, new Version("6.8.7"));
  }

  // ---- upgradeDependencies tests ----

  @Test
  public void upgradeDependenciesNoProject() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");

    try {
      new UpgradeCommand().run(config, output, null);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("requires a project.latte"));
    }
  }

  @Test
  public void upgradeDependenciesNoDeps() throws IOException {
    String noDepsProject = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, noDepsProject);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");

    new UpgradeCommand().run(config, output, project);

    assertEquals(Files.readString(projectFile), noDepsProject);
  }

  @Test
  public void upgradeDependenciesUpgradesAll() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          dependencies {
            group(name: "compile") {
              dependency(id: "org.lattejava:cli:0.1.0")
            }
            group(name: "test-compile", export: false) {
              dependency(id: "org.lattejava.plugin:dependency:0.1.0")
            }
          }

          publications {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);

    Project project = new Project(testDir, output);
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava:cli:0.1.0")),
        new DependencyGroup("test-compile", false,
            new Artifact("org.lattejava.plugin:dependency:0.1.0"))
    );

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");

    new UpgradeCommand().run(config, output, project);

    Project reparsed = reparseProject();
    assertEquals(reparsed.dependencies.groups.size(), 2);

    // Both should have been upgraded beyond 0.1.0
    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertEquals(compile.dependencies.size(), 1);
    assertTrue(compile.dependencies.getFirst().version.compareTo(new Version("0.1.0")) > 0,
        "Expected cli to be upgraded past 0.1.0 but was " + compile.dependencies.getFirst().version);

    DependencyGroup testCompile = reparsed.dependencies.groups.get("test-compile");
    assertEquals(testCompile.dependencies.size(), 1);
    assertTrue(testCompile.dependencies.getFirst().version.compareTo(new Version("0.1.0")) > 0,
        "Expected dependency plugin to be upgraded past 0.1.0 but was " + testCompile.dependencies.getFirst().version);
    assertFalse(testCompile.export);
  }

  @Test
  public void upgradeDependenciesSkipsUnknown() throws IOException {
    // Mix of a known Latte artifact and an unknown one
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          dependencies {
            group(name: "compile") {
              dependency(id: "org.lattejava:cli:0.1.0")
              dependency(id: "org.nonexistent:fake-lib:1.0.0")
            }
          }

          publications {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);

    Project project = new Project(testDir, output);
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava:cli:0.1.0"),
            new Artifact("org.nonexistent:fake-lib:1.0.0"))
    );

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependencies");

    new UpgradeCommand().run(config, output, project);

    Project reparsed = reparseProject();
    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertEquals(compile.dependencies.size(), 2);

    // cli should be upgraded
    Artifact cli = compile.dependencies.stream()
        .filter(a -> a.id.project.equals("cli"))
        .findFirst().orElseThrow();
    assertTrue(cli.version.compareTo(new Version("0.1.0")) > 0);

    // fake-lib should remain at 1.0.0 (not found in repository, skipped)
    Artifact fakeDep = compile.dependencies.stream()
        .filter(a -> a.id.project.equals("fake-lib"))
        .findFirst().orElseThrow();
    assertEquals(fakeDep.version, new Version("1.0.0"));
  }

  // ---- upgradePlugins tests ----

  @Test
  public void upgradePluginsNoProject() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("plugins");

    try {
      new UpgradeCommand().run(config, output, null);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("requires a project.latte"));
    }
  }

  @Test
  public void upgradePluginsNoPlugins() throws IOException {
    String noPluginsProject = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, noPluginsProject);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("plugins");

    new UpgradeCommand().run(config, output, project);

    assertEquals(Files.readString(projectFile), noPluginsProject);
  }

  @Test
  public void upgradePluginsUpgradesAll() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          publications {
            standard()
          }
        }

        // Plugins
        dependency = loadPlugin(id: "org.lattejava.plugin:dependency:0.1.0")
        java = loadPlugin(id: "org.lattejava.plugin:java:0.1.0")
        release = loadPlugin(id: "org.lattejava.plugin:release-git:0.1.0")
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("plugins");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(projectFile);

    // All three plugins should have been upgraded past 0.1.0
    assertFalse(result.contains("dependency:0.1.0\""), "dependency plugin should have been upgraded");
    assertFalse(result.contains("java:0.1.0\""), "java plugin should have been upgraded");
    assertFalse(result.contains("release-git:0.1.0\""), "release-git plugin should have been upgraded");

    // Verify the new versions by reparsing the loadPlugin string literals
    var literals = GroovySourceTools.findMethodCallStringArguments(result, "loadPlugin");
    assertEquals(literals.size(), 3);

    for (var literal : literals) {
      ArtifactSpec spec = new ArtifactSpec(literal.value());
      Version version = new Version(spec.version);
      assertTrue(version.compareTo(new Version("0.1.0")) > 0,
          "Expected plugin [" + spec.id + "] to be upgraded past 0.1.0 but was " + spec.version);
    }

    // The project block should be untouched
    assertTrue(result.contains("org.example"));
    assertTrue(result.contains("version: \"0.1.0\""));
  }

  @Test
  public void upgradePluginsSkipsUnknown() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        }

        dependency = loadPlugin(id: "org.lattejava.plugin:dependency:0.1.0")
        fake = loadPlugin(id: "org.nonexistent:fake-plugin:1.0.0")
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);
    Project project = new Project(testDir, output);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("plugins");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(projectFile);

    // dependency plugin should be upgraded
    assertFalse(result.contains("dependency:0.1.0\""));

    // fake plugin should remain unchanged
    assertTrue(result.contains("org.nonexistent:fake-plugin:1.0.0"));

    var literals = GroovySourceTools.findMethodCallStringArguments(result, "loadPlugin");
    assertEquals(literals.size(), 2);

    for (var literal : literals) {
      ArtifactSpec spec = new ArtifactSpec(literal.value());
      if (spec.id.project.equals("dependency")) {
        assertTrue(new Version(spec.version).compareTo(new Version("0.1.0")) > 0);
      } else if (spec.id.project.equals("fake-plugin")) {
        assertEquals(spec.version, "1.0.0");
      }
    }
  }

  // ---- upgradeRuntime tests ----

  @Test
  public void upgradeRuntimeNoLatteHome() {
    String original = System.getProperty("latte.home");
    try {
      System.clearProperty("latte.home");

      RuntimeConfiguration config = new RuntimeConfiguration();
      config.args = List.of("runtime");

      try {
        new UpgradeCommand().run(config, output, null);
        fail("Should have thrown RuntimeFailureException");
      } catch (RuntimeFailureException e) {
        assertTrue(e.getMessage().contains("latte.home"));
      }
    } finally {
      if (original != null) {
        System.setProperty("latte.home", original);
      }
    }
  }

  /**
   * Reparses the project.latte file from disk, stripping lines outside the project block (loadPlugin calls,
   * plugin settings, etc.) which would fail without the actual plugins installed.
   */
  private Project reparseProject() throws IOException {
    Path projectFile = testDir.resolve("project.latte");
    String content = Files.readString(projectFile);

    // Extract just the project(...) { ... } block using GroovySourceTools
    var block = org.lattejava.cli.parser.groovy.GroovySourceTools.findBlock(content, "project", 0);
    assertNotNull(block, "Could not find project block in reparsed file");
    String projectBlock = content.substring(block.start(), block.end());

    Path tempFile = testDir.resolve("project-reparse.latte");
    Files.writeString(tempFile, projectBlock + "\n");

    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    return parser.parse(tempFile, new RuntimeConfiguration());
  }

  @Test
  public void upgradeDependencyPreservesSkipCompatibilityCheck() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          dependencies {
            group(name: "compile") {
              dependency(id: "org.slf4j:slf4j-api:2.0.16", skipCompatibilityCheck: true)
              dependency(id: "org.lattejava:cli:0.1.0")
            }
          }

          publications {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);

    Project project = new Project(testDir, output);
    var slf4j = new Artifact("org.slf4j:slf4j-api:2.0.16", null, true, null);
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true, slf4j, new Artifact("org.lattejava:cli:0.1.0"))
    );

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.lattejava:cli", "0.1.4");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(projectFile);
    assertTrue(result.contains("skipCompatibilityCheck: true"), "skipCompatibilityCheck should be preserved in output");
    assertTrue(result.contains("org.slf4j:slf4j-api:2.0.16"));
    assertTrue(result.contains("org.lattejava:cli:0.1.4"));

    Project reparsed = reparseProject();
    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertEquals(compile.dependencies.size(), 2);

    Artifact slf4jReparsed = compile.dependencies.stream()
        .filter(a -> a.id.project.equals("slf4j-api"))
        .findFirst().orElseThrow();
    assertTrue(slf4jReparsed.skipCompatibilityCheck, "skipCompatibilityCheck should survive round-trip");
  }

  @Test
  public void upgradeDependencyPreservesExclusions() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          dependencies {
            group(name: "compile") {
              dependency(id: "org.example:has-exclusions:1.0.0") {
                exclusion(id: "org.example:excluded1")
                exclusion(id: "org.example:excluded2")
              }
              dependency(id: "org.lattejava:cli:0.1.0")
            }
          }

          publications {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);

    Project project = new Project(testDir, output);
    var withExclusions = new Artifact("org.example:has-exclusions:1.0.0", null, false,
        List.of(new ArtifactID("org.example:excluded1"), new ArtifactID("org.example:excluded2")));
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true, withExclusions, new Artifact("org.lattejava:cli:0.1.0"))
    );

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.lattejava:cli", "0.1.4");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(projectFile);
    assertTrue(result.contains("org.example:has-exclusions:1.0.0"), "Dependency with exclusions should be preserved");
    assertTrue(result.contains("exclusion(id: \"org.example:excluded1\")"), "First exclusion should be preserved");
    assertTrue(result.contains("exclusion(id: \"org.example:excluded2\")"), "Second exclusion should be preserved");
    assertTrue(result.contains("org.lattejava:cli:0.1.4"));

    Project reparsed = reparseProject();
    DependencyGroup compile = reparsed.dependencies.groups.get("compile");
    assertEquals(compile.dependencies.size(), 2);

    Artifact excluded = compile.dependencies.stream()
        .filter(a -> a.id.project.equals("has-exclusions"))
        .findFirst().orElseThrow();
    assertEquals(excluded.exclusions.size(), 2);
    assertEquals(excluded.exclusions.get(0).group, "org.example");
    assertEquals(excluded.exclusions.get(0).project, "excluded1");
    assertEquals(excluded.exclusions.get(1).group, "org.example");
    assertEquals(excluded.exclusions.get(1).project, "excluded2");
  }

  @Test
  public void upgradeDependencyPreservesNonSemanticVersion() throws IOException {
    String projectContent = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }

          dependencies {
            group(name: "compile") {
              dependency(id: "com.googlecode.jarjar:jarjar:1.3")
              dependency(id: "org.lattejava:cli:0.1.0")
            }
          }

          publications {
            standard()
          }
        }
        """;

    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, projectContent);

    Project project = new Project(testDir, output);
    // jarjar 1.3 is a non-semantic version (2 parts) — the parser stores "1.3" as nonSemanticVersion
    // and the semantic version becomes 1.3.0
    var jarjar = new Artifact(new ArtifactID("com.googlecode.jarjar:jarjar"), new Version("1.3.0"), "1.3", null);
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true, jarjar, new Artifact("org.lattejava:cli:0.1.0"))
    );

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("dependency", "org.lattejava:cli", "0.1.4");

    new UpgradeCommand().run(config, output, project);

    String result = Files.readString(projectFile);
    // The non-semantic version "1.3" should be written back, not the semantic "1.3.0"
    assertTrue(result.contains("com.googlecode.jarjar:jarjar:1.3"), "Non-semantic version should be preserved, got: " + result);
    assertFalse(result.contains("jarjar:1.3.0"), "Should not write semantic version 1.3.0 for non-semantic dependency");
    assertTrue(result.contains("org.lattejava:cli:0.1.4"));
  }

  private Project createProject(String content) throws IOException {
    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, content);

    Project project = new Project(testDir, output);
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("com.fasterxml.jackson.core:jackson-core:2.13.4")),
        new DependencyGroup("test-compile", false,
            new Artifact("org.testng:testng:6.8.7"))
    );
    return project;
  }
}
