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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Scanner;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the InitCommand.
 *
 * @author Brian Pontarelli
 */
public class InitCommandTest extends BaseUnitTest {
  private static final String TEST_TEMPLATE = """
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

  private Path templateFile;

  private Path testDir;

  @BeforeMethod
  public void beforeMethod() throws IOException {
    testDir = Files.createTempDirectory("latte-init-test");
    templateFile = Files.createTempFile("latte-template", ".latte");
    Files.writeString(templateFile, TEST_TEMPLATE);
  }

  @AfterMethod
  public void afterMethod() throws IOException {
    Files.deleteIfExists(templateFile);
    if (testDir != null) {
      Files.walk(testDir)
          .sorted(Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
          });
    }
  }

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

    // Verify directory layout was created
    assertTrue(Files.isDirectory(testDir.resolve("src/main/java")));
    assertTrue(Files.isDirectory(testDir.resolve("src/main/resources")));
    assertTrue(Files.isDirectory(testDir.resolve("src/test/java")));
    assertTrue(Files.isDirectory(testDir.resolve("src/test/resources")));
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
    // Pre-create some of the directories
    Files.createDirectories(testDir.resolve("src/main/java"));
    Files.createDirectories(testDir.resolve("src/test/java"));

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, testDir);

    assertTrue(Files.isRegularFile(testDir.resolve("project.latte")));
    assertTrue(Files.isDirectory(testDir.resolve("src/main/java")));
    assertTrue(Files.isDirectory(testDir.resolve("src/main/resources")));
    assertTrue(Files.isDirectory(testDir.resolve("src/test/java")));
    assertTrue(Files.isDirectory(testDir.resolve("src/test/resources")));
  }

  @Test
  public void initWithInvalidInputThenValid() throws IOException {
    // Bad group, then good; bad name, then good; bad license, then good
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
      assertTrue(e.getMessage().contains("already exists"));
    }

    assertEquals(Files.readString(projectFile), "existing content");
  }

  @Test
  public void initWithDefaults() throws IOException {
    // Create a directory with a valid project name
    Path namedDir = testDir.resolve("my-cool-project");
    Files.createDirectories(namedDir);

    // Empty input for name and license — should use defaults
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
    // Create a directory with a valid project name
    Path namedDir = testDir.resolve("my-cool-project");
    Files.createDirectories(namedDir);

    // Provide explicit values instead of accepting defaults
    Scanner scanner = new Scanner("org.example\ncustom-name\nApache-2.0\n");
    InitCommand command = new InitCommand(scanner);
    command.run(configWithTemplate(), output, namedDir);

    String content = Files.readString(namedDir.resolve("project.latte"));
    assertTrue(content.contains("name: \"custom-name\""));
    assertTrue(content.contains("licenses: [\"Apache-2.0\"]"));
  }

  @Test
  public void initWithCustomTemplate() throws IOException {
    Path customTemplate = Files.createTempFile("latte-custom", ".latte");
    try {
      Files.writeString(customTemplate, "custom: ${group} ${name} ${license}");

      RuntimeConfiguration config = new RuntimeConfiguration();
      config.switches.add("template", customTemplate.toString());

      Scanner scanner = new Scanner("org.test\nmy-project\nMIT\n");
      InitCommand command = new InitCommand(scanner);
      command.run(config, output, testDir);

      String content = Files.readString(testDir.resolve("project.latte"));
      assertEquals(content, "custom: org.test my-project MIT");
    } finally {
      Files.deleteIfExists(customTemplate);
    }
  }

  @Test
  public void initWithMissingTemplate() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.switches.add("template", "/nonexistent/template.latte");

    Scanner scanner = new Scanner("org.example\nmy-lib\nMIT\n");
    InitCommand command = new InitCommand(scanner);

    try {
      command.run(config, output, testDir);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("not found"));
    }
  }

  private RuntimeConfiguration configWithTemplate() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.switches.add("template", templateFile.toString());
    return config;
  }
}
