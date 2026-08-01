/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep.idea

import groovy.xml.XmlParser
import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.Artifact
import org.lattejava.dep.domain.Dependencies
import org.lattejava.dep.domain.DependencyGroup
import org.lattejava.dep.domain.License
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.dep.workflow.process.MavenProcess
import org.lattejava.version.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.Assert.assertEquals

/**
 * Tests the IDEA plugin.
 *
 * @author Brian Pontarelli
 */
class IDEAPluginTest {
  public static Path projectDir

  Output output

  Project project

  IDEAPlugin plugin

  Path cacheDir

  Path integrationDir

  Path mavenDir

  private static void copyRecursive(Path source, Path dest) {
    Files.walk(source)
         .forEach { src ->
           {
             var destFile = dest.resolve(source.relativize(src));
             if (Files.isRegularFile(src) && !Files.isRegularFile(destFile)) {
               Files.createDirectories(destFile.getParent())
               Files.copy(src, destFile);
             }
           }
        }
  }

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
      projectDir = Paths.get("../idea")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    FileTools.prune(projectDir.resolve("build/test"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    def path = projectDir.resolve("build/test")
    Files.createDirectories(path)
    project = new Project(path, output)
    project.group = "org.lattejava.test"
    project.name = "idea-test"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:multiple-versions:1.0.0"),
            new Artifact("org.lattejava.test:multiple-versions-different-dependencies:1.0.0"),
            // simple, no dependencies
            new Artifact("org.slf4j:slf4j-api:2.0.16")
        ),
        new DependencyGroup("runtime", true,
            new Artifact("org.lattejava.test:intermediate:1.0.0")
        )
    )

    cacheDir = projectDir.resolve("build/test/test-deps/latte")
    integrationDir = projectDir.resolve("build/test/test-deps/integration")
    mavenDir = projectDir.resolve("build/test/test-deps/maven")

    Files.createDirectories(cacheDir)
    Files.createDirectories(integrationDir)
    Files.createDirectories(mavenDir)

    copyRecursive(projectDir.resolve("../../test-deps/latte"), cacheDir);
    copyRecursive(projectDir.resolve("../../test-deps/integration"), integrationDir)

    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), mavenDir.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), mavenDir.toString())
        ),
        output
    )

    plugin = new IDEAPlugin(project, new RuntimeConfiguration(), output)

    // Ensure our cached Maven artifact directory is clean, so that we get deterministic results
    def slf4jDir = Paths.get(cacheDir.toString(), "org/slf4j")
    Files.deleteIfExists(slf4jDir)
  }

  @Test
  void iml() throws Exception {
    def imlFile = projectDir.resolve("build/test/idea-test.iml")
    Files.copy(projectDir.resolve("src/test/resources/test.iml"), imlFile)

    plugin.iml()

    String actual = new String(Files.readAllBytes(imlFile))
    String expected = new String(Files.readAllBytes(projectDir.resolve("src/test/resources/expected.iml")))
    assertEquals(actual, expected)
  }

  @Test
  void imlModule() throws Exception {
    def imlFile = projectDir.resolve("build/test/idea-test.iml")
    Files.copy(projectDir.resolve("src/test/resources/test.iml"), imlFile)

    plugin.settings.moduleMap.put("org.lattejava.test:leaf2:1.0.0", "leaf2-module")

    plugin.iml()

    String actual = new String(Files.readAllBytes(imlFile))
    String expected = new String(Files.readAllBytes(projectDir.resolve("src/test/resources/expected-module.iml")))
    assertEquals(actual, expected)
  }

  @Test
  void imlModule_run_twice() throws Exception {
    def imlFile = projectDir.resolve("build/test/idea-test.iml")
    Files.copy(projectDir.resolve("src/test/resources/test.iml"), imlFile)

    plugin.settings.moduleMap.put("org.lattejava.test:leaf2:1.0.0", "leaf2-module")

    plugin.iml()
    String actual = new String(Files.readAllBytes(imlFile))
    plugin.iml()
    String secondTime = new String(Files.readAllBytes(imlFile))

    assertEquals(actual, secondTime)
  }

  @Test
  void imlCompileProcessors() throws Exception {
    // The compile-processors group is resolved transitively into the PROVIDED scope so that IDEA compiles
    // against the annotation processor and everything it drags in, without leaking any of it into COMPILE
    // or RUNTIME. This project declares no other groups, so every library in the IML must be PROVIDED.
    project.dependencies = new Dependencies(
        new DependencyGroup("compile-processors", true,
            new Artifact("org.lattejava.test:intermediate:1.0.0")
        )
    )
    project.artifactGraph = null
    plugin = new IDEAPlugin(project, new RuntimeConfiguration(), output)

    def imlFile = projectDir.resolve("build/test/idea-test.iml")
    Files.copy(projectDir.resolve("src/test/resources/test.iml"), imlFile)

    plugin.iml()

    Node component = new XmlParser().parse(imlFile.toFile()).component.find { it.@name == "NewModuleRootManager" }
    List<Node> libraries = component.orderEntry.findAll { it.@type == "module-library" }

    assertEquals(libraries.collect { it.@scope }.unique(), ["PROVIDED"])

    // intermediate is the only direct dependency. The rest are transitive, reached through both the compile
    // and runtime groups of the intermediate artifacts, and all of them belong in PROVIDED as well.
    assertEquals(libraries.collect { it.library.CLASSES.root[0].@url }.sort(), [
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/intermediate/1.0.0/intermediate-1.0.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/leaf/1.0.0/leaf1-1.0.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/integration/org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/leaf1/1.0.0/leaf1-1.0.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/leaf2/1.0.0/leaf2-1.0.0.jar!/',
        'jar://$MODULE_DIR$/test-deps/latte/org/lattejava/test/leaf3/1.0.0/leaf3-1.0.0.jar!/'
    ].sort())
  }

  @Test
  void noDependencies() throws Exception {
    project.dependencies = null
    project.artifactGraph = null
    def imlFile = projectDir.resolve("build/test/idea-test.iml")
    Files.copy(projectDir.resolve("src/test/resources/test.iml"), imlFile)

    plugin.iml()

    String actual = new String(Files.readAllBytes(imlFile))
    String expected = new String(Files.readAllBytes(projectDir.resolve("src/test/resources/no-dependencies.iml")))
    assertEquals(actual, expected)
  }
}
