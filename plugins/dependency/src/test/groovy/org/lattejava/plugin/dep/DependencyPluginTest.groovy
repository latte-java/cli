/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.dep

import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.*
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.dep.workflow.process.MavenProcess
import org.lattejava.dep.workflow.process.URLProcess
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.Assert.*

/**
 * Tests the groovy plugin.
 *
 * @author Brian Pontarelli
 */
class DependencyPluginTest {
  public static Path projectDir

  Output output

  Project project

  Path cacheDir

  Path integrationDir

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
      projectDir = Paths.get("../dependency")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir, output)
    project.group = "org.lattejava.test"
    project.name = "dependency-test"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("Apache-2.0", null))

    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:multiple-versions:1.0.0"),
            new Artifact("org.lattejava.test:multiple-versions-different-dependencies:1.0.0"),
            new Artifact("org.lattejava.test:gpl-with-cpe:1.0.0")
        ),
        new DependencyGroup("runtime", true,
            new Artifact("org.lattejava.test:intermediate:1.0.0")
        )
    )

    cacheDir = projectDir.resolve("../../test-deps/latte")
    integrationDir = projectDir.resolve("../../test-deps/integration")

    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), null)
        ),
        output
    )
  }

  @Test
  void analyzeLicenses() {
//    output.enableDebug()

    FileTools.prune(projectDir.resolve("build/test/licenses"))

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.analyzeLicenses()
      fail("Expected the analyze to throw an exception")
    } catch (Exception ignore) {
      // Expected
    }

    // Fix everything and ensure it passes
    plugin.settings.license.allowedIDs.add("GPL-2.0-only")
    plugin.settings.license.allowedLicenses.add(License.parse("Commercial", "Commercial license"))
    plugin.settings.license.allowedLicenses.add(License.parse("OtherNonDistributableOpenSource", "Open source"))
    plugin.analyzeLicenses()

    // Remove one allowed and ensure it fails
    try {
      plugin.settings.license.allowedIDs.remove("GPL-2.0-with-classpath-exception")
      plugin.analyzeLicenses()
      fail("Expected the analyze to throw an exception")
    } catch (Exception ignore) {
      // Expected
    }

    // Add it back and ensure it passes
    plugin.settings.license.allowedIDs.add("GPL-2.0-with-classpath-exception")
    plugin.analyzeLicenses()

    // Remove a bad license, but ignore the artifact
    plugin.settings.license.allowedIDs.remove("GPL-2.0-only")
    plugin.settings.license.ignoredArtifactIDs.add("org.lattejava.test:leaf:*:*")
    plugin.analyzeLicenses()
  }

  @Test
  void classpathWithNoDependencies() throws Exception {
    project.dependencies = null

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    Classpath classpath = plugin.classpath {
      dependencies(group: "compile", transitive: true, fetchSource: true)
    }

    assertEquals(classpath.toString(), "")
  }

  @Test
  void classpathWithDependencies() throws Exception {
    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    Classpath classpath = plugin.classpath {
      dependencies(group: "compile", transitive: true, fetchSource: true)
    }

    assertEquals(classpath.toString(),
        "${cacheDir.resolve("org/lattejava/test/gpl-with-cpe/1.0.0/gpl-with-cpe-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf/1.0.0/leaf1-1.0.0.jar").toAbsolutePath()}:" +
            "${integrationDir.resolve("org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf1/1.0.0/leaf1-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf2/1.0.0/leaf2-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf3/1.0.0/leaf3-1.0.0.jar").toAbsolutePath()}"
    )
  }

  @Test
  void classpathWithPath() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/licenses"))

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    Classpath classpath = plugin.classpath {
      dependencies(group: "compile", transitive: true, fetchSource: true)
      path(location: "foo.jar")
    }

    assertEquals(classpath.toString(),
        "${cacheDir.resolve("org/lattejava/test/gpl-with-cpe/1.0.0/gpl-with-cpe-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf/1.0.0/leaf1-1.0.0.jar").toAbsolutePath()}:" +
            "${integrationDir.resolve("org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf1/1.0.0/leaf1-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf2/1.0.0/leaf2-1.0.0.jar").toAbsolutePath()}:" +
            "${cacheDir.resolve("org/lattejava/test/leaf3/1.0.0/leaf3-1.0.0.jar").toAbsolutePath()}:" +
            project.directory.resolve("foo.jar").toAbsolutePath()
    )
  }

  @Test
  void copy() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/copy"))

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.copy(to: "build/test/copy") {
      dependencies(group: "compile", transitive: true)
    }

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/multiple-versions-1.1.0.jar")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/gpl-with-cpe-1.0.0.jar")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/integration-build-2.1.1-{integration}.jar")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/multiple-versions-different-dependencies-1.1.0.jar")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/leaf2-1.0.0.jar")))
  }

  @Test
  void integrate() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/integration"))

    project.publications.add("main",
        new Publication(new ReifiedArtifact("group:name:name:1.1.1:jar", [License.parse("BSD_2_Clause", null)]),
            new ArtifactMetaData(null, [License.parse("BSD_2_Clause", null)]), projectDir.resolve("../../LICENSE"), projectDir.resolve("../../README.md"))
    )
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), projectDir.resolve("build/test/integration").toString(), null)
        ),
        output
    )

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.integrate()

    Path integrationFile = projectDir.resolve("build/test/integration/group/name/1.1.1-{integration}/name-1.1.1-{integration}.jar")
    Path integrationSourceFile = projectDir.resolve("build/test/integration/group/name/1.1.1-{integration}/name-1.1.1-{integration}-src.jar")
    assertTrue(Files.isRegularFile(integrationFile))
    assertTrue(Files.isRegularFile(integrationSourceFile))
    assertEquals(Files.readAllBytes(integrationFile), Files.readAllBytes(projectDir.resolve("../../LICENSE")))
    assertEquals(Files.readAllBytes(integrationSourceFile), Files.readAllBytes(projectDir.resolve("../../README.md")))
  }

  @Test(enabled = true)
  void listUnusedDependencies() {
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.apache.commons:commons-compress:1.7.0", "1.7", false, List.of())
        ),
        new DependencyGroup("test-compile", true,
            new Artifact("org.testng:testng:6.8.7"),
            new Artifact("org.apache.commons:commons-compress:1.7.0", "1.7", false, List.of())
        )
    )
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, null, null, null),
            new URLProcess(output, "https://repository.savantbuild.org", null, null),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, null, null, null)
        ),
        output
    )
    project.workflow.mappings.put("org.beanshell:bsh:2.0b4", new Version("2.0+b4"))
    project.workflow.mappings.put("org.beanshell:beanshell:2.0b4", new Version("2.0+b4"))
    output.enableDebug()

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.listUnusedDependencies()
  }

  @Test
  void path() throws Exception {
    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    Path path = plugin.path(id: "org.lattejava.test:intermediate:1.0.0", group: "runtime")
    assertEquals(path, cacheDir.resolve("org/lattejava/test/intermediate/1.0.0/intermediate-1.0.0.jar").toAbsolutePath())

    path = plugin.path(id: "org.lattejava.test:bad:1.0.0", group: "runtime")
    assertNull(path)
  }

  @Test
  void printFull() throws Exception {
    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.printFull()
  }

  @Test
  void writeLicenses() {
    FileTools.prune(projectDir.resolve("build/test/licenses"))

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.writeLicenses(to: "build/test/licenses")

    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/multiple-versions/1.1.0/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf/1.0.0/license-GPL-2.0-only.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/integration-build/2.1.1-{integration}/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/multiple-versions-different-dependencies/1.1.0/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf1/1.0.0/license-Commercial.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf2/1.0.0/license-OtherNonDistributableOpenSource.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf3/1.0.0/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/intermediate/1.0.0/license-Apache-2.0.txt")))
  }

  @Test
  void writeLicensesWithGroups() {
    FileTools.prune(projectDir.resolve("build/test/licenses"))

    DependencyPlugin plugin = new DependencyPlugin(project, new RuntimeConfiguration(), output)
    plugin.writeLicenses(to: "build/test/licenses", groups: ["compile"])

    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/multiple-versions/1.1.0/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf/1.0.0/license-GPL-2.0-only.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/integration-build/2.1.1-{integration}/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/multiple-versions-different-dependencies/1.1.0/license-Apache-2.0.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf1/1.0.0/license-Commercial.txt")))
    assertTrue(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf2/1.0.0/license-OtherNonDistributableOpenSource.txt")))
    assertFalse(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/leaf3/1.0.0/license-Apache-2.0.txt")))
    assertFalse(Files.isRegularFile(project.directory.resolve("build/test/licenses/org/lattejava/test/intermediate/1.0.0/license-Apache-2.0.txt")))
  }
}
