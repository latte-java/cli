/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.cli.command;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.lattejava.*;
import org.lattejava.cli.domain.*;
import org.lattejava.cli.runtime.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Tests the InitCommand.
 *
 * @author Brian Pontarelli
 */
public class InitCommandTest extends BaseUnitTest {
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

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }

    try (var stream = Files.walk(root)) {
      stream.sorted(Comparator.reverseOrder())
            .forEach(p -> {
              try {
                Files.deleteIfExists(p);
              } catch (IOException ignored) {
              }
            });
    }
  }

  @AfterMethod
  public void afterMethod() throws IOException {
    deleteRecursively(templateDir);
    deleteRecursively(testDir);
  }

  @BeforeMethod
  public void beforeMethod() throws IOException {
    testDir = Files.createTempDirectory("latte-init-test");
    templateDir = Files.createTempDirectory("latte-template-dir");
    Files.writeString(templateDir.resolve("project.latte"), TEST_PROJECT_LATTE);
  }

  @Test
  public void copyTemplateCreatesParentDirectories() throws IOException {
    Path nested = templateDir.resolve("deeply/nested/dir");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("file.txt"), "hi");

    runInit("org.example", "widget", "MIT");

    assertTrue(Files.isRegularFile(testDir.resolve("deeply/nested/dir/file.txt")));
  }

  @Test
  public void copyTemplatePreflightAbortsWhenTargetExists() throws IOException {
    Files.writeString(templateDir.resolve("a.txt"), "template-a");
    Files.writeString(templateDir.resolve("b.txt"), "template-b");
    Files.writeString(testDir.resolve("b.txt"), "existing-b");

    try {
      runInit("org.example", "widget", "MIT");
      fail("Expected RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[b.txt]"));
      assertTrue(e.getMessage().contains("already exists"));
    }

    // Preflight aborts before any writes: nothing else from the template was written
    assertFalse(Files.exists(testDir.resolve("a.txt")));
    assertFalse(Files.exists(testDir.resolve("project.latte")));
    // b.txt's existing content must be untouched
    assertEquals(Files.readString(testDir.resolve("b.txt")), "existing-b");
  }

  @Test
  public void copyTemplatePreservesEmptyGitkeepFiles() throws IOException {
    Path emptyDir = templateDir.resolve("resources");
    Files.createDirectories(emptyDir);
    Files.writeString(emptyDir.resolve(".gitkeep"), "");

    runInit("org.example", "widget", "MIT");

    Path result = testDir.resolve("resources/.gitkeep");
    assertTrue(Files.isRegularFile(result));
    assertEquals(Files.size(result), 0L);
  }

  @Test
  public void copyTemplateSubstitutesPathSegments() throws IOException {
    Path nested = templateDir.resolve("src/main/java/${packagePath}");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("Placeholder.java"), "package ${package};");

    runInit("org.example", "my-lib", "MIT");

    Path expected = testDir.resolve("src/main/java/org/example/my_lib/Placeholder.java");
    assertTrue(Files.isRegularFile(expected));
    assertEquals(Files.readString(expected), "package org.example.my_lib;");
  }

  @Test
  public void deriveVariablesBasic() throws IOException {
    Files.writeString(templateDir.resolve("vars.txt"),
        "group=${group} name=${name} license=${license} nameId=${nameId} package=${package} packagePath=${packagePath}");

    runInit("org.example", "widget", "MIT");

    assertEquals(Files.readString(testDir.resolve("vars.txt")),
        "group=org.example name=widget license=MIT nameId=widget package=org.example.widget packagePath=org/example/widget");
  }

  @Test
  public void deriveVariablesHyphenatedNameBecomesUnderscoreIdentifier() throws IOException {
    Files.writeString(templateDir.resolve("vars.txt"),
        "name=${name} nameId=${nameId} package=${package} packagePath=${packagePath}");

    runInit("org.example", "my-lib", "Apache-2.0");

    assertEquals(Files.readString(testDir.resolve("vars.txt")),
        "name=my-lib nameId=my_lib package=org.example.my_lib packagePath=org/example/my_lib");
  }

  @Test
  public void deriveVariablesMultiSegmentGroup() throws IOException {
    Files.writeString(templateDir.resolve("vars.txt"),
        "package=${package} packagePath=${packagePath}");

    runInit("com.acme.foo", "bar", "MIT");

    assertEquals(Files.readString(testDir.resolve("vars.txt")),
        "package=com.acme.foo.bar packagePath=com/acme/foo/bar");
  }

  @Test
  public void init() throws IOException {
    Scanner scanner = new Scanner("org.example\nmy-library\nApache-2.0\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(testDir, output));

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
  public void initAlreadyExists() throws IOException {
    Path projectFile = testDir.resolve("project.latte");
    Files.writeString(projectFile, "existing content");

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);

    try {
      command.run(configWithTemplate(), output, new Project(testDir, output));
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[project.latte]"));
      assertTrue(e.getMessage().contains("already exists"));
    }

    assertEquals(Files.readString(projectFile), "existing content");
  }

  @Test
  public void initLibraryTemplateEndToEnd() throws IOException {
    String originalHome = System.getProperty("latte.home");
    System.setProperty("latte.home", "src/main");
    try {
      Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
      InitCommand command = new InitCommand(scanner);
      command.run(new RuntimeConfiguration(), output, new Project(testDir, output));

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
      assertTrue(testModuleContent.contains("opens org.example.my_lib.tests to org.testng;"));

      // PlaceholderTest
      Path placeholderTest = testDir.resolve("src/test/java/org/example/my_lib/tests/PlaceholderTest.java");
      assertTrue(Files.isRegularFile(placeholderTest));
      assertTrue(Files.readString(placeholderTest).contains("package org.example.my_lib.tests;"));

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

  @Test
  public void initOverrideDefaults() throws IOException {
    Path namedDir = testDir.resolve("my-cool-project");
    Files.createDirectories(namedDir);

    Scanner scanner = new Scanner("org.example\ncustom-name\nApache-2.0\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(namedDir, output));

    String content = Files.readString(namedDir.resolve("project.latte"));
    assertTrue(content.contains("name: \"custom-name\""));
    assertTrue(content.contains("licenses: [\"Apache-2.0\"]"));
  }

  @Test
  public void initWebTemplateEndToEnd() throws IOException {
    String originalHome = System.getProperty("latte.home");
    System.setProperty("latte.home", "src/main");
    try {
      RuntimeConfiguration config = new RuntimeConfiguration();
      config.args.add("web");

      Scanner scanner = new Scanner("org.example\nmy-web\nMIT\n");
      InitCommand command = new InitCommand(scanner);
      command.run(config, output, new Project(testDir, output));

      String projectLatte = Files.readString(testDir.resolve("project.latte"));
      assertTrue(projectLatte.contains("group: \"org.example\""));
      assertTrue(projectLatte.contains("name: \"my-web\""));
      assertTrue(projectLatte.contains("org.lattejava:web"));
      assertTrue(projectLatte.contains("java.run(main: \"org.example.my_web.Main\")"));

      Path main = testDir.resolve("src/main/java/org/example/my_web/Main.java");
      assertTrue(Files.isRegularFile(main));
      assertTrue(Files.readString(main).contains("import module org.lattejava.web;"));

      Path mainTest = testDir.resolve("src/test/java/org/example/my_web/tests/MainTest.java");
      assertTrue(Files.isRegularFile(mainTest));
      assertTrue(Files.readString(mainTest).contains("public class MainTest"));

      assertTrue(Files.isRegularFile(testDir.resolve("web/static/.gitkeep")));
    } finally {
      if (originalHome == null) {
        System.clearProperty("latte.home");
      } else {
        System.setProperty("latte.home", originalHome);
      }
    }
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
      command.run(config, output, new Project(testDir, output));

      assertEquals(Files.readString(testDir.resolve("project.latte")), "custom: org.test my-project MIT");
      Path placeholder = testDir.resolve("src/main/java/org/test/my_project/Placeholder.java");
      assertTrue(Files.isRegularFile(placeholder));
      assertEquals(Files.readString(placeholder), "package org.test.my_project;");
    } finally {
      deleteRecursively(customTemplate);
    }
  }

  @Test
  public void initWithDefaults() throws IOException {
    Path namedDir = testDir.resolve("my-cool-project");
    Files.createDirectories(namedDir);

    Scanner scanner = new Scanner("org.example\n\n\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(namedDir, output));

    String content = Files.readString(namedDir.resolve("project.latte"));
    assertTrue(content.contains("group: \"org.example\""));
    assertTrue(content.contains("name: \"my-cool-project\""));
    assertTrue(content.contains("licenses: [\"MIT\"]"));
  }

  @Test
  public void initWithExistingDirectories() throws IOException {
    Files.createDirectories(testDir.resolve("src/main/java"));
    Files.createDirectories(testDir.resolve("src/test/java"));

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(testDir, output));

    assertTrue(Files.isRegularFile(testDir.resolve("project.latte")));
  }

  @Test
  public void initWithInvalidInputThenValid() throws IOException {
    Scanner scanner = new Scanner("123bad\norg.example\n123bad\nmy-lib\nNOTALICENSE\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(testDir, output));

    String content = Files.readString(testDir.resolve("project.latte"));
    assertTrue(content.contains("group: \"org.example\""));
    assertTrue(content.contains("name: \"my-lib\""));
    assertTrue(content.contains("licenses: [\"MIT\"]"));
  }

  @Test
  public void initWithMIT() throws IOException {
    Scanner scanner = new Scanner("com.acme\nwidget\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, new Project(testDir, output));

    String content = Files.readString(testDir.resolve("project.latte"));
    assertTrue(content.contains("group: \"com.acme\""));
    assertTrue(content.contains("name: \"widget\""));
    assertTrue(content.contains("licenses: [\"MIT\"]"));
  }

  @Test
  public void initWithMissingTemplate() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("/nonexistent/template-dir");

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);

    try {
      command.run(config, output, new Project(testDir, output));
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[/nonexistent/template-dir]"));
      assertTrue(e.getMessage().contains("not found"));
    }
  }

  @Test
  public void resolveTemplateDirDefaultsToLibrary() throws IOException {
    Path fakeLatteHome = Files.createTempDirectory("latte-home");
    Path libraryDir = fakeLatteHome.resolve("templates/library");
    Files.createDirectories(libraryDir);
    Files.writeString(libraryDir.resolve("project.latte"), "from-library");

    String original = System.getProperty("latte.home");
    System.setProperty("latte.home", fakeLatteHome.toString());
    try {
      runInit(new RuntimeConfiguration(), "org.example", "widget", "MIT");

      assertEquals(Files.readString(testDir.resolve("project.latte")), "from-library");
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
  public void resolveTemplateDirMissingLatteHome() {
    String original = System.getProperty("latte.home");
    System.clearProperty("latte.home");
    try {
      runInit(new RuntimeConfiguration(), "org.example", "widget", "MIT");
      fail("Expected RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("latte.home"));
    } finally {
      if (original != null) {
        System.setProperty("latte.home", original);
      }
    }
  }

  @Test
  public void resolveTemplateDirNamedTemplate() throws IOException {
    Path fakeLatteHome = Files.createTempDirectory("latte-home");
    Path webDir = fakeLatteHome.resolve("templates/web");
    Files.createDirectories(webDir);
    Files.writeString(webDir.resolve("project.latte"), "from-web");

    String original = System.getProperty("latte.home");
    System.setProperty("latte.home", fakeLatteHome.toString());
    try {
      RuntimeConfiguration config = new RuntimeConfiguration();
      config.args.add("web");
      runInit(config, "org.example", "widget", "MIT");

      assertEquals(Files.readString(testDir.resolve("project.latte")), "from-web");
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
      runInit(config, "org.example", "widget", "MIT");
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
  public void resolveTemplateDirPathMissing() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add("/definitely/does/not/exist/anywhere");
    try {
      runInit(config, "org.example", "widget", "MIT");
      fail("Expected RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[/definitely/does/not/exist/anywhere]"));
      assertTrue(e.getMessage().contains("not found"));
    }
  }

  @Test
  public void resolveTemplateDirTildeExpansion() throws IOException {
    Path fakeHome = Files.createTempDirectory("fake-home");
    Path tildeTarget = Files.createTempDirectory(fakeHome, "tilde-template");
    Files.writeString(tildeTarget.resolve("project.latte"), "from-tilde");

    String originalHome = System.getProperty("user.home");
    System.setProperty("user.home", fakeHome.toString());
    try {
      String relative = tildeTarget.getFileName().toString();
      RuntimeConfiguration config = new RuntimeConfiguration();
      config.args.add("~/" + relative);
      runInit(config, "org.example", "widget", "MIT");

      assertEquals(Files.readString(testDir.resolve("project.latte")), "from-tilde");
    } finally {
      System.setProperty("user.home", originalHome);
      deleteRecursively(fakeHome);
    }
  }

  @Test
  public void substituteLeavesUnknownVariablesIntact() throws IOException {
    Files.writeString(templateDir.resolve("unknown.txt"), "hi ${name} from ${where}");

    runInit("org.example", "widget", "MIT");

    assertEquals(Files.readString(testDir.resolve("unknown.txt")), "hi widget from ${where}");
  }

  @Test
  public void substituteReplacesMultipleOccurrences() throws IOException {
    Files.writeString(templateDir.resolve("repeat.txt"), "${name}/${name}/${name}");

    runInit("org.example", "widget", "MIT");

    assertEquals(Files.readString(testDir.resolve("repeat.txt")), "widget/widget/widget");
  }

  @Test
  public void substituteReplacesSingleVariable() throws IOException {
    Files.writeString(templateDir.resolve("single.txt"), "hello ${name}");

    runInit("org.example", "widget", "MIT");

    assertEquals(Files.readString(testDir.resolve("single.txt")), "hello widget");
  }

  @Test
  public void substituteReturnsInputWhenNoVariablesPresent() throws IOException {
    Files.writeString(templateDir.resolve("plain.txt"), "plain text");

    runInit("org.example", "widget", "MIT");

    assertEquals(Files.readString(testDir.resolve("plain.txt")), "plain text");
  }

  private RuntimeConfiguration configWithTemplate() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args.add(templateDir.toString());
    return config;
  }

  private void runInit(String group, String name, String license) {
    runInit(configWithTemplate(), group, name, license);
  }

  private void runInit(RuntimeConfiguration config, String group, String name, String license) {
    Scanner scanner = new Scanner(group + "\n" + name + "\n" + license + "\n");
    new InitCommand(scanner).run(config, output, new Project(testDir, output));
  }
}
