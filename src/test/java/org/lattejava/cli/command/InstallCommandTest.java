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
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the InstallCommand.
 *
 * @author Brian Pontarelli
 */
public class InstallCommandTest extends BaseUnitTest {
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
      """;

  private Path testDir;

  @BeforeMethod
  public void beforeMethod() throws IOException {
    testDir = Files.createTempDirectory("latte-install-test");
  }

  @AfterMethod
  public void afterMethod() throws IOException {
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
  public void installToCompileGroup() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("org.slf4j:slf4j-api", "2.0.16");

    new InstallCommand().run(config, output, project);

    String expected = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        
          dependencies {
            group(name: "compile") {
              dependency(id: "com.fasterxml.jackson.core:jackson-core:2.13.4")
              dependency(id: "org.slf4j:slf4j-api:2.0.16")
            }
            group(name: "test-compile", export: false) {
              dependency(id: "org.testng:testng:6.8.7")
            }
          }
        
          publications {
            standard()
          }
        }
        """;
    assertEquals(Files.readString(testDir.resolve("project.latte")), expected);
  }

  @Test
  public void installToTestGroup() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("org.easymock:easymock", "3.2.0", "test-compile");

    new InstallCommand().run(config, output, project);

    String expected = """
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
              dependency(id: "org.easymock:easymock:3.2.0")
            }
          }
        
          publications {
            standard()
          }
        }
        """;
    assertEquals(Files.readString(testDir.resolve("project.latte")), expected);
  }

  @Test
  public void installToNewGroup() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("org.example:runtime-lib", "1.0.0", "runtime");

    new InstallCommand().run(config, output, project);

    String expected = """
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
            group(name: "runtime") {
              dependency(id: "org.example:runtime-lib:1.0.0")
            }
          }
        
          publications {
            standard()
          }
        }
        """;
    assertEquals(Files.readString(testDir.resolve("project.latte")), expected);
  }

  @Test
  public void installDuplicate() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("com.fasterxml.jackson.core:jackson-core", "2.13.4");

    new InstallCommand().run(config, output, project);

    // File should be unchanged — the dependency already exists
    assertEquals(Files.readString(testDir.resolve("project.latte")), BASE_PROJECT);
  }

  @Test
  public void installNoDependenciesBlock() throws IOException {
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
    config.args = List.of("org.slf4j:slf4j-api", "2.0.16");

    new InstallCommand().run(config, output, project);

    String expected = """
        project(group: "org.example", name: "test", version: "0.1.0", licenses: ["MIT"]) {
          workflow {
            standard()
          }
        
          dependencies {
            group(name: "compile") {
              dependency(id: "org.slf4j:slf4j-api:2.0.16")
            }
          }
        }
        """;
    assertEquals(Files.readString(projectFile), expected);
  }

  @Test
  public void installNoProject() {
    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("org.foo:bar", "1.0");

    try {
      new InstallCommand().run(config, output, null);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("requires a project.latte"));
    }
  }

  @Test
  public void installNoArgs() {
    Project project = new Project(testDir, output);
    RuntimeConfiguration config = new RuntimeConfiguration();

    try {
      new InstallCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("Usage"));
    }
  }

  @Test
  public void installInvalidSpec() throws IOException {
    Project project = createProject(BASE_PROJECT);

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("not-a-valid-spec");

    try {
      new InstallCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("Invalid dependency"));
    }
  }

  @Test
  public void installArtifactNotFound() throws IOException {
    Project project = createProject(BASE_PROJECT);
    project.workflow = workflow;

    RuntimeConfiguration config = new RuntimeConfiguration();
    config.args = List.of("org.nonexistent:fake-lib", "99.99.99");

    try {
      new InstallCommand().run(config, output, project);
      fail("Should have thrown RuntimeFailureException");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("could not be found"));
    }

    // Verify the project file was NOT modified
    assertEquals(Files.readString(testDir.resolve("project.latte")), BASE_PROJECT);
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
